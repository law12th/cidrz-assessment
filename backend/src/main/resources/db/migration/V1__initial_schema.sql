-- CareLink Follow-Up: initial schema. Runs from an empty database (Flyway, over JDBC).
-- Each choice beyond the minimum schema in the brief (section 15.1) is explained in the README.
--
-- Every table carries the same audit and soft-delete columns, as the kwasila services do. That is
-- not ceremony here: these are clinical records. Section D1 of the brief is explicit that duplicate
-- patients created by a replayed sync are "not rows to be deleted casually", and a national record
-- that someone can make disappear with a DELETE is not one a Ministry can audit. Removing a row
-- means setting is_deleted; every read filters on it.

CREATE TABLE facility (
    id          text PRIMARY KEY,            -- e.g. 'FAC-0101'; a national code, stable across systems
    name        text NOT NULL,
    district    text NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_at  timestamptz,
    updated_by  uuid,
    version     integer NOT NULL DEFAULT 0,  -- optimistic locking, for when facilities become editable
    is_deleted  boolean NOT NULL DEFAULT false,
    deleted_at  timestamptz,
    CONSTRAINT facility_id_format CHECK (id ~ '^FAC-[0-9]{4}$'),
    CONSTRAINT facility_deleted_at_set CHECK (is_deleted = false OR deleted_at IS NOT NULL)
);

CREATE TABLE patient (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    facility_id     text NOT NULL REFERENCES facility (id),
    patient_number  text NOT NULL,
    first_name      text NOT NULL,
    last_name       text NOT NULL,
    date_of_birth   date NOT NULL,
    sex             text NOT NULL,
    phone_number    text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid,
    updated_at      timestamptz,
    updated_by      uuid,
    version         integer NOT NULL DEFAULT 0,
    is_deleted      boolean NOT NULL DEFAULT false,
    deleted_at      timestamptz,
    CONSTRAINT patient_number_unique_in_facility UNIQUE (facility_id, patient_number),
    CONSTRAINT patient_sex_valid CHECK (sex IN ('female', 'male', 'other')),
    CONSTRAINT patient_names_not_blank CHECK (btrim(first_name) <> '' AND btrim(last_name) <> ''),
    CONSTRAINT patient_deleted_at_set CHECK (is_deleted = false OR deleted_at IS NOT NULL)
);

CREATE TABLE visit (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id              uuid NOT NULL REFERENCES patient (id),
    visit_date              date NOT NULL,
    next_appointment_date   date,
    visit_type              text NOT NULL,
    created_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid,
    updated_at              timestamptz,
    updated_by              uuid,
    version                 integer NOT NULL DEFAULT 0,
    is_deleted              boolean NOT NULL DEFAULT false,
    deleted_at              timestamptz,
    CONSTRAINT visit_type_valid CHECK (visit_type IN ('routine', 'follow_up', 'unscheduled')),
    CONSTRAINT next_appointment_not_before_visit
        CHECK (next_appointment_date IS NULL OR next_appointment_date >= visit_date),
    CONSTRAINT visit_deleted_at_set CHECK (is_deleted = false OR deleted_at IS NOT NULL)
    -- "visit_date not in the future" is enforced by the application, in the facility's time zone.
    -- A CHECK using now() would only be evaluated on write, and in the database server's time zone.
);

-- Contact attempts recorded through POST /api/follow-up/{id}/contacted.
CREATE TABLE contact_attempt (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id       uuid NOT NULL REFERENCES patient (id),
    contacted_at     timestamptz NOT NULL,
    note             text,
    recorded_by      text NOT NULL,
    idempotency_key  text,
    created_at       timestamptz NOT NULL DEFAULT now(),
    created_by       uuid,
    updated_at       timestamptz,
    updated_by       uuid,
    version          integer NOT NULL DEFAULT 0,
    is_deleted       boolean NOT NULL DEFAULT false,
    deleted_at       timestamptz,
    CONSTRAINT contact_note_length CHECK (note IS NULL OR char_length(note) <= 500),
    -- The authority for replay safety. Redis only ever accelerates what this constraint guarantees.
    CONSTRAINT contact_idempotency_key_unique UNIQUE (idempotency_key),
    CONSTRAINT contact_deleted_at_set CHECK (is_deleted = false OR deleted_at IS NOT NULL)
);

-- Finds a facility's live patients quickly (the outer side of the follow-up query).
-- Partial, because every read filters on is_deleted and a partial index stays smaller.
CREATE INDEX patient_facility_idx ON patient (facility_id, id) WHERE is_deleted = false;

-- Finds each patient's most recent visit with a single index probe.
-- The sort order matches the tie-break used by the query exactly, and INCLUDE lets the probe answer
-- from the index alone without visiting the table.
CREATE INDEX visit_latest_idx
    ON visit (patient_id, visit_date DESC, created_at DESC, id DESC)
    INCLUDE (next_appointment_date)
    WHERE is_deleted = false;

-- Latest contact attempt per patient.
CREATE INDEX contact_attempt_patient_idx
    ON contact_attempt (patient_id, contacted_at DESC) WHERE is_deleted = false;
