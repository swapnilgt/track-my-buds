# User Service Reference Skeleton — Design (M0 · PR3)

**Status:** Approved for planning · **Date:** 2026-09-07
**Milestone:** M0 (Foundation) · **PR:** PR3 (reference-service skeleton)
**Related docs:** `.claude/implementation_plan.md` (M0), `.claude/clean_arch_backend_developer_context.md`, `.claude/tech_stack.md`, `.claude/environments.md`, `.claude/high_level_architecture.md`

---

## 1. Overview

PR3 scaffolds the **User Service** as the *reference backend service* — the template every other Kotlin/Spring Boot service is cloned from. It proves the full path end-to-end: **clean-arch layering → Flyway migration → JPA persistence → Testcontainers integration test → Actuator/Micrometer/tracing observability**, exercised through a single working vertical slice (a DB-backed `GET /ping`).

Because it is a template, correctness and idiom matter more than feature scope. The real `USER` entity and profile endpoints arrive in **M1** and build on this skeleton.

### Goals
- A standalone, buildable, testable Kotlin/Spring Boot service at `backend/user-service/`.
- A complete-but-minimal clean-arch vertical slice wired through every layer.
- Prove Flyway + JPA + Testcontainers + observability work against real infrastructure.
- Establish the conventions (package layout, extension-function mappers, constructor injection, profiles, logging, testing) that later services copy.

### Non-goals (deferred)
- The real `USER` entity and `/users/**` endpoints → **M1**.
- API Gateway and routing → **PR4**.
- Redis / Kafka / identity / MinIO adapters → their respective milestones.
- Any placeholder empty packages (`gateway`, `cache`, `messaging`, `identity`, `config`) — created only when a service first needs them.

---

## 2. Technology & conventions (per `tech_stack.md`)

- **Language:** Kotlin on JDK 21 toolchain; Spring Boot 3.4.1; Spring MVC (not WebFlux/coroutines); virtual threads available.
- **Build:** Gradle Kotlin DSL; standalone build per service (own wrapper + `settings.gradle.kts`), sharing the version catalog at `backend/gradle/libs.versions.toml` via `from(files("../gradle/libs.versions.toml"))`.
- **Kotlin plugins:** Kotlin JVM, `kotlin-spring` (all-open — Spring can proxy final-by-default classes), `kotlin-jpa` (no-arg — JPA entities).
- **Mapping:** hand-written Kotlin extension functions (`fun Source.toTarget()`), co-located with the owning adapter. No mapping library, no annotation processor.
- **Mocking:** MockK (secondary to hand-written mock adapters in `adapter/out/mock`).
- **Migrations:** Flyway (versioned plain SQL). JPA `ddl-auto: validate` — Flyway owns the schema.
- **Base package:** `com.trackmybuds.userservice` · **HTTP port:** 8081 (matches the Prometheus scrape config).

---

## 3. The vertical slice — DB-backed ping

`GET /ping` reads a seeded `heartbeat` row through the full stack and returns liveness + DB-reachability. It doubles as the Gateway's route target in PR4 and a smoke endpoint. (It overlaps with `/actuator/health`'s DB check by design — its purpose here is to be a *cloneable, working* vertical slice.)

### Data flow
```
GET /ping
  → PingController (adapter/in/web)
    → PingUseCase.ping()            (domain/usecase port)
      → PingUseCaseImpl             (@Service)
        → HeartbeatRepository.findMarker()   (domain/repository port)
          → HeartbeatRepositoryImpl (adapter/out/persistence, @Repository)
            → HeartbeatJpaRepository (Spring Data)  → Postgres
        ← Heartbeat? (domain)      via HeartbeatJpaEntity.toDomain()
      ← Pong(service, status=UP if marker present, dbCheckedAt=now)
    ← PingResponse                 via Pong.toResponse()
  ← 200 {service, status, dbCheckedAt}
```

### Response shape
```json
{ "service": "user-service", "status": "UP", "dbCheckedAt": "2026-09-07T10:15:30Z" }
```

### Domain types
- `Heartbeat(id: UUID, createdAt: Instant)` — `data class`, no framework annotations.
- `Pong(service: String, status: String, dbCheckedAt: Instant)` — use-case result `data class`, co-located with the `usecase` port. `status` is `"UP"` when the marker row is present, `"DOWN"` otherwise.
- `PingUseCase` (interface): `fun ping(): Pong`.
- `HeartbeatRepository` (interface): `fun findMarker(): Heartbeat?`.

---

## 4. Package & class layout

Under `src/main/kotlin/com/trackmybuds/userservice/`:

```
domain/
  entity/Heartbeat.kt                 data class
  repository/HeartbeatRepository.kt   port: findMarker(): Heartbeat?
  usecase/PingUseCase.kt              port: ping(): Pong
  usecase/PingUseCaseImpl.kt          @Service, ctor-injects HeartbeatRepository + service name
  usecase/Pong.kt                     use-case result data class
adapter/in/web/
  PingController.kt                   @RestController GET /ping
  dto/PingResponse.kt                 response data class
  mapper/PingWebMappers.kt            fun Pong.toResponse()
  GlobalExceptionHandler.kt           @RestControllerAdvice → RFC 7807 ProblemDetail
adapter/out/persistence/
  HeartbeatJpaEntity.kt              @Entity mutable class (no-arg plugin), @Table("heartbeat")
  HeartbeatJpaRepository.kt          interface : JpaRepository<HeartbeatJpaEntity, UUID>
  HeartbeatRepositoryImpl.kt         @Repository implements HeartbeatRepository
  HeartbeatPersistenceMappers.kt     fun HeartbeatJpaEntity.toDomain()
adapter/out/mock/
  InMemoryHeartbeatRepository.kt     implements HeartbeatRepository for unit tests (no Spring/DB)
UserServiceApplication.kt            @SpringBootApplication + top-level fun main { runApplication }
```

**Injection:** constructor injection only (primary constructor). Service name is injected via `@Value("\${spring.application.name}")` into `PingUseCaseImpl`.

**Empty packages** (`config`, `domain/gateway`, `adapter/out/{cache,web,messaging,identity}`, `adapter/in/messaging`) are **not** created — they appear when a service first needs them. The full tree is documented in `clean_arch_backend_developer_context.md`.

---

## 5. Persistence & migrations

- **Flyway** `src/main/resources/db/migration/V1__baseline.sql`:
  ```sql
  CREATE TABLE heartbeat (
      id         UUID PRIMARY KEY,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now()
  );
  INSERT INTO heartbeat (id) VALUES ('00000000-0000-0000-0000-000000000001');
  ```
  M1 adds `V2__create_user.sql`.
- `spring.jpa.hibernate.ddl-auto: validate` — Flyway is the single source of schema truth; JPA only validates the mapping against it. Never `update`/`create` in any environment.
- `HeartbeatJpaEntity` is a **mutable `class`** (properties as `var`, no-arg via `kotlin-jpa`) — deliberately *not* a `data class`, to avoid JPA identity/equality pitfalls.

### Datasource (local)
- URL `jdbc:postgresql://localhost:5432/user_service`, user `user_service`, password `localdev` — the **per-service role** the compose init script (`init-multiple-databases.sh`) provisions, not the `postgres` superuser.
- Preprod/prod pull datasource host/user/password from environment variables (no `.env`).

---

## 6. Configuration & profiles

Spring profiles map to environments (`local` / `preprod` / `prod`); active profile via `SPRING_PROFILES_ACTIVE`.

- `application.yml` (base): `spring.application.name: user-service`; server port 8081; JPA `ddl-auto: validate`, `open-in-view: false`; Flyway enabled; Actuator exposes only `health, info, prometheus`; management server on the main port; tracing sampling default.
- `application-local.yml`: localhost datasource + per-service role (above); Zipkin endpoint `http://localhost:9411/api/v2/spans`; tracing sampling `1.0`; plain-text logging (via logback profile).
- `application-preprod.yml` / `application-prod.yml`: datasource + Zipkin endpoint from env vars; reduced sampling; JSON logging (via logback profile). Minimal but present, to demonstrate the profile pattern.

---

## 7. Observability & logging

- **Metrics:** `spring-boot-starter-actuator` + `micrometer-registry-prometheus`. `/actuator/prometheus` is scraped by the Prometheus container already targeting `host.docker.internal:8081`. Micrometer auto-collects JVM, HTTP, and HikariCP metrics.
- **Tracing:** `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` → Zipkin. `traceId`/`spanId` auto-injected into MDC.
- **Health:** `/actuator/health` with the auto-configured Postgres indicator.
- **Logging:** `logback-spring.xml` using `<springProfile>`:
  - `local` → plain, human-readable console.
  - `preprod,prod` → structured JSON via `logstash-logback-encoder` (fields: timestamp, level, logger, message, `traceId`, MDC).
  - SLF4J API only; parameterised log statements; never log secrets.

---

## 8. Testing

- **Unit** — `src/test/kotlin/.../unit/PingUseCaseImplTest.kt`: drives `PingUseCaseImpl` with `InMemoryHeartbeatRepository`; asserts `status = "UP"` when a marker is present and `"DOWN"` when absent. No Spring context, no Docker. (MockK available where a hand-written mock would be overkill.)
- **Integration** — `src/test/kotlin/.../integration/PingIntegrationTest.kt`: `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@Testcontainers`, with a `PostgreSQLContainer` running the **`postgis/postgis:17-3.5`** image (parity with the compose stack) via `.asCompatibleSubstituteFor("postgres")`; `@DynamicPropertySource` wires the datasource; Flyway runs `V1`; a real `GET /ping` asserts `200` and `status = "UP"`. This is the proof of the full clean-arch + Flyway + Testcontainers path.

---

## 9. Build & version-catalog changes

`backend/gradle/libs.versions.toml` (created in PR1) is edited in this PR:
- **Add:** `kotlin` version; plugin aliases `kotlin-jvm`, `kotlin-spring`, `kotlin-jpa`; libraries `mockk`, `jackson-module-kotlin` and `kotlin-reflect` (both needed by Spring's Kotlin support — versions managed by the Spring Boot BOM / Kotlin plugin, so declared without an explicit version pin where possible).
- **Remove:** `mapstruct`, `mapstruct-processor` library entries and the `mapstruct` bundle.

`backend/user-service/build.gradle.kts`:
- Plugins: `kotlin("jvm")`, `kotlin("plugin.spring")`, `kotlin("plugin.jpa")`, `spring-boot`, `spring-dependency-management` (all via catalog).
- `kotlin { jvmToolchain(21) }`.
- Dependencies: `spring-boot-starter-web`, `-data-jpa`, `-validation`, `-actuator`; the `observability` bundle; `flyway-core` + `flyway-database-postgresql`; `postgresql` (runtimeOnly); `jackson-module-kotlin` (Kotlin JSON) and `kotlin-reflect` as required by Spring/Kotlin.
- `testImplementation`: `spring-boot-starter-test`, `mockk`, Testcontainers BOM (platform) + `junit-jupiter` + `postgresql`.

`backend/user-service/settings.gradle.kts`: `rootProject.name = "user-service"`; version-catalog reference to `../gradle/libs.versions.toml`.

`backend/user-service/Dockerfile`: multi-stage — build with a JDK 21 image (`./gradlew bootJar`), run on a JRE 21 image. (At M0 the service runs via `./gradlew bootRun`; the Dockerfile is for later compose integration.)

---

## 10. Acceptance criteria

1. `./gradlew build` (in `backend/user-service/`) compiles and passes all tests, including the Testcontainers integration test (Docker required).
2. With the compose stack up, `./gradlew bootRun` starts the service on port 8081; Flyway applies `V1`; `GET http://localhost:8081/ping` returns `200` with `{service, status: "UP", dbCheckedAt}`.
3. `GET /actuator/health` returns `UP` (incl. the DB indicator); `/actuator/prometheus` exposes metrics.
4. The service is a standalone Gradle build (own wrapper) that references the shared catalog by relative path — movable to its own repo by copying the catalog and updating one path.
5. Unit test and integration test both present and green.

---

## 11. PR scope & size

Single PR, target ≤500 changed lines (excluding generated code — Gradle wrapper jar/scripts). Contents: the `user-service` module (build files, wrapper, Dockerfile), the ping vertical slice, resources (yml + logback + Flyway V1), the two tests, and the `libs.versions.toml` edit. If it exceeds the cap, split as: (a) module + build + catalog + app entry + config/observability + Flyway + integration test proving context loads; (b) the ping vertical slice + unit test. Note the `.claude/*.md` tech-stack doc updates (Kotlin decision) ride along in this PR's commit.
