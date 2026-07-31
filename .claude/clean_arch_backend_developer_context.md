# Clean Architecture — Backend Developer Context

We are following clean architecture for all backend services. Each service lives in its own directory under `backend/<service-name>/`. The Java source root for each service is `src/main/java/com/trackmybuds/<service-name>/`. All package paths below are relative to that root.

Whenever generating code, use the structure described in this document.

---

## 1. Architecture Components

### Domain Layer
- Contains core business logic, free of any framework or infrastructure dependency.
- Package: `domain`
- Sub-packages:
    - `entity` — Domain entities as plain Java classes (no JPA or framework annotations).
    - `repository` — Repository interfaces (outbound ports) for **persistence** — database and cache. Implemented by outbound adapters.
    - `gateway` — Outbound port interfaces for **external third-party services** (identity provider, push, email, SMS, object storage). Provider-agnostic: they accept and return only our own types — no vendor SDK types leak through. Implemented by outbound adapters.
    - `usecase` — Use case interfaces (inbound ports) and their implementations. Each use case class handles a single operation.

### Inbound Adapters (`adapter/in`)
- Classes that receive calls from outside and drive the application. Each adapter calls use case interfaces in the domain layer.
- Package: `adapter/in`
- Sub-packages:
    - `web` — REST controllers annotated with `@RestController`. One controller class per domain resource. Contains request/response DTOs, their `@Valid` Bean Validation annotations, MapStruct mappers (DTO ↔ domain entity), and the global exception handler (`@ControllerAdvice`).
    - `messaging` — Kafka consumers annotated with `@KafkaListener`. One consumer class per Kafka topic. Present only in services that consume Kafka events (e.g. Notification Service).

### Outbound Adapters (`adapter/out`)
- Classes that the application calls to reach external systems. Each adapter implements a `domain/repository` (persistence) or `domain/gateway` (external service) port — the domain depends only on the interface, never on the adapter.
- Package: `adapter/out`
- Sub-packages:
    - `persistence` — Spring Data JPA implementations for PostgreSQL. Contains JPA entity models (annotated with `@Entity`), Spring Data JPA repository interfaces, the repository implementation class that implements the domain repository interface, and MapStruct mappers (JPA entity ↔ domain entity). All co-located per domain resource.
    - `cache` — Redis implementations for location cache and Pub/Sub. Contains the implementation class and its MapStruct mapper.
    - `identity` — Identity-provider gateway implementation (Firebase today). Implements the `domain/gateway` identity ports; the **only** place identity-provider SDK types may appear. See "Identity Provider" below.
    - `web` — HTTP client implementations for other external REST APIs. Contains the client class and its mapper.
    - `messaging` — Kafka producers. Called directly by controllers after a successful use case execution. Present only in services that publish Kafka events (e.g. Group Service).
    - `mock` — Mock implementations of domain `repository` / `gateway` ports. Used in unit tests instead of real infrastructure.
- Each additional external provider (push, email, SMS, object storage) gets its own `adapter/out/<capability>` sub-package implementing the corresponding `domain/gateway` port, following the identity-provider pattern below.

### Config
- Package: `config`
- Contains Spring `@Configuration` classes for explicit bean definitions (e.g. Redis client config, Kafka producer/consumer config, WebSocket config).

### Identity Provider — worked example of the gateway pattern
Authentication is delegated to an external identity provider (Firebase today) but kept provider-agnostic. The capability is expressed as small, focused ports in `domain/gateway`, each backed by a single provider adapter in `adapter/out/identity`:

| Port (`domain/gateway`) | Responsibility | Adapter (`adapter/out/identity`, Firebase today) |
|-------------------------|----------------|--------------------------------------------------|
| `IdentityTokenVerifier` | Verify an identity token; return a generic principal (`providerUid` + provider) | Verifies the Firebase ID token via the Firebase Admin SDK |
| `IdentityProvisioner` | Resolve a verified identity to the data needed for first-login provisioning | Reads the verified Firebase identity |
| `SessionRevoker` | Revoke all of a user's sessions (server-initiated logout) | Calls Firebase Admin SDK `revokeRefreshTokens` |

Rules:
- Ports accept and return only our own types (`providerUid`, principal, `userId`) — never identity-provider SDK types.
- Identity-provider SDK imports appear **only** inside `adapter/out/identity`. If they leak into `domain`, `usecase`, `adapter/in`, or any DTO, the abstraction is broken.
- A service declares only the ports it needs: the **API Gateway** uses `IdentityTokenVerifier`; **Auth Service** uses `IdentityProvisioner` and `SessionRevoker`. Ports are not shared across services (no shared kernel — each service owns its own copy so it can be extracted independently).

---

## Observability

### Metrics
- Spring Boot Actuator is enabled on all services. It exposes:
    - `/actuator/health` — liveness and readiness checks (database, Redis, Kafka connectivity).
    - `/actuator/prometheus` — Prometheus-format metrics scraped by the Prometheus container.
- Micrometer collects the following automatically — no custom code required:
    - JVM metrics (heap, GC, thread count)
    - HTTP request metrics (request rate, latency percentiles, error rate) per endpoint
    - Database connection pool metrics (HikariCP)
    - Kafka producer / consumer metrics
- Custom business metrics (e.g. location updates per second, active WebSocket connections) are added via `MeterRegistry` injection where needed.

### Distributed Tracing
- Micrometer Tracing with the Brave bridge is enabled on all services.
- A `traceId` and `spanId` are automatically injected into the MDC and appear in every log line — no manual code required.
- Trace context is propagated across service calls via HTTP headers (`b3` propagation format) automatically by the Spring Boot instrumentation.
- Spans are sent to Zipkin for visualisation.

### Health Checks
- Spring Boot Actuator auto-configures health indicators for PostgreSQL, Redis, and Kafka.
- `/actuator/health` returns the overall service status and the status of each dependency.
- In preprod and prod, this endpoint is accessible via the API Gateway only — not exposed directly.

---

## Logging

- Use the SLF4J API exclusively — never reference Logback classes directly. Declare loggers as:
  ```java
  private static final Logger log = LoggerFactory.getLogger(ClassName.class);
  ```
- Always use parameterised log statements — never string concatenation:
  ```java
  log.info("Location updated for user {}", userId);   // correct
  log.info("Location updated for user " + userId);    // wrong
  ```
- Never log sensitive data: passwords, auth tokens, raw location history in bulk.

### What each layer logs

| Layer | What to log | Level |
|-------|------------|-------|
| `adapter/in/web` | Request entry (HTTP method, path, authenticated userId); validation failures | INFO / WARN |
| `domain/usecase` | Key business events (e.g. "User {} joined group {}"); use case failures | INFO / ERROR |
| `adapter/out/persistence` | Slow query warnings; query failures with exception | WARN / ERROR |
| `adapter/out/cache` | Cache miss events; Redis failures with exception | DEBUG / ERROR |
| `adapter/out/web` | External API call failures with status code and exception | ERROR |
| `adapter/out/messaging` | Kafka publish failures with exception | ERROR |

### Log format by environment
- **Local** — plain text (human-readable in terminal). Configured in `application-local.yml`.
- **Pre-production / Production** — structured JSON via Logstash Logback Encoder. Each log line is a JSON object with `timestamp`, `level`, `logger`, `message`, `traceId`, and any MDC fields (e.g. `userId`, `groupId`). Configured in `application-preprod.yml` and `application-prod.yml`.

---

## Dependency Injection
- Spring Boot's built-in DI is used — no external DI library required.
- Always use **constructor injection**. Never use field injection (`@Autowired` on fields).
- Beans are auto-discovered via Spring's component scan. Use `@Service`, `@Component`, and `@Repository` on implementation classes.
- Explicit bean wiring goes in `@Configuration` classes inside the `config` package.

---

## 2. Project Structure

```
backend/<service-name>/
├── src/
│   ├── main/
│   │   ├── java/com/trackmybuds/<service-name>/
│   │   │   ├── domain/
│   │   │   │   ├── entity/
│   │   │   │   ├── repository/         (persistence ports)
│   │   │   │   ├── gateway/            (external-service ports: identity, push, email, sms, object store)
│   │   │   │   └── usecase/
│   │   │   ├── adapter/
│   │   │   │   ├── in/
│   │   │   │   │   ├── web/            (controllers, DTOs, mappers, exception handler)
│   │   │   │   │   └── messaging/      (only if service consumes Kafka events)
│   │   │   │   └── out/
│   │   │   │       ├── persistence/    (JPA entity models, Spring Data repos, impls, mappers)
│   │   │   │       ├── cache/          (Redis impls, mappers)
│   │   │   │       ├── identity/       (identity-provider adapter — Firebase today)
│   │   │   │       ├── web/            (HTTP clients for other external REST APIs)
│   │   │   │       ├── messaging/      (only if service publishes Kafka events)
│   │   │   │       └── mock/           (mock impls for unit tests)
│   │   │   └── config/
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-local.yml
│   │       ├── application-preprod.yml
│   │       └── application-prod.yml
│   └── test/
│       └── java/com/trackmybuds/<service-name>/
│           ├── unit/
│           └── integration/
├── pom.xml
└── Dockerfile
```

---

## 3. Environment Configuration
- Spring profiles map directly to the three environments: `local`, `preprod`, `prod`.
- `application.yml` holds base configuration shared across all environments.
- `application-local.yml`, `application-preprod.yml`, and `application-prod.yml` hold environment-specific overrides.
- The active profile is set via the `SPRING_PROFILES_ACTIVE` environment variable at runtime.

---

## 4. Testing
- Use JUnit 5 for all tests.
- Use Mockito for mocking dependencies in unit tests.
- Unit tests go in `src/test/java/.../unit/` and test individual classes in isolation.
- Integration tests go in `src/test/java/.../integration/` and use `@SpringBootTest` or `@DataJpaTest` to test with real Spring context or real DB layer.
- Mock implementations in `adapter/out/mock/` implement `domain/repository` interfaces and are used in unit tests to replace real infrastructure without starting a Spring context.

---

## 5. Setup — Run below steps when asked to run the basic setup.
- Create the folder structure as described above for the service.
- Add Spring Boot starter dependencies in `pom.xml`: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`.
- Add `spring-boot-starter-actuator` and `micrometer-registry-prometheus` to `pom.xml` for metrics and health checks.
- Add `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` to `pom.xml` for distributed tracing.
- Add `logstash-logback-encoder` to `pom.xml` for structured JSON logging in preprod and prod.
- Add `spring-boot-starter-test`, `mockito-core` in test scope in `pom.xml`.
- Add MapStruct dependency in `pom.xml` with the annotation processor configured.
- Create `application.yml` and environment-specific yml files under `src/main/resources/`.
- Create the main `@SpringBootApplication` entry point class in the root package.
- Add a `Dockerfile` using a multi-stage build: compile with a JDK image, run with a JRE image.

---

## Notable Differences vs Flutter Clean Architecture

| Concern | Flutter | Backend (Java / Spring Boot) |
|---------|---------|------------------------------|
| **Dependency injection** | GetIt — manual registration in `injection.dart` | Spring built-in — auto-discovery via annotations; constructor injection always |
| **Presentation layer** | Screens + BLoC (events, states) — stateful, UI-driven | REST controllers + DTOs — stateless, request/response |
| **State management** | BLoC manages UI state across events | None — each HTTP request is independent and stateless |
| **Adapter organisation** | `implementation/data/datasource/local\|remote\|mock/` — grouped by datasource type under a shared implementation layer | `adapter/in/web\|messaging` and `adapter/out/persistence\|cache\|web\|messaging\|mock` — split by direction (inbound vs outbound) |
| **Mapper placement** | Shared `mapper/` folder under implementation | Co-located with the adapter that owns the mapping: infrastructure mappers in `adapter/out/<technology>/`, DTO mappers in `adapter/in/web/` |
| **Mapper library** | Manual mappers | MapStruct — generates mapper implementations at compile time |
| **Messaging** | Not applicable | `adapter/in/messaging/` for Kafka consumers; `adapter/out/messaging/` for Kafka producers |
| **Environment config** | Environment-specific Dart files | Spring profiles via `application-{profile}.yml`; active profile set by `SPRING_PROFILES_ACTIVE` |
| **Testing setup** | Mockito + Flutter test + Build Runner | JUnit 5 + Mockito; `@SpringBootTest` for integration; `@DataJpaTest` for JPA layer |
| **Entry point** | `main.dart` — sets up DI and router | `@SpringBootApplication` class — Spring auto-configures everything |
| **Build file** | `pubspec.yaml` | `pom.xml` |
