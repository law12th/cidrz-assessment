import type { FollowUpItem, FollowUpStatus } from '../api/types';

const FIRST = ['Chileshe', 'Mwila', 'Bwalya', 'Mutale', 'Natasha', 'Kondwani', 'Musonda', 'Mapalo'];
const LAST = ['Mumba', 'Tembo', 'Ngoma', 'Lungu', 'Sakala', 'Daka', 'Zimba', 'Mbewe'];

/** Invented rows in the API's shape, for measuring rendering without a server. */
export function generateItems(count: number): FollowUpItem[] {
  const items: FollowUpItem[] = [];
  for (let i = 0; i < count; i++) {
    const days = 8 + ((i * 7919) % 110);
    const status: FollowUpStatus = days > 21 ? 'overdue' : 'missed';
    items.push({
      id: `bench-${i}`,
      patient_number: `0101-${String(100000 + i).padStart(6, '0')}`,
      first_name: FIRST[i % FIRST.length]!,
      last_name: LAST[(i * 3) % LAST.length]!,
      facility_id: 'FAC-0101',
      facility_name: 'Mwansa Urban Clinic',
      missed_appointment_date: '2026-09-05',
      days_overdue: days,
      follow_up_status: status,
      phone_number: i % 3 === 0 ? null : `+26097${String(i).padStart(7, '0')}`,
      last_contact_attempt: i % 4 === 0 ? '2026-09-29T09:14:00Z' : null,
    });
  }
  return items.sort((a, b) => b.days_overdue - a.days_overdue);
}
