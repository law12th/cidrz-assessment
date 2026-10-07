import type { FollowUpItem, SortOrder } from '../../api/types';
import { StatusBadge } from './StatusBadge';
import { daysLabel, formatDate, formatDateTime } from './format';

interface Props {
  items: FollowUpItem[];
  caption: string;
  sort: SortOrder;
  busy: boolean;
  canRecordContact: boolean;
  pendingIds: ReadonlySet<string>;
  onSortChange: (sort: SortOrder) => void;
  onRecordContact: (item: FollowUpItem) => void;
}

/**
 * A real <table>: screen readers can announce the column for each cell and jump by row.
 * The patient name is the row header, so moving across a row announces whose record it is.
 * Presentational only. It receives data and callbacks and makes no requests.
 */
export function FollowUpTable({
  items, caption, sort, busy, canRecordContact, pendingIds, onSortChange, onRecordContact,
}: Props) {
  const descending = sort === 'days_overdue_desc';
  return (
    <div className="table-scroll">
      <table className="follow-up-table" aria-busy={busy}>
        <caption>{caption}</caption>
        <thead>
          <tr>
            <th scope="col">Patient</th>
            <th scope="col">Status</th>
            <th scope="col">Appointment</th>
            <th scope="col" aria-sort={descending ? 'descending' : 'ascending'}>
              <button
                type="button"
                className="sort-button"
                onClick={() => onSortChange(descending ? 'days_overdue_asc' : 'days_overdue_desc')}
              >
                Days overdue
                <span aria-hidden="true">{descending ? ' ▼' : ' ▲'}</span>
                <span className="visually-hidden">
                  {descending ? ', most overdue first. Activate to show least overdue first' : ', least overdue first. Activate to show most overdue first'}
                </span>
              </button>
            </th>
            <th scope="col">Phone</th>
            <th scope="col">Last contact</th>
            {canRecordContact && <th scope="col">Action</th>}
          </tr>
        </thead>
        <tbody>
          {items.map((item) => {
            const name = `${item.first_name} ${item.last_name}`;
            const pending = pendingIds.has(item.id);
            return (
              <tr key={item.id}>
                <th scope="row" className="patient-cell">
                  <span className="patient-cell__name">{name}</span>
                  <span className="patient-cell__number">{item.patient_number}</span>
                </th>
                <td><StatusBadge status={item.follow_up_status} /></td>
                <td>{formatDate(item.missed_appointment_date)}</td>
                <td className="numeric">{daysLabel(item.days_overdue)}</td>
                <td>{item.phone_number ?? <span className="muted">No phone number</span>}</td>
                <td>
                  {item.last_contact_attempt
                    ? formatDateTime(item.last_contact_attempt)
                    : <span className="muted">Not contacted yet</span>}
                </td>
                {canRecordContact && (
                  <td>
                    <button
                      type="button"
                      className="button"
                      aria-disabled={pending || undefined}
                      onClick={() => {
                        if (!pending) onRecordContact(item);
                      }}
                    >
                      {pending ? 'Recording…' : 'Record contact'}
                      <span className="visually-hidden"> with {name}</span>
                    </button>
                  </td>
                )}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
