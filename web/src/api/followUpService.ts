import { ApiError } from './apiError';
import { isFollowUpPage } from './guards';
import type { HttpClient } from './httpClient';
import type { ContactRequest, FollowUpPage, FollowUpQuery } from './types';

/**
 * Everything the follow-up screen needs from the server. Components depend on this interface,
 * never on fetch, so tests can hand them a fake.
 */
export interface FollowUpService {
  list(query: FollowUpQuery, signal?: AbortSignal): Promise<FollowUpPage>;
  /** The idempotency key makes a retried request safe: the server records the attempt once. */
  recordContact(patientId: string, request: ContactRequest, idempotencyKey: string): Promise<void>;
}

export function toSearchParams(query: FollowUpQuery): URLSearchParams {
  const params = new URLSearchParams();
  params.set('facility_id', query.facilityId);
  if (query.status) params.set('status', query.status);
  params.set('overdue_days', String(query.overdueDays));
  params.set('sort', query.sort);
  params.set('page', String(query.page));
  params.set('page_size', String(query.pageSize));
  return params;
}

export function createFollowUpService(http: HttpClient): FollowUpService {
  return {
    async list(query, signal) {
      const page = await http.request('/api/follow-up', {
        query: toSearchParams(query),
        signal,
        parse: (json) => (isFollowUpPage(json) ? json : undefined),
      });
      if (!page) {
        throw new ApiError('invalid_response', 'The server sent an empty response');
      }
      return page;
    },

    async recordContact(patientId, request, idempotencyKey) {
      await http.request(`/api/follow-up/${encodeURIComponent(patientId)}/contacted`, {
        method: 'POST',
        headers: { 'Idempotency-Key': idempotencyKey },
        body: {
          contacted_at: request.contactedAt,
          ...(request.note ? { note: request.note } : {}),
        },
        parse: () => undefined,
      });
    },
  };
}
