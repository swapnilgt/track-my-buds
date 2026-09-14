# Generic Coding Guidelines

These apply to both the backend (Kotlin / Spring Boot) and the client app (Flutter / Dart). Language-specific low-level design lives in `clean_arch_backend_developer_context.md` and `clean_arch_flutter_developer_context.md`; the API contract's error format lives in `api_contract.md`.

## Architecture & abstractions
- Always use an interface definition between any two layers in the low-level design.
- For all the operational tools like analytics, logging as well, expose the functionality through an interface so that the underlying solution provider can be updated at will.
- All external third-party providers (identity / auth, push notifications, email, SMS, object storage) must be accessed through an interface — a domain port with a provider-specific adapter — so the provider can be swapped without touching business logic or public APIs. For example, Firebase Auth is used only behind an identity-provider interface; no API, service, interface, or data model exposes Firebase-specific types.
- This provider-behind-an-interface rule applies to the client app as well, not just the backend. The Flutter app must access Firebase Auth (phone OTP, Google SSO, token retrieval / refresh) only through its own auth-provider interface, with the Firebase SDK confined to a single adapter. No BLoC, use case, repository, or widget may reference Firebase types directly. This keeps the client — the part a backend port cannot shield — from becoming the hard-to-migrate seam if the identity provider is ever changed.
- No vendor/framework type crosses a layer boundary upward. Adapters translate infrastructure types into our own domain types; the domain never imports a driver, SDK, or framework type.

## Naming conventions
- Names are intention-revealing. Prefer clarity over brevity; avoid non-obvious abbreviations.
- Interfaces (ports) are named for the capability, not the implementation, and take **no** `I`/`Impl` decoration — e.g. `IdentityTokenVerifier`, `LocationRepository`. Adapters are named for the capability **plus** their technology — e.g. `FirebaseIdentityTokenVerifier`, `PostgresLocationRepository`, `RedisMembershipCache`.
- **Backend (Kotlin):** `PascalCase` for types, `camelCase` for functions/properties, `UPPER_SNAKE_CASE` for constants, all-lowercase package names; one file per public type (`.kt`), file named after the type. Request/response DTOs are suffixed `Request` / `Response` (Kotlin `data class`es); JPA entities suffixed `Entity` (kept out of the domain); boundary mapping is done with **hand-written extension functions** — `fun <SourceType>.to<TargetType>()` (e.g. `HeartbeatEntity.toDomain()`, `Heartbeat.toResponse()`) co-located with the adapter that owns the mapping; use cases named for the action they perform (e.g. `InviteMemberUseCase`).
- **Client (Dart):** `PascalCase` for types, `camelCase` for members, `snake_case` for file names (following the structure in the Flutter context doc, e.g. `home_screen_bloc.dart`).
- Booleans read as predicates (`isActive`, `locationSharingEnabled`); collections are plural.

## Error handling
- The domain layer throws **domain-specific** exceptions (e.g. `MemberNotFoundException`, `LastOwnerException`) — never framework or vendor exceptions.
- Adapters translate infrastructure/vendor failures into domain exceptions **at the boundary**. A `SQLException`, a Firebase SDK exception, or an S3 error must not propagate upward as-is.
- The inbound web adapter maps domain exceptions to **RFC 7807 Problem Details** responses via a single centralized handler (`@RestControllerAdvice` on the backend), consistent with `api_contract.md`. HTTP status is decided there, not in use cases.
- Validate inputs at the edge and fail fast — Bean Validation on request DTOs (backend); validate before hitting a use case. Do not let invalid data reach the domain.
- Never swallow an exception silently. If it is caught and handled, log it with enough context (ids, operation) at the point of handling; if it cannot be handled locally, let it propagate to the boundary handler.
- **Client:** repositories return typed success/failure results; BLoCs translate those into explicit UI states. Raw exceptions never reach widgets.

## Testing — unit vs integration
- **Always include test cases with the new code written.** Every feature PR ships with its tests.
- **Unit tests** cover pure logic in isolation — use cases, domain rules, mappers, validators — with ports replaced by mocks (`adapter/out/mock` implementations or MockK). No Spring context, no Docker, no network. These are the bulk of the tests and must stay fast.
- **Integration tests** cover code that touches real infrastructure or serialization boundaries, run against real engines via **Testcontainers**: persistence/spatial adapters against PostgreSQL+PostGIS, cache/pub-sub against Redis, event producers/consumers against Kafka, object-storage adapters against MinIO; the web layer via `@SpringBootTest` / MockMvc.
- Rule of thumb for placement: **business logic → unit test; anything exercising SQL (especially PostGIS), a driver, serialization, HTTP wiring, or a message broker → integration test.** Do not fake these with in-memory substitutes (e.g. H2 has no PostGIS) — the real behavior is the thing under test.
- **Client:** unit-test BLoCs and use cases with mocked repositories; widget-test screens for state-to-UI rendering.

## Pull requests & reviewability
- When creating an implementation plan, structure the work so that the code it produces is reviewable.
- **Single purpose.** One reason to change per PR. Keep each unit of work small enough to be reviewed on its own; a change that touches, say, a persistence adapter *and* an unrelated endpoint *and* a UI screen is several PRs, not one.
- **Size cap.** Every PR keeps its **changed lines of code (added + removed) ≤ 500**, excluding generated code and lockfiles (build-tool lockfiles, code-generator output, dependency/version-catalog regen). Count production and test code together — tests ship in the same PR as the code they cover. If a unit of work would exceed the cap, split it further.

### Stacked pull requests
When a change is an unavoidable dependency chain that cannot fit in a single ≤500-line PR (e.g. shared types / schema → repository → use case → I/O adapter → UI), split it into a **stack of dependent PRs** rather than one large PR — or several PRs that don't build on their own ([GitHub: About stacked pull requests](https://docs.github.com/en/pull-requests/get-started/about-stacked-prs)):

- **Base-branch chaining.** The bottom PR targets the trunk (`main`). Each higher PR targets the branch of the PR directly below it as its base, so each PR's diff shows *only its own layer*, not the layers beneath it.
- **Dependencies point downward.** Foundational changes (shared types, schema/migrations, domain ports) go in lower branches; code that depends on them goes in higher branches. If layer A depends on layer B, B must be in the same branch or a lower one.
- **Merge bottom-up.** Merge the lowest PR first; the host retargets the PRs above it to the trunk and cascades the rebase. Never merge a higher PR before the one it sits on.
- **Every layer stands on its own.** Each PR in the stack builds and passes its own tests green — a stack member still meets the full definition of done, just at smaller scope.
- **Keep stacks short.** Prefer 2–4 PRs per stack. If a stack grows beyond that, the change is too big for one unit of work — reconsider the split. Start a fresh branch (not a taller stack) when switching concerns.
