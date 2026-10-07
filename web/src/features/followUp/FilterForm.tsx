import { useId, useRef, useState, type FormEvent } from 'react';
import type { FollowUpStatus } from '../../api/types';
import type { FacilityOption } from '../../config/identities';

export interface Filters {
  facilityId: string;
  status: FollowUpStatus | '';
  overdueDays: number;
}

interface Props {
  facilities: FacilityOption[];
  applied: Filters;
  onApply: (filters: Filters) => void;
}

const MAX_DAYS = 365;

/**
 * Filters apply when the user presses "Show list", not on every keystroke. On a slow link,
 * typing "14" would otherwise send two requests, and focus would not jump around while typing.
 */
export function FilterForm({ facilities, applied, onApply }: Props) {
  const ids = { facility: useId(), status: useId(), days: useId(), daysHint: useId(), daysError: useId() };
  const [facilityId, setFacilityId] = useState(applied.facilityId);
  const [status, setStatus] = useState<Filters['status']>(applied.status);
  const [daysText, setDaysText] = useState(String(applied.overdueDays));
  const [daysError, setDaysError] = useState<string | null>(null);
  const daysInput = useRef<HTMLInputElement>(null);

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const trimmed = daysText.trim();
    const days = Number(trimmed);
    if (!/^\d+$/.test(trimmed) || days > MAX_DAYS) {
      setDaysError(`Enter a whole number of days from 0 to ${MAX_DAYS}.`);
      // Moving focus to the field makes screen readers read its label, value and error together.
      daysInput.current?.focus();
      return;
    }
    setDaysError(null);
    onApply({ facilityId, status, overdueDays: days });
  }

  const singleFacility = facilities.length === 1 ? facilities[0] : undefined;

  return (
    <form className="filters" onSubmit={handleSubmit} noValidate aria-label="Filter the follow-up list">
      <div className="field">
        <label htmlFor={ids.facility}>Facility</label>
        {singleFacility ? (
          <input id={ids.facility} value={singleFacility.name} readOnly />
        ) : (
          <select id={ids.facility} value={facilityId} onChange={(e) => setFacilityId(e.target.value)}>
            {facilities.map((f) => (
              <option key={f.id} value={f.id}>
                {f.name}
              </option>
            ))}
          </select>
        )}
      </div>

      <div className="field">
        <label htmlFor={ids.status}>Status</label>
        <select id={ids.status} value={status} onChange={(e) => setStatus(e.target.value as Filters['status'])}>
          <option value="">Needs follow-up (missed and overdue)</option>
          <option value="missed">Recently missed</option>
          <option value="overdue">Overdue</option>
          <option value="due_soon">Due soon</option>
        </select>
      </div>

      <div className="field field--days">
        <label htmlFor={ids.days}>Missed after (days)</label>
        <input
          id={ids.days}
          ref={daysInput}
          inputMode="numeric"
          autoComplete="off"
          value={daysText}
          onChange={(e) => setDaysText(e.target.value)}
          aria-invalid={daysError ? true : undefined}
          aria-describedby={daysError ? `${ids.daysHint} ${ids.daysError}` : ids.daysHint}
        />
        <p id={ids.daysHint} className="field__hint">
          Days after the appointment before a patient counts as missed.
        </p>
        {daysError && (
          <p id={ids.daysError} className="field__error">
            <span aria-hidden="true">✕ </span>
            {daysError}
          </p>
        )}
      </div>

      <button type="submit" className="button button--primary">
        Show list
      </button>
    </form>
  );
}
