-- Seed data for CareLink Follow-Up. ALL DATA IS INVENTED.
-- Loads the starter data from section 15.4 exactly, then expands to
-- 100,000 patients and 400,000 visits in total.
-- Generated dates are relative to CURRENT_DATE so the list stays realistic whenever it is run.
-- Safe to re-run: it clears the tables first.

\set ON_ERROR_STOP on
BEGIN;

TRUNCATE contact_attempt, visit, patient, facility CASCADE;

-- Facilities: the three starter facilities, plus seven invented ones.
-- Mwansa district has three facilities, so a district officer has more than one to read.
INSERT INTO facility (id, name, district) VALUES
  ('FAC-0101', 'Mwansa Urban Clinic',          'Mwansa'),
  ('FAC-0207', 'Kalemba Rural Health Centre',  'Kalemba'),
  ('FAC-0312', 'Chembe District Hospital',     'Chembe'),
  ('FAC-0102', 'Mwansa East Clinic',           'Mwansa'),
  ('FAC-0103', 'Mwansa North Health Post',     'Mwansa'),
  ('FAC-0208', 'Kalemba South Health Post',    'Kalemba'),
  ('FAC-0313', 'Chembe Rural Health Centre',   'Chembe'),
  ('FAC-0401', 'Nsato Health Centre',          'Nsato'),
  ('FAC-0402', 'Nsato Mission Clinic',         'Nsato'),
  ('FAC-0501', 'Lubemba Urban Clinic',         'Lubemba');

-- Starter patients and visits, exactly as in section 15.4.
INSERT INTO patient (facility_id, patient_number, first_name, last_name, date_of_birth, sex, phone_number) VALUES
  ('FAC-0101', '0101-000471', 'Thandiwe', 'Banda', '1991-03-14', 'female', '+260970000471'),
  ('FAC-0101', '0101-000472', 'Mulenga',  'Phiri', '1978-11-02', 'male',   NULL),
  ('FAC-0207', '0207-000018', 'Chipo',    'Zulu',  '2015-07-23', 'female', '+260960000018');

INSERT INTO visit (patient_id, visit_date, next_appointment_date, visit_type, created_at)
SELECT p.id, v.visit_date::date, v.next_appt::date, v.visit_type, v.visit_date::timestamptz + interval '9 hours'
FROM (VALUES
  ('0101-000471', '2026-07-02', '2026-08-02', 'routine'),
  ('0101-000471', '2026-08-05', '2026-09-05', 'follow_up'),
  ('0101-000472', '2026-08-20', '2026-09-10', 'routine'),
  ('0207-000018', '2026-09-28', NULL,         'unscheduled')
) AS v(patient_number, visit_date, next_appt, visit_type)
JOIN patient p ON p.patient_number = v.patient_number;

-- Generated patients: 99,997, skewed so FAC-0101 has well over 10,000 on its follow-up list.
SELECT setseed(0.2026);

CREATE TEMP TABLE gen ON COMMIT DROP AS
SELECT
  g,
  gen_random_uuid() AS id,
  CASE
    WHEN g <= 35000 THEN 'FAC-0101' WHEN g <= 50000 THEN 'FAC-0207'
    WHEN g <= 65000 THEN 'FAC-0312' WHEN g <= 73000 THEN 'FAC-0102'
    WHEN g <= 80000 THEN 'FAC-0103' WHEN g <= 86000 THEN 'FAC-0208'
    WHEN g <= 92000 THEN 'FAC-0313' WHEN g <= 96000 THEN 'FAC-0401'
    WHEN g <= 98500 THEN 'FAC-0402' ELSE 'FAC-0501'
  END AS facility_id,
  floor(random() * 120)::int      AS last_offset,  -- most recent visit was 0 to 119 days ago
  7 + floor(random() * 84)::int   AS appt_gap,     -- next appointment 7 to 90 days after it
  random() < 0.85                 AS has_next,     -- 15% have no next appointment set
  floor(random() * 20)::int       AS fn,
  floor(random() * 20)::int       AS ln,
  random()                        AS r_sex,
  random() < 0.7                  AS has_phone,
  floor(random() * 27000)::int    AS dob_offset
FROM generate_series(1, 99997) AS g;

INSERT INTO patient (id, facility_id, patient_number, first_name, last_name, date_of_birth, sex, phone_number, created_at)
SELECT
  id,
  facility_id,
  substr(facility_id, 5) || '-' || lpad((100000 + g)::text, 6, '0'),  -- 100001+ never collides with starter numbers
  (ARRAY['Chileshe','Mwila','Bwalya','Mutale','Natasha','Kondwani','Musonda','Lweendo','Mapalo','Chisomo',
         'Kasonde','Nchimunya','Luyando','Mubanga','Temwani','Chilufya','Mwape','Kabwe','Inonge','Sepo'])[fn + 1],
  (ARRAY['Mumba','Tembo','Ngoma','Lungu','Sakala','Daka','Mulenga','Chanda','Bwalya','Kunda',
         'Musonda','Zimba','Mwanza','Nyirenda','Kapata','Siame','Mbewe','Simukonda','Hamoonga','Chisenga'])[ln + 1],
  date '1950-01-01' + dob_offset,
  CASE WHEN r_sex < 0.52 THEN 'female' WHEN r_sex < 0.99 THEN 'male' ELSE 'other' END,
  CASE WHEN has_phone THEN '+26097' || lpad(g::text, 7, '0') END,  -- invented numbers
  now()
FROM gen;

-- Generated visits: 399,996, so 400,000 in total with the 4 starter visits.
-- Visit i belongs to generated patient (i mod 99997) + 1, so each patient has 4 visits (a handful have 5).
-- Visits are 30 days apart. Every earlier visit's appointment was kept, 0 to 4 days late.
-- Only the most recent visit can leave an appointment open.
CREATE INDEX ON gen (g);
ANALYZE gen;

INSERT INTO visit (patient_id, visit_date, next_appointment_date, visit_type, created_at)
SELECT
  gen.id,
  v.visit_date,
  CASE
    WHEN v.is_latest THEN CASE WHEN gen.has_next THEN v.visit_date + gen.appt_gap END
    ELSE v.visit_date + 30 - (v.i % 5)
  END,
  CASE WHEN v.s = 0 THEN 'routine' WHEN v.i % 9 = 0 THEN 'unscheduled' ELSE 'follow_up' END,
  v.visit_date::timestamptz + interval '8 hours' + (v.i % 480) * interval '1 minute'
FROM generate_series(0, 399995) AS i
JOIN gen ON gen.g = (i % 99997) + 1
CROSS JOIN LATERAL (
  SELECT
    i,
    i / 99997 AS s,
    (i / 99997) = (399995 - (i % 99997)) / 99997 AS is_latest,
    current_date - gen.last_offset
      - (((399995 - (i % 99997)) / 99997) - (i / 99997)) * 30 AS visit_date
) v;

COMMIT;

ANALYZE facility;
ANALYZE patient;
ANALYZE visit;
ANALYZE contact_attempt;

SELECT (SELECT count(*) FROM patient) AS patients, (SELECT count(*) FROM visit) AS visits;
