import type { ErrorBody, FollowUpItem, FollowUpPage, FollowUpStatus } from './types';

/**
 * Runtime checks on what the server sends. TypeScript types are erased at run time, so without these a
 * changed or broken response would render as blank cells instead of failing visibly.
 */
function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

const STATUSES: readonly FollowUpStatus[] = ['due_soon', 'overdue', 'missed'];

function isNullableString(value: unknown): value is string | null {
  return value === null || typeof value === 'string';
}

export function isFollowUpItem(value: unknown): value is FollowUpItem {
  return (
    isRecord(value) &&
    typeof value.id === 'string' &&
    typeof value.patient_number === 'string' &&
    typeof value.first_name === 'string' &&
    typeof value.last_name === 'string' &&
    typeof value.facility_id === 'string' &&
    typeof value.facility_name === 'string' &&
    typeof value.missed_appointment_date === 'string' &&
    typeof value.days_overdue === 'number' &&
    STATUSES.includes(value.follow_up_status as FollowUpStatus) &&
    isNullableString(value.phone_number) &&
    isNullableString(value.last_contact_attempt)
  );
}

export function isFollowUpPage(value: unknown): value is FollowUpPage {
  return (
    isRecord(value) &&
    typeof value.page === 'number' &&
    typeof value.page_size === 'number' &&
    typeof value.total === 'number' &&
    Array.isArray(value.items) &&
    value.items.every(isFollowUpItem)
  );
}

export function isErrorBody(value: unknown): value is ErrorBody {
  return (
    isRecord(value) &&
    isRecord(value.error) &&
    typeof value.error.code === 'string' &&
    typeof value.error.message === 'string'
  );
}
