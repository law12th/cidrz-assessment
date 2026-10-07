/**
 * Wire types for the follow-up API (appendix 15.2 and 15.3). Field names match the JSON exactly.
 */
export type FollowUpStatus = 'due_soon' | 'overdue' | 'missed';
export type SortOrder = 'days_overdue_desc' | 'days_overdue_asc';

export interface FollowUpItem {
  id: string;
  patient_number: string;
  first_name: string;
  last_name: string;
  facility_id: string;
  facility_name: string;
  /** The appointment the patient missed. For due_soon rows, the upcoming appointment. */
  missed_appointment_date: string;
  /** Days since the appointment. Zero or negative for due_soon rows. */
  days_overdue: number;
  follow_up_status: FollowUpStatus;
  phone_number: string | null;
  last_contact_attempt: string | null;
}

export interface FollowUpPage {
  page: number;
  page_size: number;
  total: number;
  items: FollowUpItem[];
}

export interface FollowUpQuery {
  facilityId: string;
  /** Undefined means the default list: missed and overdue. */
  status?: FollowUpStatus;
  overdueDays: number;
  sort: SortOrder;
  page: number;
  pageSize: number;
}

export interface ContactRequest {
  contactedAt: string;
  note?: string;
}

export interface ErrorBody {
  error: { code: string; message: string; correlation_id: string | null };
}
