/**
 * DEVELOPMENT ONLY. Stands in for signing in, so the screen can be shown as each role.
 * In production the token comes from the identity provider (OIDC), and the facilities a user may
 * see come from their token claims, not from this file.
 */
export interface FacilityOption {
  id: string;
  name: string;
}

export interface DevIdentity {
  label: string;
  token: string;
  canRecordContact: boolean;
  facilities: FacilityOption[];
}

const MWANSA: FacilityOption[] = [
  { id: 'FAC-0101', name: 'Mwansa Urban Clinic' },
  { id: 'FAC-0102', name: 'Mwansa East Clinic' },
  { id: 'FAC-0103', name: 'Mwansa North Health Post' },
];

export const DEV_IDENTITIES: DevIdentity[] = [
  {
    label: 'Clinician, Mwansa Urban Clinic',
    token: 'dev-clinician-0101',
    canRecordContact: true,
    facilities: [{ id: 'FAC-0101', name: 'Mwansa Urban Clinic' }],
  },
  {
    label: 'Clinician, Kalemba Rural Health Centre',
    token: 'dev-clinician-0207',
    canRecordContact: true,
    facilities: [{ id: 'FAC-0207', name: 'Kalemba Rural Health Centre' }],
  },
  {
    label: 'District officer, Mwansa',
    token: 'dev-district-mwansa',
    canRecordContact: false,
    facilities: MWANSA,
  },
];
