import { ApiError } from './apiError';
import { newCorrelationId } from './correlationId';
import { isErrorBody } from './guards';

export interface HttpClientOptions {
  getToken: () => string | null;
  baseUrl?: string;
  timeoutMs?: number;
  fetchImpl?: typeof fetch;
}

export interface RequestOptions<T> {
  method?: 'GET' | 'POST';
  query?: URLSearchParams;
  body?: unknown;
  headers?: Record<string, string>;
  signal?: AbortSignal;
  /** Validates and narrows the parsed JSON. Not called for 204 responses. */
  parse: (json: unknown) => T | undefined;
}

export interface HttpClient {
  request<T>(path: string, options: RequestOptions<T>): Promise<T | undefined>;
}

const DEFAULT_TIMEOUT_MS = 15_000;

export function createHttpClient(options: HttpClientOptions): HttpClient {
  const fetchImpl = options.fetchImpl ?? ((input, init) => fetch(input, init));
  const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS;
  const baseUrl = options.baseUrl ?? '';

  return {
    async request<T>(path: string, request: RequestOptions<T>): Promise<T | undefined> {
      const correlationId = newCorrelationId();
      const url = `${baseUrl}${path}${request.query ? `?${request.query.toString()}` : ''}`;

      // One controller fed by two sources: the caller cancelling (e.g. filters changed) and our timeout.
      // AbortSignal.any() would do this, but older browsers lack it.
      const controller = new AbortController();
      let timedOut = false;
      const timer = setTimeout(() => {
        timedOut = true;
        controller.abort();
      }, timeoutMs);
      const onCallerAbort = () => controller.abort();
      request.signal?.addEventListener('abort', onCallerAbort);

      const headers: Record<string, string> = {
        Accept: 'application/json',
        'X-Correlation-Id': correlationId,
        ...request.headers,
      };
      const token = options.getToken();
      if (token) headers.Authorization = `Bearer ${token}`;
      if (request.body !== undefined) headers['Content-Type'] = 'application/json';

      let response: Response;
      try {
        response = await fetchImpl(url, {
          method: request.method ?? 'GET',
          headers,
          body: request.body === undefined ? undefined : JSON.stringify(request.body),
          signal: controller.signal,
        });
      } catch (error) {
        if (request.signal?.aborted) throw error; // the caller cancelled: not an error to show
        if (timedOut) {
          throw new ApiError('timeout', 'The server took too long to answer', null, null, correlationId);
        }
        throw new ApiError('network', 'The server could not be reached', null, null, correlationId);
      } finally {
        clearTimeout(timer);
        request.signal?.removeEventListener('abort', onCallerAbort);
      }

      const serverCorrelationId = response.headers.get('X-Correlation-Id') ?? correlationId;

      if (!response.ok) {
        const json: unknown = await response.json().catch(() => null);
        if (isErrorBody(json)) {
          throw new ApiError('http', json.error.message, response.status, json.error.code,
            json.error.correlation_id ?? serverCorrelationId);
        }
        throw new ApiError('http', `The server answered with status ${response.status}`, response.status, null,
          serverCorrelationId);
      }

      if (response.status === 204) return undefined;

      const json: unknown = await response.json().catch(() => undefined);
      const parsed = request.parse(json);
      if (parsed === undefined) {
        throw new ApiError('invalid_response', 'The server sent a response this screen does not understand',
          response.status, null, serverCorrelationId);
      }
      return parsed;
    },
  };
}
