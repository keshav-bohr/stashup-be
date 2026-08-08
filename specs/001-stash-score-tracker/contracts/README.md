# API Contracts

**Contract**: [openapi.yaml](./openapi.yaml) — OpenAPI 3.1, 23 paths, 31 operations, 21 schemas.

This file is the **design-time** contract. At build time, springdoc-openapi generates the schema
from the running application and CI diffs the two. A divergence fails the build, which is what
satisfies the constitution's requirement that the published schema be generated from or validated
against the running code rather than maintained by hand.

## Cross-cutting rules

Every one of these holds on every endpoint, and each is a constitution requirement rather than a
style preference.

| Rule | Shape |
|---|---|
| Money | `{ amountMinor: int64, currency: "INR" }` — integer minor units, explicit currency, never a decimal or float |
| Identifiers | Opaque strings (UUIDv7). Clients must not parse, order, or infer from them |
| Calendar values | `entryDate` and `period` carry no time and no timezone |
| Instants | `createdAt`, `computedAt`, `acknowledgedAt` are ISO 8601 UTC |
| Errors | RFC 9457 `application/problem+json`, extended with a stable `code` and the request's `correlationId` |
| Pagination | Keyset via `cursor` / `nextCursor`, `limit` max 100 — over-limit is **rejected**, not silently clamped |
| Auth | Bearer JWT on everything except `POST /auth/register`, `/auth/login`, `/auth/refresh` |
| Versioning | All paths under `/api/v1`; breaking changes go to `/api/v2` with 90 days of overlap |

`code` values are frozen once published. Clients match on them, so rewording one is a breaking
change even though the message text is free to improve.

## Decisions encoded in the contract

Several responses are shaped by spec requirements in ways that look odd without the reasoning.

**404 rather than 403 for another user's entry.** `GET /entries/{id}` returns the same 404 whether
the entry does not exist or belongs to someone else. A distinct 403 would confirm that a given ID
is real, which is an enumeration oracle over other people's records.

**Sending a friend request to someone who blocked you returns 201.** The request is silently
discarded. Returning an error would make blocks detectable by probing, and FR-032 requires a block
to be invisible to the blocked party.

**Declining a request returns 204 and tells the requester nothing.** FR-031 requires that a
requester cannot distinguish "declined" from "not yet answered".

**Login returns an identical 401 for unknown email and wrong password.** Otherwise the endpoint
enumerates registered accounts.

**`score` is nullable but `completeness` is not.** Three states that a client must render
differently: no row at all (404 — nothing recorded), `INSUFFICIENT_DATA` (entries exist, no income,
so no proportion can exist), and `COMPLETE` with `score: 0` (income recorded, nothing stashed).
Collapsing any two of these into "0" would misreport a user's month back to them.

**The owner always sees their own score, including when unreconciled.** FR-029 — completeness gates
comparison eligibility only, never the number itself.

**`ComparisonEntry` is the entire surface one user may see of another.** User, rank, score, band,
change, streak. No amounts, no income, no categories, no gap. The schema is written as a closed
list deliberately: adding a field here is the single most likely way this product leaks financial
data, so it should be conspicuous in review.

**`unranked` is a separate array, not zero-scored rows.** FR-038 requires a friend with no score to
be shown as having none rather than ranked last, and a separate array makes it impossible for a
client to accidentally sort them in.

**`streakLookbackMonths` is in the response as a constant 24.** The constitution forbids silent
caps; a client that receives a streak of 24 needs to know it may extend further rather than
presenting it as final.

**`Idempotency-Key` is required, not optional, on entry creation.** FR-010 is a guarantee, and it
cannot be one if the client may omit the key.

## Not yet in the contract

- Webhooks or push notifications — no requirement in this feature.
- Bulk entry import — out of scope per the spec.
- Any recommendations endpoint — deferred out of scope.
