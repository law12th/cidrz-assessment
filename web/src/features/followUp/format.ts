import type { FollowUpStatus } from '../../api/types';

export const STATUS_LABEL: Record<FollowUpStatus, string> = {
  due_soon: 'Due soon',
  missed: 'Recently missed',
  overdue: 'Overdue',
};

/** Plain words for the days column, so a clinician never has to interpret a negative number. */
export function daysLabel(daysOverdue: number): string {
  if (daysOverdue === 0) return 'Due today';
  if (daysOverdue < 0) {
    const days = -daysOverdue;
    return `Due in ${days} ${days === 1 ? 'day' : 'days'}`;
  }
  return `${daysOverdue} ${daysOverdue === 1 ? 'day' : 'days'} overdue`;
}

const dateFormat = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
const dateTimeFormat = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
});

/** Formats a YYYY-MM-DD date without time-zone shifts: "5 Sep 2026". */
export function formatDate(isoDate: string): string {
  const [y, m, d] = isoDate.split('-').map(Number);
  if (!y || !m || !d) return isoDate;
  return dateFormat.format(new Date(y, m - 1, d));
}

export function formatDateTime(isoTimestamp: string): string {
  const date = new Date(isoTimestamp);
  return Number.isNaN(date.getTime()) ? isoTimestamp : dateTimeFormat.format(date);
}

export function formatCount(n: number): string {
  return n.toLocaleString('en-GB');
}
