# C. Code review

Reviewed as a pull request. **Critical** = harms a patient, loses data or exposes records;
**major** = hurts users or the fleet; **minor** = quality and cost.

## C1, back-end service

**Critical**

| # | Problem | Cost to a clinic |
|---|---|---|
| 1 | SQL built by string concatenation, in all three methods | A clinician searching **O'Brien** breaks the query; a hostile `facility` reads the whole patient table. `notes` is free text, so it fires by accident first |
| 2 | Live `sa` credentials in source | Repository access means administrator rights over national patient data; rotation needs a release to 1,500 nodes |
| 3 | `catch (Exception) { // ignore }` | The encounter is lost and the clinician is told it saved; nobody finds out until the next visit |
| 4 | `userRole` accepted, never used | Advertises a check that does not exist; any caller reads any facility |
| 5 | Logs the search term and the first result's `NationalId` | Patient names and national identifiers reach logs read by staff with no clinical relationship |

**Major** — 6, the insert and update are not one transaction, so `last_visit_date` and the encounter
rows disagree and nothing detects it. 7, `DueForFollowUp` loads every patient then queries visits per
patient: 35,003 round trips for one screen, where the brief requires filtering in the database. 8, a
patient is added once per overdue visit, so three missed appointments means three calls to one
person. 9, the rule is wrong — *any* past appointment, not the most recent visit — so patients who
came back never leave the list and staff distrust it. 10, connections are never disposed, giving pool
exhaustion whose symptom looks nothing like its cause. 11, `DateTime.Now` is server time, not the
facility's, so days overdue is wrong near midnight and always under UTC. 12, `Search("", facility,
"system")` is a magic role bypassing a check that does not exist yet.
**Minor** — 13, `SELECT *`, a leading-wildcard `LIKE` and no paging: an unindexable scan returning
every match.

**Blockers: 1, 2 and 3.** Parameterise every statement; move credentials out of source and replace
`sa` with a least-privilege role; let the exception propagate. Each already causes harm in normal
use, not only under attack — an apostrophe triggers 1, a clone triggers 2, a full disk triggers 3.
Numbers 4 and 5 are equally critical; they sit below only because a facility-scoped token is a larger
change, and I would not let the sprint close without them.

## C2, front-end component

**Critical**

| # | Problem | Cost to a clinic |
|---|---|---|
| 1 | `markContacted` mutates the object, posts no body, checks nothing | Three failures in four lines: nothing visibly happens so staff click again, the contract requires `contacted_at`, and a failed POST is never reported — a patient recorded as contacted who never was |
| 2 | No `response.ok`, no `.catch`, anywhere | `setLoading(false)` runs only on success, so any failure leaves "loading" for ever with no retry. During the outages the brief describes, the screen hangs |
| 3 | The response is treated as an array | The contract returns `{page, page_size, total, items}`; `patients.filter` throws. Against the documented API this never worked |
| 4 | `<div onClick>`, unlabelled input, status by colour alone | Staff work "with a keyboard only"; a `div` takes no focus and ignores Enter and Space, so the one action here is unreachable. Red on black is invisible to a colour-blind user |
| 5 | Polls every 3 seconds, per open screen | A few hundred clinics is ~1,000 requests a second nationally, over a metered APN, for a list that changes hourly |

**Major** — 6, `?facility=` instead of `facility_id=`: a strict server returns 400, a lenient one
quietly returns somebody else's list. 7, the whole list is filtered and sorted in the browser, so
every poll downloads 10,000 rows (~3 MB) to show 50. 8, no request cancellation, so a slower first
response lands last and wins, showing the facility you just left. 9, `key={i}` in a list that
re-sorts, so React reuses the wrong rows and a click can land on a patient who moved. 10, the sort
and polling effects are both subtler than they look: neither is the infinite loop or interval leak it
appears to be — React bails out on an identical reference, and the cleanup clears the previous timer.
Worse, the sort mutates the array React already rendered then suppresses the re-render, and every
render restarts the countdown, so while anyone types the refresh never fires. 11, `any` throughout
and `fetch` inside the component, both ruled out by the brief — types would have caught number 3 at
compile time, and as written it cannot be tested without a network.
**Minor** — 12, no empty state, and `days_overdue > 30` hard-codes a server rule.

**Blockers: 1, 2 and 4.** A clinical action that silently does nothing, a screen that hangs for ever
on any failure, and a screen the intended users cannot operate by keyboard. Number 3 is just as
broken, but a crash announces itself in the first five minutes of testing and never reaches a clinic.
These three ship looking fine and fail quietly in use.

## The refactor: C2

I chose the front-end extract because its worst problems need judgement where C1's do not:
parameterised queries and a secret store are the answer any reviewer would give. I have addressed
**the three blockers** and left the rest, as the brief asks. Dropping the 3-second poll comes with
the first — behind a service and a state machine, a background timer has nowhere sensible to live.

```tsx
import { useCallback, useEffect, useState } from 'react';

// The component depends on this interface, never on fetch, so it can be tested without a network
// and the transport can change without touching the screen.
export interface FollowUpService {
  list(facilityId: string, signal: AbortSignal): Promise<FollowUpPage>;
  recordContact(patientId: string, contactedAt: string, idempotencyKey: string): Promise<void>;
}

type ListState =
  | { status: 'loading' }
  | { status: 'ready'; page: FollowUpPage; loadedAt: Date }
  | { status: 'failed'; message: string };

/** Per row, so one failed call cannot make every button look busy or broken. */
type ContactState = Record<string, 'idle' | 'saving' | 'done' | 'failed'>;

export function FollowUpList({ facilityId, service }: { facilityId: string; service: FollowUpService }) {
  const [state, setState] = useState<ListState>({ status: 'loading' });
  const [contact, setContact] = useState<ContactState>({});
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    // Cancels the request in flight when the facility changes, so a slow earlier response
    // can never land last and overwrite a newer list.
    const controller = new AbortController();
    setState({ status: 'loading' });
    service
      .list(facilityId, controller.signal)
      .then((page) => setState({ status: 'ready', page, loadedAt: new Date() }))
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        setState({ status: 'failed', message: describeError(error) });
      });
    return () => controller.abort();
  }, [service, facilityId, attempt]);

  const refresh = useCallback(() => setAttempt((n) => n + 1), []);

  async function markContacted(patient: FollowUpItem) {
    setContact((c) => ({ ...c, [patient.id]: 'saving' }));
    try {
      // A real body, as the contract requires, and a key that makes a retry after a timeout
      // safe: the server records the attempt once however many times this is sent.
      await service.recordContact(patient.id, new Date().toISOString(), crypto.randomUUID());
      setContact((c) => ({ ...c, [patient.id]: 'done' }));
    } catch {
      // The failure is shown next to the patient it belongs to. Never report success we do not have.
      setContact((c) => ({ ...c, [patient.id]: 'failed' }));
    }
  }

  if (state.status === 'loading') return <p role="status">Loading the follow-up list…</p>;

  if (state.status === 'failed') {
    return (
      <div role="alert">
        <p>{state.message}</p>
        <button type="button" onClick={refresh}>Try again</button>
      </div>
    );
  }

  const { items } = state.page;

  return (
    <section aria-labelledby="follow-up-heading">
      <h2 id="follow-up-heading">Patients to follow up ({state.page.total})</h2>
      <p>
        Loaded at {state.loadedAt.toLocaleTimeString()}.{' '}
        <button type="button" onClick={refresh}>Refresh</button>
      </p>

      {items.length === 0 ? (
        <p role="status">No patients need following up at this facility today.</p>
      ) : (
        <table>
          <caption className="visually-hidden">Patients with a missed follow-up appointment</caption>
          <thead>
            <tr>
              <th scope="col">Patient</th>
              <th scope="col">Days overdue</th>
              <th scope="col">Status</th>
              <th scope="col">Action</th>
            </tr>
          </thead>
          <tbody>
            {/* Keyed by patient id, not by position: the row must follow the patient when the list re-sorts. */}
            {items.map((patient) => (
              <tr key={patient.id}>
                <th scope="row">{patient.first_name} {patient.last_name}</th>
                <td>{patient.days_overdue}</td>
                <td>
                  {/* A word and a shape as well as a colour, so status survives colour blindness,
                      a monochrome printout and an old monitor. */}
                  <span className={`status status--${patient.follow_up_status}`}>
                    <span aria-hidden="true">{SHAPE[patient.follow_up_status]} </span>
                    {STATUS_LABEL[patient.follow_up_status]}
                  </span>
                </td>
                <td>
                  {/* A real button: focusable, fires on Enter and Space, and named for a screen
                      reader so "Mark contacted" is not ambiguous across 50 identical rows. */}
                  <button
                    type="button"
                    onClick={() => markContacted(patient)}
                    disabled={contact[patient.id] === 'saving' || contact[patient.id] === 'done'}
                  >
                    {contact[patient.id] === 'done' ? 'Contacted' : 'Mark contacted'}
                    <span className="visually-hidden"> — {patient.first_name} {patient.last_name}</span>
                  </button>
                  {contact[patient.id] === 'failed' && (
                    <span role="alert"> Not saved. Try again.</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
```

**What I deliberately did not fix here**, because the brief asks for the worst two or three rather
than the whole file: the filter box is gone rather than repaired, because filtering and sorting
belong on the server once a list reaches 10,000 rows, and that is a change to the API contract as
much as to this component; and paging is not shown. Both are built properly in my Section B slice,
in `web/src/features/followUp/`, which is where I would point a reviewer for the finished version.
