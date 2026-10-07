import { describe, expect, it, vi } from 'vitest';
import { ApiError } from './apiError';
import { createFollowUpService } from './followUpService';
import { createHttpClient } from './httpClient';
import type { FollowUpPage } from './types';

const PAGE: FollowUpPage = {
  page: 1,
  page_size: 50,
  total: 1,
  items: [
    {
      id: 'p_8841', patient_number: '0101-000471', first_name: 'Thandiwe', last_name: 'Banda',
      facility_id: 'FAC-0101', facility_name: 'Mwansa Urban Clinic', missed_appointment_date: '2026-09-05',
      days_overdue: 31, follow_up_status: 'overdue', phone_number: '+260970000471', last_contact_attempt: null,
    },
  ],
};

function json(status: number, body: unknown, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...headers } });
}

function serviceWith(fetchImpl: typeof fetch, timeoutMs?: number) {
  return createFollowUpService(createHttpClient({ getToken: () => 'dev-token', fetchImpl, timeoutMs }));
}

const QUERY = { facilityId: 'FAC-0101', overdueDays: 7, sort: 'days_overdue_desc', page: 2, pageSize: 50 } as const;

describe('follow-up service', () => {
  it('sends the token, a correlation ID and every query parameter', async () => {
    const fetchImpl = vi.fn<typeof fetch>().mockResolvedValue(json(200, PAGE));
    await serviceWith(fetchImpl).list({ ...QUERY, status: 'missed' });

    const [url, init] = fetchImpl.mock.calls[0]!;
    expect(url).toBe('/api/follow-up?facility_id=FAC-0101&status=missed&overdue_days=7&sort=days_overdue_desc&page=2&page_size=50');
    const headers = init?.headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer dev-token');
    expect(headers['X-Correlation-Id']).toMatch(/^[0-9a-f-]{36}$/);
  });

  it('turns the contract error shape into an ApiError with the server correlation ID', async () => {
    const fetchImpl = vi.fn<typeof fetch>().mockResolvedValue(
      json(400, { error: { code: 'VALIDATION_FAILED', message: 'facility_id is required', correlation_id: '8c21-44f1' } }),
    );
    const error = await serviceWith(fetchImpl).list(QUERY).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      kind: 'http', status: 400, code: 'VALIDATION_FAILED',
      message: 'facility_id is required', correlationId: '8c21-44f1',
    });
  });

  it('reports a stopped server as a network error, not a crash', async () => {
    const fetchImpl = vi.fn<typeof fetch>().mockRejectedValue(new TypeError('Failed to fetch'));
    await expect(serviceWith(fetchImpl).list(QUERY)).rejects.toMatchObject({ kind: 'network' });
  });

  it('rejects a response that does not match the contract instead of rendering blanks', async () => {
    const fetchImpl = vi.fn<typeof fetch>().mockResolvedValue(json(200, { items: [{ id: 1 }] }));
    await expect(serviceWith(fetchImpl).list(QUERY)).rejects.toMatchObject({ kind: 'invalid_response' });
  });

  it('times out a request that never answers', async () => {
    const fetchImpl = vi.fn<typeof fetch>((_url, init) =>
      new Promise<Response>((_resolve, reject) => {
        init?.signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')));
      }));
    await expect(serviceWith(fetchImpl, 20).list(QUERY)).rejects.toMatchObject({ kind: 'timeout' });
  });

  it('sends contact attempts with an idempotency key', async () => {
    const fetchImpl = vi.fn<typeof fetch>().mockResolvedValue(new Response(null, { status: 204 }));
    await serviceWith(fetchImpl).recordContact('p_8841', { contactedAt: '2026-10-06T09:14:00Z' }, 'key-12345678');

    const [url, init] = fetchImpl.mock.calls[0]!;
    expect(url).toBe('/api/follow-up/p_8841/contacted');
    expect(init?.method).toBe('POST');
    expect((init?.headers as Record<string, string>)['Idempotency-Key']).toBe('key-12345678');
    expect(JSON.parse(init?.body as string)).toEqual({ contacted_at: '2026-10-06T09:14:00Z' });
  });
});
