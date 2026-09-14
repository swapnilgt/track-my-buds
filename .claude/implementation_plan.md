# Implementation Plan

A phased, milestone-based plan for building Track My Buds. It sequences the work as **vertical slices** — each milestone delivers one feature working end-to-end (client → Gateway → services → data stores), with its tests, and is independently demoable and reviewable.

Read alongside: `high_level_architecture.md`, `domain_model.md`, `api_contract.md`, `tech_stack.md`, `clean_arch_backend_developer_context.md`, `clean_arch_flutter_developer_context.md`, `generic_coding_guidelines.md`, `environments.md`. The readiness gaps that used to block implementation are all closed (`implementation_readiness_checklist.md`).

---

## Guiding principles

- **Vertical slices.** Build feature-by-feature end-to-end, not service-by-service. Every milestone produces something you can run from the app.
- **Reviewable increments.** Each milestone is split into small, single-purpose PRs (listed per milestone). Tests ship with the code, never after. Every PR stays **≤ 500 lines of changed code** (see *PR sizing & stacking* below) — split further when a listed PR would exceed that.
- **Self-contained services.** Each backend service is an independent build with its own database, migrations, Dockerfile, and config, so any service (or the client app) can be lifted into its own repository later. **No shared library / shared kernel** between services — cross-cutting boilerplate (Problem Details handler, `X-User-Id` filter, event envelope) is scaffolded per service from a template rather than shared as a module. Services integrate only over REST and Kafka.
- **Provider abstractions.** External providers (identity, push, email, SMS, object storage) sit behind domain ports with vendor code confined to a single adapter — on the backend *and* the client (see `generic_coding_guidelines.md`).
- **Tests every slice.** Unit tests for logic (mocked ports, no Spring context); integration tests for anything touching real infrastructure, run against **Testcontainers** (Postgres+PostGIS, Redis, Kafka, MinIO).

---

## PR sizing & stacking

The generic PR discipline — the **≤ 500 changed-line cap**, single-purpose PRs, and the **stacked-PR** mechanics (base-branch chaining, dependencies downward, bottom-up merge) — lives in `generic_coding_guidelines.md` and applies here. This section only records how it maps onto the milestones below.

- The per-milestone **PRs** listed are the *intended units of review*, each a target of **≤ 500 lines**. If a listed PR would exceed the cap, split it further (e.g. entity + migration in one PR, use cases in the next, web adapter in the next).
- Where a milestone's PRs form a natural dependency chain (marked *stackable* in that milestone), implement them as a bottom-up **stack** rather than one large PR. A typical layering order for a backend slice: (1) entity + Flyway migration + persistence adapter → (2) domain ports + use cases (unit-tested) → (3) web/messaging adapter + Problem Details wiring → (4) Gateway route/filter → (5) client datasource/repository → (6) client BLoC + screen. Each stacked PR still ships its own tests green (see *Definition of done*).

---

## Confirmed foundational stack

| Concern | Choice |
|---------|--------|
| Language / runtime | Kotlin (on JDK 21 LTS), Spring Boot 3.x |
| Build tool | Gradle (Kotlin DSL) |
| API Gateway | Spring Cloud Gateway |
| Migrations | Flyway (per service) |
| Data isolation | Database-per-service (PostgreSQL + PostGIS) |
| Caching / fanout | Redis (cache + Pub/Sub) |
| Eventing | Kafka (`group.events`) |
| Object store | MinIO (S3-compatible) |
| Local orchestration | Docker Compose |
| Integration tests | Testcontainers |
| Observability | Actuator + Micrometer + Prometheus + Grafana + Zipkin |
| Client | Flutter, BLoC, GetIt |
| Identity | Firebase (behind provider-agnostic interfaces, both ends) |

---

## Repository & module layout

```
backend/
  docker-compose.yml          # local infra: postgres+postgis, redis, kafka, minio, prometheus, grafana, zipkin
  gradle/ libs.versions.toml  # shared version catalog (copied on extraction)
  README.md                   # how to run locally
  gateway/                    # Spring Cloud Gateway — standalone Gradle build
  auth-service/
  user-service/
  group-service/
  location-service/
  websocket-service/
  notification-service/
    build.gradle.kts settings.gradle.kts gradlew Dockerfile
    src/main/kotlin/...       # clean-arch packages (see backend context doc)
    src/main/resources/
      application.yml
      db/migration/           # Flyway V__*.sql
    src/test/java/.../unit
    src/test/java/.../integration

client-app/track-my-buds/     # Flutter app (see flutter context doc)
```

Each backend service uses the clean-arch package structure from `clean_arch_backend_developer_context.md`: `domain/{entity,repository,gateway,usecase}`, `adapter/in/{web,messaging}`, `adapter/out/{persistence,cache,identity,messaging,web,mock}`, `config`.

---

## Milestones at a glance

| # | Milestone | Delivers (demoable) | Services touched | Depends on |
|---|-----------|---------------------|------------------|------------|
| M0 | Foundation | Infra up; skeletons build & test; Gateway routes a ping; app runs to a placeholder | scaffolding, Gateway, one reference service | — |
| M1 | Auth + onboarding | Sign in (Firebase), session minted, onboard (username+name), activation gate, profile CRUD | Gateway, Auth, User | M0 |
| M2 | Groups + membership | Create/edit groups, invite/accept/leave/remove, promote/demote, group avatar, `group.events` published | Group (+Gateway) | M1 |
| M3 | Location (REST) | Post location; view buddies' last-known on a map; group centerpoint | Location (+Gateway) | M1, M2 |
| M4 | Live location | Real-time location over WebSocket; membership-cache-driven fanout | Location, WebSocket | M2, M3 |
| M5 | Notifications | Invite/promote/demote trigger FCM/email/SMS | Notification (+User) | M2 |
| M6 | Hardening | Rate limiting, service-auth hardening, dashboards, e2e, CI | all / cross-cutting | M1–M5 |

Dependency logic: M3 needs Group's internal member-resolution endpoint (M2) and User's sharing-flag (M1); M4 needs `group.events` (M2) and the REST location path (M3); M5 needs `group.events` (M2) and User contact lookup (M1).

---

## M0 — Foundation

**Objective:** stand up the repo, local infrastructure, and one reference service proving the full clean-arch + Flyway + Testcontainers + observability path, plus a Gateway skeleton and the Flutter scaffold.

**Backend**
- Monorepo layout, Gradle version catalog, root `README`, `.editorconfig`.
- `docker-compose.yml`: Postgres+PostGIS (with multiple databases provisioned — one per service), Redis, Kafka, MinIO, Prometheus (+ scrape config), Grafana (+ datasource), Zipkin.
- **Reference service = User Service skeleton** (M1 builds on it): clean-arch packages, Flyway baseline migration, Actuator + Micrometer + tracing, SLF4J/Logback with logstash-encoder profiles (plain local / JSON preprod-prod), a trivial ping endpoint, and a Testcontainers harness (a `@SpringBootTest` that boots Postgres, runs Flyway, asserts the context loads).
- Gateway skeleton: Spring Cloud Gateway with a route to the reference service's ping, Actuator, no auth filter yet (passthrough).

**Client**
- Flutter scaffold per the flutter context doc: folder structure, GetIt DI + `injection.dart`, build_runner, a placeholder home screen, and a stubbed `AuthProvider` interface (no Firebase yet).

**Tests**
- Reference service: one unit test (trivial use case) + one Testcontainers integration test.
- Gateway: a routing test.

**Exit criteria:** `docker compose up` brings the stack up; every scaffolded build passes its tests (including a Testcontainers test); Gateway proxies a ping to the reference service; the app launches to the placeholder screen.

**PRs** (each ≤500 lines): (1) monorepo + Gradle + version catalog + README · (2) docker-compose infra + observability configs · (3) reference-service skeleton (clean-arch + Flyway + Testcontainers + logging/metrics) · (4) Gateway skeleton + route · (5) Flutter scaffold + DI. These are independent scaffolding PRs (no stack needed).

---

## M1 — Auth + onboarding  (Gateway → Auth → User)

**Objective:** a new user authenticates with Firebase, gets a session (internal `userId` minted), completes onboarding (username + name → account activated), and can read/update their profile. Gateway enforces identity verification and the activation gate.

**Backend — Auth Service**
- Identity ports in `domain/gateway`: `IdentityTokenVerifier`, `IdentityProvisioner`, `SessionRevoker`. Firebase adapter in `adapter/out/identity` (only place Firebase SDK appears).
- `AUTH_CREDENTIAL` entity + Flyway + persistence adapter (generic `provider` + `providerUid`).
- `POST /auth/session` — verify token, first-login provisioning (mint `userId` + credential), idempotent, returns `{userId, activated}`.
- `DELETE /auth/sessions` — server-initiated logout via `SessionRevoker`.

**Backend — User Service**
- `USER` entity + Flyway.
- `POST /users/me` (onboarding: username + name, activates), `GET /users/me`, `PATCH /users/me` (username immutable), `PUT` location-sharing, `PUT` phone (OTP re-verify coordination), `GET /users?ids` batch.
- Avatar: object-storage gateway port + MinIO adapter; pre-signed PUT to `user-avatars` (key `{userId}`), store object key on `avatarUrl`.
- Internal endpoints (not routed via Gateway): `GET /internal/users/{userId}/location-sharing`, `GET /internal/users/{userId}/contact` (used in M3 / M5).

**Backend — Gateway**
- Identity-verification filter (own `IdentityTokenVerifier` adapter), resolve `userId` from `AUTH_CREDENTIAL` mapping cached in Redis, strip client `X-User-Id`, inject trusted one.
- Activation-gate filter (cached activation flag; blocks all but onboarding routes until a profile exists).
- Routes for `/auth/**`, `/users/**`.

**Client**
- `AuthProvider` interface + Firebase adapter (phone OTP, Google SSO, token retrieval/refresh) confined to one adapter.
- Onboarding flow: sign-in (phone/Google) → `POST /auth/session` → set username+name (`POST /users/me`) → home placeholder. Profile view/edit screens + BLoCs.

**Tests**
- Unit: provisioning idempotency, username-immutable rule, activation logic, Gateway filters (mocked verifier).
- Integration (Testcontainers): Auth + User persistence (Postgres), avatar pre-signed URL (MinIO), Gateway routing + filters (stubbed verifier), Firebase verify via the Firebase Auth emulator or a fake verifier.

**Exit criteria:** from the app — sign in, onboard, activation gate flips open, profile GET/PATCH work, server logout revokes the session.

**PRs** (each ≤500 lines): (1) Auth session + identity ports/adapter + `AUTH_CREDENTIAL` · (2) User profile + activation · (3) User avatar (MinIO gateway + bucket) · (4) Gateway identity + activation filters + Redis cache · (5) client `AuthProvider` + Firebase adapter · (6) client onboarding + profile screens · (7) server-initiated logout. *Stackable:* the client chain (5 → 6) stacks on the Auth/User endpoints; if (1) or (2) exceeds the cap, split each into an entity+migration+persistence PR under a use-case+web-adapter PR.

---

## M2 — Groups + membership  (Group Service + Kafka producer)

**Objective:** full group lifecycle — create/edit/delete groups, invite/accept/decline, leave, remove, promote/demote owners, group avatar — with `group.events` published for every membership/ownership change.

**Backend — Group Service**
- `GROUP` + `GROUP_MEMBERSHIP` (polymorphic `memberType`/`memberId`) entities + Flyway.
- Group CRUD; membership use cases (invite → `PENDING`, accept → `ACTIVE`, leave, remove); ownership use cases (promote, demote) enforcing **at-least-one-owner** and **OWNER-only** guards; pending memberships hidden from other members' views.
- Group avatar via object-storage gateway → `group-avatars` (key `{groupId}`).
- Kafka producer for `group.events` — envelope + event types (`MEMBER_INVITED/JOINED/REMOVED/LEFT`, `OWNER_PROMOTED/DEMOTED`, group deletion → per-member `MEMBER_REMOVED`), keyed by `groupId`.
- Internal endpoints (not via Gateway): `GET /internal/users/{userId}/groups` (active), `GET /internal/groups/{groupId}/members` (active).
- Gateway routes for `/groups/**`, `/invites/**`.

**Client**
- Group list / detail / create / edit screens + BLoCs; invite flow; member list (excludes PENDING for others); ownership actions; group avatar upload.

**Tests**
- Unit: at-least-one-owner, OWNER-only guard, invite/accept transitions, event mapping, producer serialization.
- Integration: Group persistence (Postgres), `group.events` producer (Kafka container — assert envelope + key), internal endpoints, group avatar (MinIO).

**Exit criteria:** full group lifecycle from the app; events land on Kafka (verified by a test consumer).

**PRs** (each ≤500 lines): (1) group + membership entities + CRUD + rules · (2) invite/accept/leave/remove · (3) promote/demote + at-least-one-owner · (4) Kafka producer + envelope + internal endpoints · (5) group avatar · (6–7) client group screens. *Stackable:* (1) is the base; (2)(3)(4) each depend on it — stack them on (1) and merge bottom-up so each diff is just its own membership/ownership/eventing layer. Split (1) if entities+CRUD together exceed the cap.

---

## M3 — Location updates + group reads  (REST)

**Objective:** devices post location; the latest is stored (Postgres+PostGIS + Redis cache); clients read buddies' last-known locations for a group; group centerpoint via PostGIS. No real-time yet.

**Backend — Location Service**
- `USER_LOCATION` entity + PostGIS Flyway (geometry column SRID 4326, spatial index, `geom` derived at write).
- `POST /api/v1/locations` → `202`; rejects when sharing disabled (reads User's sharing flag via internal endpoint, cached in Redis).
- Redis location cache (`user:{userId}:location`, last-known, ~24h reclamation TTL) with Postgres fallback on read.
- `GET /locations?groupId` → members' last-known (resolve active members via Group internal endpoint); PostGIS group centerpoint (`ST_Centroid` + `ST_Collect`).
- Gateway routes for `/locations/**`.

**Client**
- Location sender (foreground; cadence is a client-side detail); map/list view of members' last-known; centerpoint display.

**Tests**
- Unit: geom-derivation, sharing-disabled rejection, cache read/write/fallback.
- Integration: PostGIS spatial + centerpoint (Testcontainers postgis), Redis cache, read endpoint.

**Exit criteria:** post location from the app and see buddies' last-known on a map; disabling sharing blocks updates.

**PRs** (each ≤500 lines): (1) `USER_LOCATION` + PostGIS migration + persistence · (2) `POST /locations` + sharing check + Redis cache · (3) `GET /locations?groupId` + member resolution · (4) PostGIS centerpoint · (5–6) client map + sender. *Stackable:* (2)(3)(4) depend on (1) — stack on it; the client (5 → 6) stacks on the read/write endpoints.

---

## M4 — Live location  (WebSocket + Redis Pub/Sub + membership cache)

**Objective:** live location pushed to subscribed clients over WebSocket; Location Service publishes per-group to Redis Pub/Sub; the membership cache that drives fanout is maintained from `group.events` with read-through.

**Backend — Location Service**
- On each update: resolve the user's groups from the Redis membership cache (`user:{userId}:groups`) and publish to `location:group:{groupId}`.
- Consume `group.events` → maintain the membership cache (`SADD` on `MEMBER_JOINED`, `SREM` on `MEMBER_REMOVED`/`MEMBER_LEFT`, ACTIVE-only); read-through to Group's internal endpoint on cache miss.

**Backend — WebSocket Service**
- Identity-token handshake (verify at connect).
- Subscribe/unsubscribe per group with ref-counted Redis channel subscriptions; verify membership once via Group internal endpoint; catch-up (send current last-known on subscribe); push live updates.
- Gateway WebSocket route + handshake auth.

**Client**
- WebSocket layer: connect, subscribe to group(s), render live movement, unsubscribe on leave.

**Tests**
- Unit: membership-cache apply logic, ref-count subscribe/unsubscribe, fanout-target derivation.
- Integration: Redis Pub/Sub publish→receive, `group.events` consumer updates cache (Kafka + Redis containers), WebSocket handshake + subscribe + catch-up.

**Exit criteria:** two devices see each other move live; a newly-accepted member starts receiving (cache `SADD`); a removed member stops (`SREM`).

**PRs** (each ≤500 lines): (1) Location publish to Pub/Sub + membership-cache consumer + read-through · (2) WebSocket handshake + auth · (3) subscribe/unsubscribe ref-count + membership verify · (4) catch-up + live push · (5) client live layer. *Stackable:* the WebSocket chain (2 → 3 → 4) is a natural stack; (5) stacks on (4).

---

## M5 — Notifications  (Notification Service consumer)

**Objective:** `group.events` drive notifications via FCM/email/SMS; contact info fetched from User Service; all providers behind ports.

**Backend — Notification Service**
- Consume `group.events` (act on `MEMBER_INVITED`, `OWNER_PROMOTED`, `OWNER_DEMOTED`); dedupe on `eventId`.
- Fetch email/phone from User's internal contact endpoint (events carry ids only).
- Push/email/SMS gateway ports; FCM adapter now; email/SMS providers are TBD → mock adapters behind the ports for now.

**Client**
- FCM device-token registration; in-app push handling and display.

**Tests**
- Unit: event-type filtering, dedupe, contact-fetch orchestration, provider-port usage.
- Integration: `group.events` consumer (Kafka container) → triggers (mock) providers; contact internal endpoint.

**Exit criteria:** inviting / promoting / demoting a user triggers a notification (mock or real); dedupe verified on redelivery.

**PRs** (each ≤500 lines): (1) consumer + dedupe + event filtering · (2) User contact internal endpoint · (3) FCM push adapter + port · (4) email/SMS mock adapters behind ports · (5) client FCM registration + handling. *Stackable:* (1) is the base; (3)(4) provider adapters stack on it. (2) is independent (lives in User Service).

---

## M6 — Hardening & cross-cutting

**Objective:** production-leaning concerns after the features work end-to-end.

- **Gateway rate limiting** configured.
- **Service-to-service auth hardening** — revisit the network-trust caveat (#7): choose and apply mTLS or signed service tokens; enforce `/internal/*` is unreachable from public ingress. *(Deferred decision — resolve here.)*
- **Observability**: Grafana dashboards + alert rules, Zipkin sampling, readiness/liveness probes.
- **End-to-end tests** across the compose stack for the main flows.
- **Consistency audits**: RFC 7807 error format across services; input-validation pass.
- **Fill provider TBDs**: email provider, SMS provider, cloud platform (preprod/prod), secrets manager. *(Deferred sign-offs.)*
- **CI**: build + unit + integration (Testcontainers) pipeline.

---

## Conventions applied in every milestone

- Clean-arch layering with an interface at every boundary; no vendor/framework type crosses a boundary upward (`generic_coding_guidelines.md`).
- Domain exceptions → RFC 7807 Problem Details at the web adapter via a central handler.
- Every service ships Flyway migrations, Actuator/metrics/tracing, and profile-based logging from its first PR.
- Unit tests (mocked ports) for logic; Testcontainers integration tests for real-infra code.
- Firebase (and every external provider) only behind its port, one adapter, on both backend and client.

## Definition of done (per PR)

- Code + tests in the same PR; unit and integration tests pass; build green.
- **≤ 500 changed lines** and single-purpose, per `generic_coding_guidelines.md`. Over the cap → split, or turn the slice into a stack (see *PR sizing & stacking*).
- New endpoints match `api_contract.md`; new persistence has a Flyway migration.
- No provider/vendor type leaks past its adapter.
- PR is single-purpose and independently reviewable. If part of a stack: it builds on its own, targets the branch below it, and is merged bottom-up.

---

## Deferred decisions (revisit at the noted milestone)

| Decision | Revisit at |
|----------|-----------|
| Service-to-service auth hardening (mTLS vs signed tokens) | M6 |
| Email provider · SMS provider | M5 (adapters) / M6 (real provider) |
| Cloud platform (preprod/prod), managed Postgres/Redis/Kafka/object-store, secrets manager | M6 / environments work |
| Client location update cadence / batching | M3 (client) |
| WebSocket horizontal-scaling specifics (sticky sessions, instance count) | M4 / M6 |
