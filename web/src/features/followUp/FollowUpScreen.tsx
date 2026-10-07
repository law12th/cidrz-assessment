import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../../api/apiError';
import { newCorrelationId } from '../../api/correlationId';
import { useFollowUpService } from '../../api/ServiceContext';
import type { FollowUpItem, FollowUpQuery, SortOrder } from '../../api/types';
import type { FacilityOption } from '../../config/identities';
import { FilterForm, type Filters } from './FilterForm';
import { FollowUpTable } from './FollowUpTable';
import { EmptyMessage, ErrorMessage, LoadingMessage, describeError } from './ListMessage';
import { Pagination } from './Pagination';
import { formatCount } from './format';
import { useFollowUpList } from './useFollowUpList';

interface Props {
  facilities: FacilityOption[];
  canRecordContact: boolean;
}

/**
 * Container: owns the query, talks to the service through the hook, and passes plain data
 * to presentational components.
 */
export function FollowUpScreen({ facilities, canRecordContact }: Props) {
  const service = useFollowUpService();
  const [query, setQuery] = useState<FollowUpQuery>({
    facilityId: facilities[0]?.id ?? '',
    status: undefined,
    overdueDays: 7,
    sort: 'days_overdue_desc',
    page: 1,
    pageSize: 50,
  });
  const { state, retry, updateItem } = useFollowUpList(service, query);
  const [pendingIds, setPendingIds] = useState<ReadonlySet<string>>(new Set());
  const [announcement, setAnnouncement] = useState('');
  const resultsHeading = useRef<HTMLHeadingElement>(null);
  const focusResultsAfterLoad = useRef(false);

  const facilityName = facilities.find((f) => f.id === query.facilityId)?.name ?? query.facilityId;

  // After a page change, move focus to the top of the results. A keyboard user who pressed
  // "Next page" at the bottom is not left there, and a screen reader announces the new page.
  useEffect(() => {
    if (state.status !== 'loading' && focusResultsAfterLoad.current) {
      focusResultsAfterLoad.current = false;
      resultsHeading.current?.focus();
    }
  }, [state.status]);

  const applyFilters = useCallback((filters: Filters) => {
    setQuery((q) => ({
      ...q,
      facilityId: filters.facilityId,
      status: filters.status || undefined,
      overdueDays: filters.overdueDays,
      page: 1,
    }));
  }, []);

  const changePage = useCallback((page: number) => {
    focusResultsAfterLoad.current = true;
    setQuery((q) => ({ ...q, page }));
  }, []);

  const changePageSize = useCallback((pageSize: number) => {
    setQuery((q) => ({ ...q, pageSize, page: 1 }));
  }, []);

  const changeSort = useCallback((sort: SortOrder) => {
    setQuery((q) => ({ ...q, sort, page: 1 }));
  }, []);

  const recordContact = useCallback(
    async (item: FollowUpItem) => {
      const name = `${item.first_name} ${item.last_name}`;
      const contactedAt = new Date().toISOString();
      setPendingIds((ids) => new Set(ids).add(item.id));
      try {
        // One key per click: if this request is retried after a timeout, the server records it once.
        await service.recordContact(item.id, { contactedAt }, newCorrelationId());
        updateItem(item.id, (current) => ({ ...current, last_contact_attempt: contactedAt }));
        setAnnouncement(`Contact recorded for ${name}.`);
      } catch (error) {
        const message = error instanceof ApiError ? describeError(error) : 'Something unexpected went wrong.';
        setAnnouncement(`Contact for ${name} was not recorded. ${message}`);
      } finally {
        setPendingIds((ids) => {
          const next = new Set(ids);
          next.delete(item.id);
          return next;
        });
      }
    },
    [service, updateItem],
  );

  const page = state.page;
  const showTable = page !== null && page.items.length > 0;

  return (
    <main id="main" className="screen">
      <h1>Follow-up list</h1>

      <FilterForm
        facilities={facilities}
        applied={{ facilityId: query.facilityId, status: query.status ?? '', overdueDays: query.overdueDays }}
        onApply={applyFilters}
      />

      {/* Results of "Record contact" are announced here without moving focus. */}
      <div role="status" className="visually-hidden">
        {announcement}
      </div>

      <section aria-labelledby="results-heading" className="results">
        <h2 id="results-heading" ref={resultsHeading} tabIndex={-1}>
          {page ? `${formatCount(page.total)} patients at ${facilityName}` : `Patients at ${facilityName}`}
        </h2>

        {state.status === 'loading' && <LoadingMessage />}
        {state.status === 'error' && <ErrorMessage error={state.error} onRetry={retry} />}
        {state.status === 'success' && state.page.items.length === 0 && <EmptyMessage />}

        {showTable && (
          <>
            <FollowUpTable
              items={page.items}
              caption={`Follow-up list, page ${page.page}, sorted by days overdue`}
              sort={query.sort}
              busy={state.status === 'loading'}
              canRecordContact={canRecordContact}
              pendingIds={pendingIds}
              onSortChange={changeSort}
              onRecordContact={recordContact}
            />
            <Pagination
              page={page.page}
              pageSize={page.page_size}
              total={page.total}
              onPageChange={changePage}
              onPageSizeChange={changePageSize}
            />
          </>
        )}
      </section>
    </main>
  );
}
