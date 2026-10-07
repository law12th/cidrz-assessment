# F. Delivery and engineering practice

Joining as an engineer, not the manager: what I would do, push for, and how I would behave. No
delivery freeze is needed.

**The two changes I would make first: stop changes being applied directly on the production server,
and make the build trustworthy.** Everything else is unmeasurable until those two land. While
someone can edit production by hand, nobody can say what is running, a defect cannot be reproduced,
and a rollback is a guess. And a suite under 10% that fails for unrelated reasons is worse than no
suite, because the team has already learned to ignore red — so adding tests to it changes nothing.

## Days 1–30: find the ground

**Do:** deploy one release myself, by hand, writing down every step — that document becomes the
pipeline, and it earns the right to comment. Pair with the two engineers who patch production rather
than raising it in a meeting; they are working around a three-day release process, and the fix is to
make the pipeline faster than the workaround. Quarantine every flaky test the same week, so green
means something. Replace the coding-standards argument with a formatter and linter on CI.

**Leave alone:** the backlog, the architecture, large refactors, coverage targets.

**Know it worked:** the build is green on more than 95% of runs; every defect from the last fortnight
reproduces on a laptop; the deploy runbook exists.

## Days 31–60: make releasing boring

**Do:** short-lived branches merged behind a flag — fortnight-old defects mean changes land
in large, late batches. CI on every pull request: build, lint, tests, and a migration applied to a
throwaway database. One artifact promoted from staging to production, never rebuilt.
Expand-then-contract migrations so a release rolls back without a restore, and release rings, as in
Section A, so a bad release reaches five facilities rather than 1,500. Write tests on the paths that
actually broke — coverage on changed lines, not a global percentage, which only rewards testing the
easy code. Pair with the juniors on real changes rather than handing them tickets.

**Leave alone:** the untested legacy core — I would not backfill tests for code nobody is changing.

**Know it worked:** release time from three days to under an hour; fewer releases needing a hotfix;
no change reaching production outside the pipeline.

## Days 61–90: make it stick, and visible

**Do:** publish four numbers the programme can see — how often we release, how long a change takes
to reach a clinic, how often a release needs fixing, how long recovery takes — framed for a clinic,
not as engineering metrics. Quantify technical debt only where it has cost something: the three areas
that caused real incidents, not a register nobody reads. Generate API documentation from the code, as
I did in Section B. For the friction with programme teams, propose a thin definition of ready:
nothing starts until its acceptance criteria and its effect on clinic workflow are written down. Most
"requirements keep changing" is requirements never having been agreed.

**Leave alone:** team structure — 90 days is not enough to know whether the problem is structural or
just the absence of these basics.

**Know it worked:** defects traced to the previous fortnight down from roughly one a week; both
juniors shipping a change end to end unaided.

## How I would behave meanwhile

Do the work before proposing it: one green pipeline argues better than a proposal. Review generously
and ask only for the standard I am willing to meet myself. Never name an individual as the problem —
the person patching production at 19:00 is usually the one who cares most. And because a bad release
interrupts patient care, say so out loud when the pressure is to ship anyway.
