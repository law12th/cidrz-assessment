export type ApiErrorKind = 'http' | 'network' | 'timeout' | 'invalid_response';

/** Every failure the service layer reports. Screens never see a raw fetch error. */
export class ApiError extends Error {
  constructor(
    readonly kind: ApiErrorKind,
    message: string,
    readonly status: number | null = null,
    readonly code: string | null = null,
    readonly correlationId: string | null = null,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError';
}
