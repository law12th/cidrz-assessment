import { useCallback, useEffect, useState } from 'react';
import { ApiError, isAbortError } from '../../api/apiError';
import type { FollowUpService } from '../../api/followUpService';
import type { FollowUpItem, FollowUpPage, FollowUpQuery } from '../../api/types';

export type ListState =
  | { status: 'loading'; page: FollowUpPage | null }
  | { status: 'success'; page: FollowUpPage }
  | { status: 'error'; error: ApiError; page: FollowUpPage | null };

/**
 * Loads one page of the list. Changing the query cancels the request in flight, so a slow
 * earlier response can never overwrite a newer one. While a new page loads, the previous page
 * stays on screen (marked busy) rather than flashing empty.
 */
export function useFollowUpList(service: FollowUpService, query: FollowUpQuery) {
  const [state, setState] = useState<ListState>({ status: 'loading', page: null });
  const [attempt, setAttempt] = useState(0);
  const { facilityId, status, overdueDays, sort, page, pageSize } = query;

  useEffect(() => {
    const controller = new AbortController();
    setState((previous) => ({ status: 'loading', page: previous.page }));
    service
      .list({ facilityId, status, overdueDays, sort, page, pageSize }, controller.signal)
      .then((result) => setState({ status: 'success', page: result }))
      .catch((error: unknown) => {
        if (isAbortError(error) || controller.signal.aborted) return;
        const apiError =
          error instanceof ApiError ? error : new ApiError('invalid_response', 'Something unexpected went wrong');
        setState((previous) => ({ status: 'error', error: apiError, page: previous.page }));
      });
    return () => controller.abort();
  }, [service, facilityId, status, overdueDays, sort, page, pageSize, attempt]);

  const retry = useCallback(() => setAttempt((n) => n + 1), []);

  /** Updates one row in place, e.g. after recording a contact, without refetching the page. */
  const updateItem = useCallback((id: string, update: (item: FollowUpItem) => FollowUpItem) => {
    setState((previous) => {
      if (previous.status !== 'success') return previous;
      return {
        ...previous,
        page: { ...previous.page, items: previous.page.items.map((item) => (item.id === id ? update(item) : item)) },
      };
    });
  }, []);

  return { state, retry, updateItem };
}
