# Generic Coding Guidelines

These apply to both the backend (Java / Spring Boot) and the client app (Flutter / Dart). Language-specific low-level design lives in `clean_arch_backend_developer_context.md` and `clean_arch_flutter_developer_context.md`; the API contract's error format lives in `api_contract.md`.

## Architecture & abstractions
- Always use an interface definition between any two layers in the low-level design.
- For all the operational tools like analytics, logging as well, expose the functionality through an interface so that the underlying solution provider can be updated at will.
- All external third-party providers (identity / auth, push notifications, email, SMS, object storage) must be accessed through an interface — a domain port with a provider-specific adapter — so the provider can be swapped without touching business logic or public APIs. For example, Firebase Auth is used only behind an identity-provider interface; no API, service, interface, or data model exposes Firebase-specific types.
- This provider-behind-an-interface rule applies to the client app as well, not just the backend. The Flutter app must access Firebase Auth (phone OTP, Google SSO, token retrieval / refresh) only through its own auth-provider interface, with the Firebase SDK confined to a single adapter. No BLoC, use case, repository, or widget may reference Firebase types directly. This keeps the client — the part a backend port cannot shield — from becoming the hard-to-migrate seam if the identity provider is ever changed.
- No vendor/framework type crosses a layer boundary upward. Adapters translate infrastructure types into our own domain types; the domain never imports a driver, SDK, or framework type.

## Naming conventions
- Names are intention-revealing. Prefer clarity over brevity; avoid non-obvious abbreviations.
- Interfaces (ports) are named for the capability, not the implementation, and take **no** `I`/`Impl` decoration — e.g. `IdentityTokenVerifier`, `LocationRepository`. Adapters are named for the capability **plus** their technology — e.g. `FirebaseIdentityTokenVerifier`, `PostgresLocationRepository`, `RedisMembershipCache`.
- **Backend (Java):** `PascalCase` for types, `camelCase` for methods/fields, `UPPER_SNAKE_CASE` for constants, all-lowercase package names. Request/response DTOs are suffixed `Request` / `Response`; JPA entities suffixed `Entity` (kept out of the domain); MapStruct mappers suffixed `Mapper`; use cases named for the action they perform (e.g. `InviteMemberUseCase`).
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
- **Unit tests** cover pure logic in isolation — use cases, domain rules, mappers, validators — with ports replaced by mocks (`adapter/out/mock` implementations or Mockito). No Spring context, no Docker, no network. These are the bulk of the tests and must stay fast.
- **Integration tests** cover code that touches real infrastructure or serialization boundaries, run against real engines via **Testcontainers**: persistence/spatial adapters against PostgreSQL+PostGIS, cache/pub-sub against Redis, event producers/consumers against Kafka, object-storage adapters against MinIO; the web layer via `@SpringBootTest` / MockMvc.
- Rule of thumb for placement: **business logic → unit test; anything exercising SQL (especially PostGIS), a driver, serialization, HTTP wiring, or a message broker → integration test.** Do not fake these with in-memory substitutes (e.g. H2 has no PostGIS) — the real behavior is the thing under test.
- **Client:** unit-test BLoCs and use cases with mocked repositories; widget-test screens for state-to-UI rendering.

## Reviewability
- When creating the implementation plan, create the implementation plan in a way that the code generated is reviewable.
- Keep changes small and single-purpose so each unit of work can be reviewed on its own.
