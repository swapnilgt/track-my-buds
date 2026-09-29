# API Gateway Skeleton — Design (M0 · PR4)

**Status:** Approved for planning · **Date:** 2026-09-23
**Milestone:** M0 (Foundation) · **PR:** PR4 (Gateway skeleton + route)
**Related docs:** `.claude/implementation_plan.md` (M0), `.claude/clean_arch_backend_developer_context.md`, `.claude/tech_stack.md`, `.claude/spring_best_practices.md`, `.claude/high_level_architecture.md`
**Precedent:** clones the standalone-build / observability / logging / CI patterns established by the User Service skeleton (PR3, `docs/superpowers/specs/2026-09-07-user-service-skeleton-design.md`).

---

## 1. Overview

PR4 scaffolds the **API Gateway** as a standalone Kotlin/Spring Boot service that is the single entry point to the backend. At M0 it is a **passthrough**: one route forwarding `GET /ping` to the User Service, plus Actuator/observability. No auth, activation, or rate-limiting filters yet (those are M1/M6).

### Goals
- A standalone, buildable, testable Gateway service at `backend/gateway/` on port 8080.
- One working route (`/ping` → User Service) proving Gateway Server MVC routing end-to-end.
- Actuator + Micrometer/Prometheus + Brave/Zipkin, logback plain/JSON profiles — consistent with the User Service.
- A routing integration test (WireMock stub, no Docker).
- Added to the backend CI matrix.

### Non-goals (deferred)
- Identity-verification filter, activation-gate filter, Redis `userId`/activation cache, `/auth/**` and `/users/**` routes → **M1**.
- Rate limiting → **M6**.
- Any domain/business logic — the Gateway has none.

---

## 2. Technology & conventions

- **Flavor:** **Spring Cloud Gateway Server MVC** (servlet-based, Tomcat + virtual threads) — consistent with `tech_stack.md`'s "Spring MVC (not WebFlux)". Chosen over the reactive gateway so M1's blocking work (Firebase token verification + Redis lookups) is natural, with no reactive offloading.
- Kotlin on JDK 21 toolchain; Spring Boot 3.4.1; Spring Cloud 2024.0.0.
- Standalone Gradle build (own wrapper, shared catalog by relative path); base package `com.trackmybuds.gateway`; **port 8080** (matches `backend/docker/prometheus/prometheus.yml`).
- Follows `spring_best_practices.md` where applicable; the Gateway is stateless (no JPA/Flyway/DB).

---

## 3. Structure — a thin routing app (not the clean-arch domain layering)

The Gateway is infrastructure, not a business service — it has no entities, use cases, or persistence — so it **deliberately does not** adopt the `domain/adapter` package structure from `clean_arch_backend_developer_context.md`. Minimal layout under `src/main/kotlin/com/trackmybuds/gateway/`:

```
GatewayApplication.kt          @SpringBootApplication + top-level main { runApplication }
config/RoutesConfig.kt         route definitions (RouterFunction bean)
```
Resources: `application.yml` + `application-local/preprod/prod.yml` + `logback-spring.xml`. Plus `Dockerfile`, build files, wrapper.

A one-line note is added to `clean_arch_backend_developer_context.md`: infrastructure-only services (the Gateway) are exempt from the domain-layer structure.

---

## 4. Route

One passthrough route, defined with Gateway Server MVC's **functional `RouterFunction` DSL** (idiomatic and version-stable, avoiding property-namespace drift):

- `GET /ping` → forwards to the User Service.
- Target URI from config key `gateway.user-service-uri`, default `http://localhost:8081` (the User Service's host-run `bootRun` port), overridable per profile / env var.
- No path rewriting, no filters — pure passthrough.

Shape (exact DSL imports pinned at implementation against Spring Cloud 2024.0.0 `gateway-server-webmvc`):
```kotlin
@Configuration
class RoutesConfig {
    @Bean
    fun pingRoute(
        @Value("\${gateway.user-service-uri}") userServiceUri: String,
    ): RouterFunction<ServerResponse> =
        route("ping")
            .GET("/ping", http(URI.create(userServiceUri)))
            .build()
}
```

---

## 5. Observability & logging

Identical pattern to the User Service:
- Actuator exposes `health,info,prometheus` on port 8080 (Prometheus already scrapes `host.docker.internal:8080`).
- `micrometer-tracing-bridge-brave` + `zipkin-reporter-brave` → Zipkin; `b3` trace context propagates across the hop to the User Service so a request is one correlated trace.
- `logback-spring.xml` with `<springProfile>`: plain console for `local`, logstash JSON for `preprod,prod`, plain console when no profile (tests).
- Profiles: `application.yml` (base: app name, port 8080, actuator, `gateway.user-service-uri` default); `application-local.yml` (user-service-uri `http://localhost:8081`, Zipkin `http://localhost:9411/api/v2/spans`, sampling `1.0`); `application-preprod.yml` / `application-prod.yml` (user-service-uri + Zipkin from env vars; sampling `0.1` / `0.05`).

---

## 6. Testing — routing integration test (no Docker)

`src/test/kotlin/.../GatewayRoutingTest.kt`:
- `@SpringBootTest(webEnvironment = RANDOM_PORT)`.
- Starts a **WireMock** server; `@DynamicPropertySource` sets `gateway.user-service-uri` to the WireMock base URL and `management.tracing.sampling.probability=0.0`.
- WireMock stubs `GET /ping` → `200` with a JSON body.
- `TestRestTemplate` calls `GET /ping` on the gateway; asserts status `200`, the stub's body is returned, and WireMock recorded the forwarded request.
- In-JVM only — no Testcontainers, no real User Service, no Docker.

---

## 7. Build & version-catalog changes

`backend/gradle/libs.versions.toml`:
- **Swap:** remove the reactive `spring-cloud-starter-gateway` library; add `spring-cloud-starter-gateway-server-webmvc = { module = "org.springframework.cloud:spring-cloud-starter-gateway-server-webmvc" }`.
- **Add:** `spring-cloud-dependencies = { module = "org.springframework.cloud:spring-cloud-dependencies", version.ref = "springCloud" }` (BOM, if not already present) so the unversioned gateway starter resolves via Spring Cloud 2024.0.0.
- **Add:** `wiremock = { module = "org.wiremock:wiremock-standalone", version.ref = "wiremock" }` with `wiremock` pinned to the current stable 3.x (the plan resolves the exact version from Maven Central) — test-only.

`backend/gateway/build.gradle.kts`:
- Plugins: `kotlin("jvm")`, `kotlin("plugin.spring")`, `spring-boot`, `spring-dependency-management` (via catalog). No `kotlin-jpa` (no entities).
- `kotlin { jvmToolchain(21) }`.
- Import the Spring Cloud BOM via `implementation(platform(libs.spring.cloud.dependencies))` (same pattern used for the Testcontainers BOM in user-service), so the unversioned gateway starter resolves to the 2024.0.0-managed version.
- Dependencies: `spring-cloud-starter-gateway-server-webmvc`, `spring-boot-starter-actuator`, `bundles.observability`, `logstash-logback-encoder`, `jackson-module-kotlin`, `kotlin-reflect`.
- `testImplementation`: `spring-boot-starter-test`, `wiremock`.
- No data-jpa, flyway, postgres, or Testcontainers.

`backend/gateway/settings.gradle.kts`: `rootProject.name = "gateway"` + catalog reference to `../gradle/libs.versions.toml`.

`backend/gateway/Dockerfile`: multi-stage, **context = `backend/`** (same shape as user-service): `COPY gradle/ ./gradle/`, `COPY gateway/ ./gateway/`, `WORKDIR /workspace/gateway`, `./gradlew bootJar`; run stage `EXPOSE 8080`; runtime-env comment. `backend/.dockerignore` already covers build output.

Gradle wrapper: reuse the same 8.11.1 wrapper (fetched as in PR3, or copied from user-service).

---

## 8. CI

Add `gateway` to the matrix in `.github/workflows/backend-ci.yml`:
```yaml
matrix:
  service:
    - user-service
    - gateway
```
Its `./gradlew build` runs the WireMock routing test (fast, no Docker).

---

## 9. Docs to update

- `tech_stack.md`: API Gateway row → **Spring Cloud Gateway Server MVC** (servlet, Tomcat + virtual threads); reflect the catalog swap.
- `implementation_readiness_checklist.md` #6: note the MVC (servlet) variant.
- `clean_arch_backend_developer_context.md`: note infra-only services (Gateway) are exempt from the domain-layer structure.

---

## 10. Acceptance criteria

1. `./gradlew build` (in `backend/gateway/`) compiles and passes the routing test.
2. With the User Service running (`bootRun`, 8081) and the Gateway running (`bootRun`, 8080), `GET http://localhost:8080/ping` returns the User Service's ping response (`200`, `status: "UP"`).
3. `GET http://localhost:8080/actuator/health` → `UP`; `/actuator/prometheus` exposes metrics.
4. Standalone Gradle build (own wrapper) referencing the shared catalog by relative path.
5. Routing test present and green; `gateway` added to CI matrix.

---

## 11. PR scope & size

Single PR on branch `m0-pr4-gateway-skeleton`, ≤500 changed lines excluding the generated Gradle wrapper. Contents: the `gateway` module (build files, wrapper, Dockerfile), `GatewayApplication`, `RoutesConfig`, resources (yml + logback), the routing test, the `libs.versions.toml` edits, the CI matrix line, and the three doc updates.
