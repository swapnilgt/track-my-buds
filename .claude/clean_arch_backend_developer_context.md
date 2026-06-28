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
    - `repository` — Repository interfaces defining data access contracts.
    - `usecase` — Use case interfaces and their implementations. Each use case class handles a single operation.

### Data Layer
- Contains data source interfaces that abstract the underlying storage or external system.
- Package: `data`
- Sub-packages:
    - `datasource` — Data source interfaces. Each repository has a corresponding data source interface in a file named `<RepoName>Datasource.java`. Data source interfaces have exactly the same methods as the corresponding repository interface unless there are specific mentions otherwise.

### Implementation Layer
- Contains all concrete implementations of the domain and data layers.
- Package: `implementation`
- Sub-packages:
    - `data/datasource/jpa` — Spring Data JPA implementations for PostgreSQL. Contains JPA entity models (annotated with `@Entity`) alongside their repository implementations.
    - `data/datasource/redis` — Redis implementations for cache and Pub/Sub operations.
    - `data/datasource/http` — HTTP client implementations for external APIs (e.g. Firebase Auth).
    - `data/datasource/mock` — Mock implementations used in tests.
    - `data/mapper` — Mappers that convert datasource-specific models (JPA entities, Redis models) to domain entities and vice versa. Use MapStruct for all mappers.
    - `domain/repository` — Concrete implementations of domain repository interfaces.

### Presentation Layer
- Contains REST controllers, request/response DTOs, and exception handling.
- Package: `presentation`
- Sub-packages:
    - `controller` — REST controllers annotated with `@RestController`. One controller class per domain resource.
    - `dto/request` — Inbound request DTOs. Validated with `@Valid` and Bean Validation annotations.
    - `dto/response` — Outbound response DTOs.
    - `mapper` — Mappers that convert domain entities to response DTOs and request DTOs to domain entities. Use MapStruct for all mappers.
    - `exception` — Global exception handler using `@ControllerAdvice`.

### Messaging Layer
- Present only in services that produce or consume Kafka events.
- Package: `messaging`
- Sub-packages:
    - `producer` — Kafka producers. Called directly by controllers after a successful use case execution. Present only in services that publish Kafka events (e.g. Group Service).
    - `consumer` — Kafka consumers annotated with `@KafkaListener`. One consumer class per Kafka topic. Present only in services that consume Kafka events (e.g. Notification Service).

### Config
- Package: `config`
- Contains Spring `@Configuration` classes for explicit bean definitions (e.g. Redis client config, Kafka producer/consumer config, WebSocket config).

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
│   │   │   │   ├── repository/
│   │   │   │   └── usecase/
│   │   │   ├── data/
│   │   │   │   └── datasource/
│   │   │   ├── implementation/
│   │   │   │   ├── data/
│   │   │   │   │   ├── datasource/
│   │   │   │   │   │   ├── jpa/
│   │   │   │   │   │   ├── redis/
│   │   │   │   │   │   ├── http/
│   │   │   │   │   │   └── mock/
│   │   │   │   │   └── mapper/
│   │   │   │   └── domain/
│   │   │   │       └── repository/
│   │   │   ├── presentation/
│   │   │   │   ├── controller/
│   │   │   │   ├── dto/
│   │   │   │   │   ├── request/
│   │   │   │   │   └── response/
│   │   │   │   ├── mapper/
│   │   │   │   └── exception/
│   │   │   ├── messaging/
│   │   │   │   ├── producer/           (only if service publishes Kafka events)
│   │   │   │   └── consumer/           (only if service consumes Kafka events)
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
- Mock datasource implementations in `implementation/data/datasource/mock/` are used to support unit testing of use cases and repositories without infrastructure dependencies.

---

## 5. Setup — Run below steps when asked to run the basic setup.
- Create the folder structure as described above for the service.
- Add Spring Boot starter dependencies in `pom.xml`: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`.
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
| **Datasource implementations** | `local/`, `remote/`, `mock/` | `jpa/`, `redis/`, `http/`, `mock/` — named by infrastructure type |
| **Mapper levels** | One level: datasource model → domain entity | Two levels: datasource model → domain entity (in `implementation/data/mapper/`) AND domain entity ↔ DTO (in `presentation/mapper/`) |
| **Mapper library** | Manual mappers | MapStruct — generates mapper implementations at compile time |
| **Messaging** | Not applicable | `messaging/producer/` for Kafka producers (called by controllers); `messaging/consumer/` for Kafka consumers |
| **Environment config** | Environment-specific Dart files | Spring profiles via `application-{profile}.yml`; active profile set by `SPRING_PROFILES_ACTIVE` |
| **Testing setup** | Mockito + Flutter test + Build Runner | JUnit 5 + Mockito; `@SpringBootTest` for integration; `@DataJpaTest` for JPA layer |
| **Entry point** | `main.dart` — sets up DI and router | `@SpringBootApplication` class — Spring auto-configures everything |
| **Build file** | `pubspec.yaml` | `pom.xml` |
