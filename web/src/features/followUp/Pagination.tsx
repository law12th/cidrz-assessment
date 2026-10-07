import { useId } from 'react';
import { formatCount } from './format';

interface Props {
  page: number;
  pageSize: number;
  total: number;
  onPageChange: (page: number) => void;
  onPageSizeChange: (pageSize: number) => void;
}

export const PAGE_SIZES = [25, 50, 100, 200] as const;

export function Pagination({ page, pageSize, total, onPageChange, onPageSizeChange }: Props) {
  const sizeId = useId();
  const lastPage = Math.max(1, Math.ceil(total / pageSize));
  const first = total === 0 ? 0 : (page - 1) * pageSize + 1;
  const last = Math.min(total, page * pageSize);

  return (
    <nav className="pagination" aria-label="Pages of the follow-up list">
      <p className="pagination__summary">
        Showing {formatCount(first)} to {formatCount(last)} of {formatCount(total)}. Page {formatCount(page)} of{' '}
        {formatCount(lastPage)}.
      </p>
      <div className="pagination__controls">
        <button type="button" className="button" disabled={page <= 1} onClick={() => onPageChange(1)}>
          First page
        </button>
        <button type="button" className="button" disabled={page <= 1} onClick={() => onPageChange(page - 1)}>
          Previous page
        </button>
        <button type="button" className="button" disabled={page >= lastPage} onClick={() => onPageChange(page + 1)}>
          Next page
        </button>
        <button type="button" className="button" disabled={page >= lastPage} onClick={() => onPageChange(lastPage)}>
          Last page
        </button>
        <label htmlFor={sizeId}>Rows per page</label>
        <select id={sizeId} value={pageSize} onChange={(e) => onPageSizeChange(Number(e.target.value))}>
          {PAGE_SIZES.map((size) => (
            <option key={size} value={size}>
              {size}
            </option>
          ))}
        </select>
      </div>
    </nav>
  );
}
