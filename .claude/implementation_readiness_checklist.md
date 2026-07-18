# Implementation Readiness Checklist

Open gaps and decisions identified before starting implementation. Each item notes why it matters and which document should hold the answer once resolved. Check items off as they are closed and fold the detail into the target document.

Status legend: `[ ]` open · `[~]` in progress · `[x]` closed

---

## Major — close before / early in implementation

### [~] 1. API contract
- **Gap:** No endpoint specification exists — no URLs, request/response shapes, standard error body, pagination format, or API versioning convention.
- **Why it matters:** The Flutter app and the six backend services cannot be built in parallel without an agreed contract. The clean-arch backend doc already assumes DTOs exist without defining them.
- **Target document:** `api_contract.md` — **drafted.**
- **Decided:** path versioning (`/api/v1`); bare-resource responses with `{ items, page }` for lists; RFC 7807 Problem Details for errors; page/offset pagination (`?page&size`); all endpoints across Auth/User/Group/Location + the live-location WebSocket are specified.
- **Still open (depends on #2):** token-exchange details, JWT claims, and the trusted `X-User-Id` propagation are provisional until the auth flow is finalised. Finalise these, then flip to `[x]`.

### [ ] 2. Authentication & authorization flow
- **Gap:** Architecture says Auth Service "issues JWT" and Gateway "validates JWT," but the mechanics are undefined.
- **Why it matters:** Every secured endpoint depends on this; retrofitting auth is expensive.
- **Target document:** `high_level_architecture.md` (flow) + `api_contract.md` (headers/claims).
- **To decide:**
    - How a Firebase token is exchanged for the app's own JWT.
    - JWT claims (userId, activation state?), token expiry, refresh-token strategy.
    - How `userId` is propagated from Gateway to downstream services (trusted header?).
    - Authorization model for owner-only actions — how Group Service verifies the caller holds the `OWNER` role before invite / promote / demote / remove.

### [ ] 3. Membership data propagation to Redis
- **Gap:** Location Service reads each user's group-membership list from Redis to decide which Pub/Sub channels to publish to, but that data is owned by Group Service. No mechanism defines how membership changes reach that Redis cache.
- **Why it matters:** Without it, location fanout targets stale groups — new members miss updates, removed members keep receiving them.
- **Target document:** `high_level_architecture.md`.
- **To decide:** Who writes the membership cache — e.g. Group Service writes it directly, or publishes a membership-change event that Location Service consumes and applies.

### [ ] 4. Kafka `group.events` payload schema
- **Gap:** The topic exists but the event structure is undefined — event types, fields, and whether the event carries the target user's email + phone.
- **Why it matters:** Notification Service needs contact info to send email/SMS; that data is owned by User Service. The payload decision determines whether Notification Service is self-sufficient or must call User Service.
- **Target document:** `high_level_architecture.md` (Kafka section) + a schema definition.
- **To decide:** Event type enum (invite / accept / promote / demote / remove), full field list, and whether contact details are embedded in the event or fetched on consume.

### [x] 5. Location-update transport
- **Decided:** REST `POST /api/v1/locations` to Location Service through the Gateway, returning `202 Accepted`. The WebSocket stays receive-only for live location. Specified in `api_contract.md` §5.
- **Still open:** update cadence / batching on the client side — a client-implementation detail, not a contract blocker.

### [ ] 6. API Gateway technology
- **Gap:** The Gateway is a core component but absent from `tech_stack.md`.
- **Why it matters:** It owns JWT validation, rate limiting, and routing — needs choosing before those responsibilities can be built.
- **Target document:** `tech_stack.md`.
- **To decide:** Spring Cloud Gateway vs Kong vs Nginx vs other. **Requires user sign-off per project tech-stack rule.**

---

## Medium — resolve during early implementation

### [ ] 7. Inter-service communication & service-to-service auth
- **Gap:** WebSocket Service already calls Group Service to verify membership, but the mechanism (REST? internal endpoint?) and how that call is authenticated are undefined.
- **Target document:** `high_level_architecture.md`.
- **To decide:** Sync REST vs other; service-to-service auth (internal network trust, mTLS, or service tokens).

### [ ] 8. Database migration tool
- **Gap:** No schema-migration strategy chosen.
- **Why it matters:** Spring Boot + PostgreSQL services need this from the first schema.
- **Target document:** `tech_stack.md` + `clean_arch_backend_developer_context.md`.
- **To decide:** Flyway vs Liquibase. **Requires user sign-off per project tech-stack rule.**

### [ ] 9. Object storage bucket / key layout
- **Gap:** MinIO is chosen but the bucket structure and object-key naming for avatars are undefined.
- **Target document:** `high_level_architecture.md` (Object Store section).
- **To decide:** Bucket-per-type vs shared bucket, key naming (e.g. `avatars/user/{userId}`), public vs pre-signed access.

---

## Minor — nice to close before coding the relevant part

### [ ] 10. Testcontainers for integration tests
- **Gap:** Integration testing uses `@SpringBootTest` / `@DataJpaTest` but Testcontainers (real PostgreSQL/Redis in Docker for tests) is not mentioned.
- **Target document:** `clean_arch_backend_developer_context.md` + `tech_stack.md`.

### [ ] 11. Expand `generic_coding_guidelines.md`
- **Gap:** Currently two lines. Missing naming conventions, error-handling rules, and what is unit- vs integration-tested.
- **Target document:** `generic_coding_guidelines.md`.
