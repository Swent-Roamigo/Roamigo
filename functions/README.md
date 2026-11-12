# Trusted vote resolution

AI assistance was used for implementation support.

`createRevote` and `resolveVoteByOwner` are authenticated callable functions in
`europe-west1`, alongside the existing Functions backend. Android invokes them through
`VoteResolutionServiceFunctions`; no client writes resolution metadata directly.

Both operations read the trip, CLOSED source vote and its ballots in an Admin Firestore
transaction. They calculate tied winners from the actual ballot documents. The owner endpoint
checks authenticated UID against `trip.ownerId` and only accepts a tied option. The revote
endpoint derives the question/options/round/creator/time on the server and atomically creates
the child plus the parent's `nextRoundVoteId`. No ballot counters are persisted. Source content
and ballots are never changed. Repeated requests for the same committed outcome are idempotent;
competing outcomes serialize on the parent document.

Firestore rules deny **all direct client writes** to closed vote resolution/linkage metadata,
and deny linked-round creation by clients. They retain normal vote creation, creator-only
closure and post-close ballot protections. The Admin functions bypass rules, so their membership,
owner, tie and transition checks are the trusted authorization boundary. Rules alone do not
aggregate ballots.

## Trip end policy

There is deliberately **no server calendar cutoff** and no `COMPLETED` status shortcut. Existing
trip/history UI classifies completion using `Trip.isCompletedOn(today, deviceZone)`, independently
of stored status. Android resolution UI honors its read-only argument and that local-date policy;
the repository re-reads the trip from the server and checks the same local-calendar boundary
immediately before invoking the callable. The whole local end date remains writable, including
23/25-hour days. No trip-date migration is needed.

This calendar/read-only policy is client/repository-enforced. An authenticated member calling
an endpoint directly can resolve a *genuine* tie after the app's local end date, subject to the
same owner/tied-option permissions; they cannot resolve a non-tie or replace an existing decision.
Enforcing a shared end boundary on the server would require a separately agreed canonical
exclusive end instant and legacy-trip policy. Do not claim the current backend enforces the
viewer's calendar or navigation read-only state.

## Build, test and release

From the repository root:

```sh
npm ci --prefix functions
npm run build --prefix functions
VOTE_FUNCTIONS_URL=http://127.0.0.1:5001/demo-roamigo/europe-west1 \
  firebase emulators:exec --only auth,firestore,functions,database --project demo-roamigo \
  'node --test tests/firestore/*.test.mjs'
```

The tests use actual callable HTTP requests with Auth-emulator ID tokens, real Admin transactions,
and separate direct-client Firestore rule checks. They require no production service or credentials.
Use an isolated Firebase config/ports if the default emulator ports are occupied.

Deploy the two callable functions and the restrictive Firestore rules before releasing the
Android client. With restrictive rules but missing callables, resolution fails closed; there is
no direct-write fallback. Deployment is not performed by these tests. If any earlier permissive
resolution rules were deployed, audit historical resolution/linkage metadata before trusting it;
tightening rules does not retroactively validate old writes.
