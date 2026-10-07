import type { FollowUpStatus } from '../../api/types';
import { STATUS_LABEL } from './format';

// A distinct shape for each status as well as a word and a colour, so status survives
// colour blindness, a monochrome printout and a washed-out old monitor.
const SHAPE: Record<FollowUpStatus, string> = {
  overdue: '\u25B2', // ▲
  missed: '\u25C6', // ◆
  due_soon: '\u25CB', // ○
};

export function StatusBadge({ status }: { status: FollowUpStatus }) {
  return (
    <span className={`status status--${status}`}>
      <span className="status__shape" aria-hidden="true">
        {SHAPE[status]}
      </span>
      {STATUS_LABEL[status]}
    </span>
  );
}
