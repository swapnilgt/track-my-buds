# Implementation Readiness Checklist

Open gaps and decisions identified before starting implementation. Each item notes why it matters and which document should hold the answer once resolved. Check items off as they are closed and fold the detail into the target document.

Status legend: `[ ]` open · `[~]` in progress · `[x]` closed

---

## Major — close before / early in implementation

### [x] 1. API contract
- **Gap:** No endpoint specification exists — no URLs, request/response shapes, standard error body, pagination format, or API versioning convention.
- **Why it matters:** The Flutter app and the six backend services cannot be built in parallel without an agreed contract. The clean-arch backend doc already assumes DTOs exist without defining them.
- **Target document:** `api_contract.md` — **done.**
- **Decided:** path versioning (`/api/v1`); bare-resource responses with `{ items, page }` for lists; RFC 7807 Problem Details for errors; page/offset pagination (`?page&size`); all endpoints across Auth/User/Group/Location + the live-location WebSocket are specified. Auth model finalised (Model B, see #2) and reflected in §1–§2.

### [x] 2. Authentication & authorization flow
- **Gap:** Architecture said Auth Service "issues JWT" and Gateway "validates JWT," but the mechanics were undefined.
- **Why it matters:** Every secured endpoint depends on this; retrofitting auth is expensive.
- **Target document:** `high_level_architecture.md` (Authentication & Authorization section) + `api_contract.md` (§1–§2) — **done.**
- **Decided (Model B — verify Firebase tokens directly):**
    - The app consumes **Firebase ID tokens directly** — no app-issued JWT and no refresh-token strategy on our side. The Firebase SDK auto-refreshes tokens on the client.
    - Auth Service does not issue tokens. On first login (`POST /auth/session`) it verifies the Firebase token, mints the internal `userId`, and creates the `AUTH_CREDENTIAL` mapping. Profile creation stays in User Service (`POST /users/me`, JIT at onboarding); Auth and User rows share the same `userId`.
    - The Gateway verifies the Firebase token per request, resolves `userId` from the cached `AUTH_CREDENTIAL` mapping, and forwards a trusted `X-User-Id` downstream (client-supplied `X-User-Id` is stripped).
    - Authorization is enforced from our own data, not token claims: activation gate at the Gateway (cached flag); `OWNER`-only actions enforced in Group Service use cases by checking the caller's membership `role`.
    - JWT signing chosen as **RS256** should we ever move to Model A (app-issued tokens); not needed under Model B since no app tokens are issued. Firebase tokens are verified via Firebase's public keys.
    - **Server-initiated logout** supported via the provider: `DELETE /auth/sessions` (Firebase Admin SDK `revokeRefreshTokens` today), enforced at the Gateway by verifying against the provider's revocation time. Ordinary logout is client-side.
    - **Provider-agnostic:** Firebase is accessed only behind identity-provider interfaces (verify token, provision on first login, revoke sessions). `AUTH_CREDENTIAL` stores a generic `provider` + `providerUid`; no API, interface, or data model exposes Firebase types; downstream services see only `X-User-Id`.
- **Caveat:** documented in `api_contract.md` §1 and `high_level_architecture.md` — revisit Model A (own JWT + refresh strategy) if the product scales or needs to decouple from Firebase.

### [x] 3. Membership data propagation to Redis
- **Decided:** Location Service consumes `group.events` from Kafka and maintains its own `user:{userId}:groups` cache — `SADD` on `MEMBER_JOINED`, `SREM` on `MEMBER_REMOVED` / `MEMBER_LEFT`; only `ACTIVE` memberships are cached. On a cache miss it rebuilds from Group Service's internal read-through endpoint `GET /internal/users/{userId}/groups`. Group Service stays the single owner/producer; no direct cross-service cache writes. Documented in `high_level_architecture.md` ("Group Event Propagation" + Redis Location Cache).

### [x] 4. Kafka `group.events` payload schema
- **Decided:** Common envelope (`eventId`, `type`, `occurredAt`, `groupId`, `groupName`, `actorUserId`, `payload`) with event types `MEMBER_INVITED` / `MEMBER_JOINED` / `MEMBER_REMOVED` / `MEMBER_LEFT` / `OWNER_PROMOTED` / `OWNER_DEMOTED` (+ reserved `GROUP_DELETED`). Keyed by `groupId` for per-group ordering; at-least-once with idempotent consumers. **Events carry ids only — no contact details**; Notification Service fetches email/phone from User Service on consume (contact data is owned there). Full schema + per-consumer behaviour table in `high_level_architecture.md` ("Group Event Propagation").

### [x] 5. Location-update transport
- **Decided:** REST `POST /api/v1/locations` to Location Service through the Gateway, returning `202 Accepted`. The WebSocket stays receive-only for live location. Specified in `api_contract.md` §5.
- **Still open:** update cadence / batching on the client side — a client-implementation detail, not a contract blocker.

### [x] 6. API Gateway technology
- **Decided:** **Spring Cloud Gateway** (signed off). Keeps the Gateway in the Java/Spring ecosystem so per-request identity-token verification (Model B) sits alongside the Firebase Admin SDK identity adapter, with built-in filters for routing and rate limiting and no separate runtime to operate. Recorded in `tech_stack.md`.

---

## Medium — resolve during early implementation

### [x] 7. Inter-service communication & service-to-service auth
- **Decided:** synchronous REST over the internal network via dedicated `/internal/...` endpoints (WebSocket → Group membership verify, Location → Group membership read-through, Notification → User contact lookup). `/internal/...` routes are **never mapped in the Gateway**, so they are unreachable from outside. **Auth = network trust for now** — callers pass `userId` explicitly rather than a trusted principal; no per-call service credential yet. Documented in `high_level_architecture.md` → Authentication & Authorization → "Internal (service-to-service) calls", with a caveat to harden with mTLS / signed service tokens before production.

### [x] 8. Database migration tool
- **Decided:** **Flyway** (signed off). Versioned plain-SQL migrations per service — a natural fit for Postgres + PostGIS DDL (extensions, geometry columns, spatial indexes) with first-class Spring Boot auto-integration. Recorded in `tech_stack.md`.

### [x] 9. Object storage bucket / key layout
- **Decided:** **bucket-per-type** — `user-avatars` (key `{userId}`) and `group-avatars` (key `{groupId}`). **Stable key, overwrite-in-place** (one object per owner, no orphan cleanup). Buckets are **private**; upload via pre-signed PUT, read via short-lived pre-signed GET. `avatarUrl` stores the **object key only**, never a pre-signed URL; no file extension (content type in object metadata). Documented in `high_level_architecture.md` → MinIO Object Store ("Bucket & key layout").

---

## Minor — nice to close before coding the relevant part

### [x] 10. Testcontainers for integration tests
- **Decided:** **Testcontainers** (signed off) for integration tests — real PostgreSQL+PostGIS, Redis, Kafka, and MinIO in Docker; no in-memory substitutes for PostGIS/Redis/Kafka behavior. Docker is a test-time dependency. Recorded in `tech_stack.md` and `clean_arch_backend_developer_context.md` (§4 Testing).

### [x] 11. Expand `generic_coding_guidelines.md`
- **Decided:** expanded into sections — Architecture & abstractions, Naming conventions (backend + client, ports vs adapters), Error handling (domain exceptions, boundary translation, RFC 7807 mapping), Testing (unit vs integration placement rules), and Reviewability. In `generic_coding_guidelines.md`.
