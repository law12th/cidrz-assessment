# CareLink Follow-Up: technical assessment

This repository holds my submission for the CIDRZ technical assessment, position **Senior Software Engineer – Front End**. 

| Section | Where |
|---|---|
| A. Architecture and trade-offs | [A-architecture.md](A-architecture.md) |
| B. Vertical slice | `backend/` (API), `web/` (screen), `db/seed/` (seed data); this README |
| C. Code review and refactor | [C-review.md](C-review.md) |
| D. Resilience, offline, data integrity | [D-resilience.md](D-resilience.md) |
| E. Presentation | [E-presentation.pdf](E-presentation.pdf) |
| F. Delivery and engineering practice | [F-practice.md](F-practice.md) |
| G. Executive briefing | [G-briefing.md](G-briefing.md) |
| Tool-use declaration | [declaration.md](declaration.md) |

All data in this repository is invented. Nothing here is real patient, CIDRZ, Ministry or SmartCare data.

## What I built, and what I stubbed

**Both halves of Section B are real. Nothing is stubbed, and nothing is pointed at fixture data.**
The endpoint runs against PostgreSQL with the seeded 100,000 patients, and the screen consumes that
endpoint over HTTP.

- **API** (`backend/`): Java 25 and reactive Spring Boot 4 (WebFlux, R2DBC) on PostgreSQL 16, with Redis.
  - `GET /api/follow-up` as specified.
  - `POST /api/follow-up/{id}/contacted`, so that "clinician writes, district officer cannot" has something to test.
  - A Flyway migration from empty, and a seed script for 100,000 patients and 400,000 visits.
  - OpenAPI generated from the controllers, at `/v3/api-docs`, with Swagger UI at `/swagger-ui.html`.
- **Screen** (`web/`): React 19 and TypeScript, built with Vite. It is the follow-up list with filters, server-side sorting and paging, recording a contact, and loading, empty and error states with a retry.

## Stack and why

- **Java 25 and reactive Spring Boot 4.1** match the architecture in Section A. There, the same artifact runs on facility nodes with 8 GB of RAM, where the work is mostly waiting on slow links. WebFlux handles that on a small fixed thread pool, and Reactor gives the sync agent backpressure and retry.
  - The cost is a steeper learning curve and harder debugging. A-architecture.md compares this with Spring MVC on virtual threads.
- **PostgreSQL 16:** no licence cost across 1,500 nodes, plus `LATERAL` joins, `INCLUDE` indexes and row-level security, all of which this design uses or plans to use.
- **Redis** carries the rate limiter's counters and remembers idempotency keys that have already been written. It is deliberately an accelerator, never an authority: the unique constraint on `contact_attempt.idempotency_key` is what makes a replay safe, and both Redis users fail open. I verified this by running the API with Redis unreachable — reads and writes both still succeed.
- **React with TypeScript**, as preferred in the brief. The build targets ES2017 for older clinic browsers, and the system font stack avoids downloading fonts on an offline LAN.

## How to run it from a clean machine

**You need:** Docker running, and Node 20 or later. The API needs no local JDK: it is built inside its container, and the Gradle wrapper fetches its own JDK when you run the tests.

```sh
git clone https://github.com/law12th/cidrz-assessment.git
cd cidrz-assessment
./scripts/run.sh
```

That last line is the whole thing. It checks Docker is up, starts PostgreSQL, Redis and the API, waits for the API to report ready, loads the seed data if the database is empty, starts the screen, and opens it in a browser. Press Ctrl+C to stop; the containers stop with it and the data is kept, so the next run starts in seconds.

| | |
|---|---|
| `./scripts/run.sh` | Everything, and open the screen |
| `./scripts/run.sh --api-only` | No screen. For curl or the Swagger UI |
| `./scripts/run.sh --reseed` | Reload the 100,000-patient seed first |
| `./scripts/run.sh --no-open` | Everything, but do not open a browser |
| `./scripts/run.sh --stop` | Stop it all |

If something already holds a port, the script says which one and stops rather than failing obscurely. Move it and carry on: `API_PORT=8081 WEB_PORT=5174 ./scripts/run.sh`.

To do it by hand instead, or on Windows without a POSIX shell:

```sh
docker compose up -d --build     # the API applies the migration when it starts
docker compose exec -T db psql -v ON_ERROR_STOP=1 -U carelink -d carelink -f /seed/seed.sql
cd web && npm ci && npm run dev  # the screen, at http://localhost:5173
```

**Run all tests with one command** (Docker must be running):

```sh
./scripts/test-all.sh
```

This runs `./gradlew test` in `backend/` (unit tests, plus integration tests against a real PostgreSQL and a real Redis started by Testcontainers) and then `npm test` in `web/`.

**Try the API directly.** These tokens exist only in the `local` profile:

```sh
curl -s -H "Authorization: Bearer dev-clinician-0101" \
  "http://localhost:8080/api/follow-up?facility_id=FAC-0101&status=overdue&page_size=5"
# District officer: may read Mwansa facilities, gets 403 for FAC-0207
curl -s -H "Authorization: Bearer dev-district-mwansa" "http://localhost:8080/api/follow-up?facility_id=FAC-0207"
```

**See the error state:** with the screen open, run `docker compose stop api` in another terminal and press "Show list". Then `docker compose start api` and press "Try again".

The "Signed in as (development)" menu in the screen's header switches between a clinician and a district officer. It stands in for signing in.

## The follow-up rule: my interpretation

The brief leaves edge cases open. Here is how I resolved each one.

1. **"Most recent visit"** means the latest `visit_date`. Two visits on the same day are separated by `created_at`, then by `id`, so the answer is always the same.
2. **Only the most recent visit's appointment counts.** If the most recent visit set no appointment, the patient is not listed (this is Chipo Zulu's case). Any visit after a missed appointment, whether scheduled or not, takes the patient off the list, because that visit is now the most recent. "No visit since that date" therefore follows from point 1.
3. **"More than n days" is strict.** An appointment exactly *n* days ago is not listed; *n* + 1 days is. The default *n* is 7, and `overdue_days` accepts 0 to 365.
4. **"Today" is the facility's local date** (Africa/Lusaka), taken from an injectable clock, not from the database server's clock or time zone.
5. **The three statuses** come from `days_overdue` (today minus the appointment date):

| Status | days_overdue | Meaning |
|---|---|---|
| `due_soon` | −7 to 0 | Appointment today or in the next 7 days |
| (none) | 1 to *n* | Grace period: may just be late |
| `missed` (shown as "Recently missed") | *n*+1 to *n*+14 | The brief's rule, in the last two weeks |
| `overdue` | *n*+15 or more | Missed some time ago |

   This reproduces appendix 15.3: 8 days is `missed`; 26 and 31 days are `overdue`. The 7-day and 14-day windows are configuration (`carelink.follow-up.*`), not code, because they are programme decisions.

6. **With no `status` parameter, the list is the brief's rule** (missed plus overdue). Due-soon patients appear only when asked for. On the screen, the default option is labelled "Needs follow-up".
7. **`missed_appointment_date`** keeps its name from the contract. For due-soon rows it holds the upcoming appointment, and `days_overdue` is zero or negative. A v2 contract would rename it `appointment_date`.
8. **Recording a contact does not remove a patient from the list.** Only a visit does that, because a phone call is not the patient coming back. The row shows the last contact date instead.

## Schema additions (beyond appendix 15.1)

- **`facility` table**, with a foreign key from `patient`. It supplies `facility_name` and the district, which district-officer access depends on.
- **`contact_attempt` table** for the POST endpoint. It has a unique `idempotency_key`, so a replayed request (for example, after a timeout) is recorded once. The constraint lives in the database on purpose: it holds across instances and restarts, and it needs no lock and no read-then-write. Redis only short-circuits a replay that the constraint would have absorbed anyway, and a key is cached **after** the row is durable, never before, so a failed insert cannot suppress the client's genuine retry. Section D1 extends this idea.
- **Constraints** for everything the brief states:
  - `patient_number` unique within a facility;
  - `sex` and `visit_type` from fixed sets;
  - `next_appointment_date` not before `visit_date`.
- **"`visit_date` not in the future" is enforced by the application, not the database.** A database rule using `now()` would be checked only on write, and in the database server's time zone, not the clinic's.
- **Audit and soft-delete columns** (`created_by`, `updated_at`, `updated_by`, `version`, `is_deleted`, `deleted_at`) on every table, and every read filters on `is_deleted`. These are clinical records: Section D1 is explicit that duplicates created by a replayed sync are not rows to delete casually, and a national record that a `DELETE` can erase is not one a Ministry can audit. Merging a duplicate marks the loser deleted and keeps it readable. `version` is there for optimistic locking when these rows become editable.
- **`visit_latest_idx`** on `(patient_id, visit_date DESC, created_at DESC, id DESC) INCLUDE (next_appointment_date)`, partial on `is_deleted = false`. Finding each patient's latest visit is then one index probe that never reads the table. I confirmed it is an index-only scan with `EXPLAIN ANALYZE`.
- **UUID keys**, so offline facilities can create records without colliding (Section A). This slice uses `gen_random_uuid()`; production would use time-ordered UUIDv7.

## Performance

**API, measured on the seeded data:** 100,000 patients and 400,000 visits, PostgreSQL 16.

- The largest facility, FAC-0101, has 35,002 patients, of whom 15,566 are on the default list.
- The query, including its total count, runs in **about 130 ms** for any page, measured end to end over HTTP (mean of five calls each at pages 1, 10, 100 and 241; 122 to 136 ms). The plan is one index-only probe per patient.
- Filtering, sorting, paging and counting all happen in SQL. The application never holds more than one page.

If this needed to be faster, the next step would be a small `follow_up` table, maintained whenever a visit is written. The list would then be a plain indexed range scan. I did not build it, because 130 ms is acceptable here and the projection is one more thing that can drift out of step.

**Screen with 10,000 records.** The fix is **server-side paging** (50 rows by default, up to 200), with sorting done by the server. I rejected virtual scrolling for three reasons:
- it breaks the browser's find-in-page and printing;
- screen readers handle a table whose rows keep appearing and disappearing badly;
- it still downloads all 10,000 records over a slow clinic link.

| Measure | Before: all 10,000 rows at once | After: one page of 50 |
|---|---|---|
| DOM elements in the table | 150,850 | 783 |
| JSON downloaded | 2,953 KB | 14.6 KB |
| Render time, jsdom | 20,389 ms | 237 ms |

All three come from `npm run measure`. The first two do not depend on the machine. The third does,
and jsdom is much slower than a browser, so treat it as the shape of the problem — roughly **86x** —
rather than as a clinic figure.

For a real browser number on an old clinic PC: run `npm run dev`, open `http://localhost:5173/?bench`,
set CPU to "4x slowdown" in the Chrome DevTools Performance panel, and press "Run measurement". It
renders the real table component 5 times in each mode and reports the median.

## Tests and why these ones

**API** (`./gradlew test`):
- **The rule's boundaries** as fast unit tests: −8, −7, 0, 1, *n*, *n*+1, *n*+14 and *n*+15 days, at several thresholds. A test also checks that every day inside a filter's range classifies as that status, so the SQL ranges and the labels cannot drift apart.
- **End-to-end against real PostgreSQL** through the HTTP API. The migration runs from empty, and the clock is fixed to 6 October 2026. These tests cover:
  - the brief's worked example, exactly;
  - exactly *n* days compared with *n*+1;
  - a patient who came back after missing an appointment;
  - two visits on the same day;
  - due soon;
  - another facility's patients never leaking into a list;
  - sorting and paging, including a page past the end;
  - every validation and access rule (401, 403, 404);
  - idempotent contact recording;
  - a soft-deleted patient never appearing on a list;
  - the error shape and correlation ID;
  - the rate limiter refusing a flood with a 429 in the contract's error shape.
- **A test that pins the wire format**, comparing a whole response against the contract's JSON in strict mode. Deserialising into my own types would have passed even with every field misnamed, because both ends apply the same naming strategy; strict comparison also distinguishes `last_contact_attempt: null` from the field being absent, which the screen's types treat as different values.
- **A logging test.** It makes real requests, then asserts that the logs carry the correlation ID and contain no patient names, phone numbers, patient numbers, note text or tokens.
- **BlockHound** fails any test that blocks an event-loop thread, which is the classic reactive bug. I checked it is actually installed rather than silently inert, by making a blocking call inside a reactive operator and confirming the test failed.

I used a real database rather than mocks because the rule lives in SQL. A mocked repository would test nothing that matters.

**Screen** (`npm test`): I focused on what would hurt a clinic if it broke:
- every state: loading, the list, the API stopped (with a working retry), and empty;
- status shown as words;
- server-side sorting, and focus after changing page;
- recording a contact;
- a district officer having no write control;
- the filter form's validation, error wiring and tab order;
- an axe accessibility scan;
- the service layer's handling of the contract error shape, network failure, timeouts and malformed responses.

I did not test the CSS or snapshot the markup. Those tests break on every harmless change and catch little.

## Accessibility: three decisions and one thing I chose not to do

1. **A real `<table>`.** Each patient's name is the row header, and the sortable column has `aria-sort`. Moving across a row with a screen reader announces whose record it is, and the sort order is spoken. The sort control is a `<button>`, not a clickable `<div>`.
2. **Status is never colour alone.** Each status has a word ("Overdue"), a distinct shape (▲ ◆ ○) and a colour. All text colours are at least 6.3:1 against their background, well above the 4.5:1 minimum.
3. **Errors and changes are announced where the user is.**
   - An invalid field gets `aria-invalid`, its error is linked with `aria-describedby`, and focus moves to it.
   - After a page change, focus moves to the results heading.
   - "Record contact" results are announced through a live region without moving focus.
   - Load failures use `role="alert"` and include a "Try again" button.

**Not done: custom keyboard shortcuts**, such as J and K to move between rows. They would help power users, but they collide with screen-reader keys and nobody discovers them. Everything works with Tab, Enter and Space. I have also tested only with axe and Testing Library so far, not with a real screen reader. NVDA on Windows, as used on clinic PCs, is the next check.

## Logging and security notes

- **Logs** are structured JSON (ECS). Each request writes one line with method, path, status and duration, but never the query string or body. Every line carries the correlation ID, which is passed through Reactor's context.
- **Errors.** A 5xx is logged with its stack trace, while the client receives only a generic message and the correlation ID. A 4xx logs only its error code, never the rejected input.
- **Authentication in production.** The static tokens and `StaticTokenAuthenticationFilter` would be replaced by Spring Security's OAuth2 resource server, validating Keycloak-issued JWTs, with role, facility and district read from claims. `AccessPolicy` keeps all the authorisation rules in one place and would stay as it is. Facility nodes would also use cached offline credentials, as described in Section A.
- **Rate limiting.** 120 requests per principal per minute, in a fixed Redis window, returning 429. It is keyed on the principal rather than the caller's IP address, because a whole clinic sits behind one NAT and an IP-keyed limit would throttle a busy facility instead of the one browser looping. It exists mainly to survive a client bug: the front-end extract in Section C polls every three seconds, and that pattern across a few hundred clinics is what takes a national node down.
- **Health.** Readiness names only what the node cannot serve without (`readinessState`, `r2dbc`). A Redis outage shows as degraded on `/actuator/health`, but readiness stays up, so a working clinic is never taken out of service by a cache it does not need.
- **Not yet done in this slice:**
  - PostgreSQL row-level security (Section A's second line of defence), which needs a non-owner database role and `SET LOCAL` per transaction;
  - returning 409 when an idempotency key is reused with a different body.
- **Dev credentials.** The local database password and dev tokens appear only in `docker-compose.yml` and the `local` and `test` profiles. The base configuration reads credentials from the environment and has no tokens.

## What I left out, and what I would do next

1. Row-level security on `patient`, `visit` and `contact_attempt`, scoped per transaction.
2. Recording a note with a contact. The API accepts it, but the screen sends none; it needs a small dialog with proper focus handling.
3. A test with a real screen reader (NVDA), and a check on the oldest supported browser.
4. Keyset pagination ("after days_overdue X, patient id Y") for very deep pages, and the projection table if timings rise.
5. Publishing the generated OpenAPI document with each release, and diffing it in CI so a breaking change to the contract fails the build rather than reaching a client.
