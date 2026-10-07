# D. Resilience, offline and data integrity

Both answers assume Section A's facility node, with its own PostgreSQL and outbox.

## D1, duplicate submissions

**1. Changing the API so a replay is safe.** Three changes, in the order they matter.

- **Identifiers come from the client.** A patient registered offline gets a UUIDv7 on the node, so
  `POST /patients` is an upsert on a key the caller chose and a replay collides with itself —
  removing the duplicate-patient half outright. The change I would make first.
- **Every write carries an `operation_id`**, recorded at national under a unique constraint *in the
  same transaction as its effect*. Section B already does this for contact attempts: a unique
  `idempotency_key` with `ON CONFLICT DO NOTHING`, Redis in front as an accelerator that is
  explicitly not the authority.
- **A replay returns the original answer**, not silence — the client needs the created id to carry
  on. The same key with a *different* payload is a 409; accepting it would corrupt data quietly.

The sync endpoint also returns a result **per operation**, so a batch that times out halfway resumes
from the first unacknowledged one instead of being replayed whole — which is what caused this.

**2. What the client must do.** Generate the `operation_id` when the user acts and persist it with
the queued operation; generating it at send time defeats the design, because every retry then looks
like new work. This is the one assumption the server depends on. Queue durably in the same
transaction as the clinical record — the outbox pattern — acknowledge by operation id rather than by
batch, retry the same id, and treat 409 as needing a human.

**3. How long that state is kept.** The `operation_id` table is kept **90 days** at national: enough
for a node switched off over a holiday, a restored backup, or the USB-update path for sites offline
for weeks. The cost is a uuid and a timestamp against ~400,000 visits a year; Section B's Redis entry
is a 72-hour accelerator on top. Pruning would let an ancient replay through, so national rejects any
operation whose client timestamp predates the window with a 409 and routes it to review — always a
bug or a restored backup, and both deserve a person.

**4. The duplicates already there.** Nothing is deleted; Section B's schema already carries
`is_deleted` and `deleted_at`, and I would add `superseded_by`. Duplicate visits are findable
deterministically — same patient, `visit_date`, `visit_type` and `next_appointment_date`, minutes
apart — so I would run that as a **report**, check a sample against what the clinic remembers, then
mark the later row superseded.

Duplicate patients are not a data-cleaning job: merging two records merges two clinical histories,
and getting it wrong is a safety event. The index only *proposes*; a person at the owning facility
confirms, because they can ring the patient; the merge is its own row (surviving id, merged id, who
decided, when, why); the loser is marked merged rather than destroyed, so it is **reversible**.
Nothing merges automatically. Order of work: fix the API, detect in report-only mode, then merge with
human review — and tell the clinics who is affected.

## D2, working through an outage

**1. What the client caches, where, and for how long.** The browser caches **no patient data at all**
— no `localStorage`, no IndexedDB, no service-worker cache. That is why Section A puts a server in
the clinic: the cache that survives the outage is one machine the clinic controls, not a copy on each
shared PC quietly drifting apart.

| Where | What | How long |
|---|---|---|
| Browser | Current screen and token in memory; UI preferences | Session; 15 min idle |
| Facility node | That facility's patients, visits, contact attempts, reference data, outbox | Indefinitely — the record until synced |
| Facility node | Argon2 verifiers for offline login | 14 days from last online login |
| National | Redis caches and rate-limit counters | Seconds to hours, disposable |

**2. Synchronising the queue.** Ordering is a per-facility monotonic sequence, applied in that order
at national. Retry is exponential with jitter, one second to a five-minute ceiling, retrying
indefinitely for transient failures — the jitter is not decoration, because when a tower returns
several hundred facilities reconnect in the same instant. **Partial failure is the part that
matters:** national applies what it can and returns a result per operation, and a permanently failing
operation moves to a review queue and **does not block the queue behind it**. One poisoned record
must never stop a facility syncing for two days. A 429 is obeyed with its `Retry-After`.

**3. Monday's offline edit against Tuesday's central change.** It depends on what was edited, which
is why Section A separates the two. A clinical event does not conflict: visits are append-only, so
both facts are kept. A mutable field does: the Monday edit was made against row version 3, national
moved to 4 on Tuesday, rejects it and queues it. On Wednesday the clinician sees, on their own node,
*"You changed Thandiwe Banda's phone number on Monday. It was also changed at the district on
Tuesday. Which is right?"* — both values, both timestamps, both authors. Never a silent overwrite.
**The owning facility decides**, because they can ring the number and find out; national decides for
reference data. If nobody acts within 14 days the central value stands and the local one is kept as a
superseded version — a queue with no default grows until it is ignored.

**4. Sensitive data on a shared clinic computer.** The strongest protection is the design: the
browser holds no patient data, so a shared PC has nothing to leak. The rest supports it — full-disk
encryption on the node (LUKS with the TPM) so a stolen box is inert; a 15-minute idle timeout with no
"remember me"; opaque UUIDs in URLs rather than national IDs, so nothing sensitive reaches browser
history or a proxy log. The printed morning list is paper with real names on it and needs a disposal
routine: a policy gap rather than a technical one, which I would raise rather than ignore.

**I would refuse to store at all:** patient data in any browser storage; credentials anywhere but
memory; a reversible form of any password, offline login included — only an Argon2 verifier;
national identifiers in logs, URLs or analytics; clinical notes in crash payloads; patient data on a
personal device.
