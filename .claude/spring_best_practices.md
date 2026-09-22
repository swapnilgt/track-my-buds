# Spring Boot Best Practices (Backend)

Spring Boot / Kotlin-Spring conventions for the backend services. These complement the
structural rules in `clean_arch_backend_developer_context.md` and the cross-language rules
in `generic_coding_guidelines.md`; the client-facing error format lives in `api_contract.md`.

---

## Exception handling → RFC 7807 Problem Details

Every service has **one** centralized exception handler — a `@RestControllerAdvice` class in
`adapter/in/web/` — that translates exceptions into HTTP responses. It must:

1. **Extend `ResponseEntityExceptionHandler`.** The base class already carries `@ExceptionHandler`s
   for Spring's framework exceptions (bean-validation → 400, no handler / not found → 404, method
   not supported → 405, unreadable body → 400, …). Extending it keeps those mapped to their
   **correct status**.
2. **Enable RFC 7807 rendering:** set `spring.mvc.problemdetails.enabled=true` in `application.yml`,
   so those framework exceptions are returned as `ProblemDetail` bodies — the error format mandated
   by `api_contract.md`.
3. **Map domain exceptions explicitly:** add one `@ExceptionHandler(<DomainException>::class)` per
   domain error, returning `ProblemDetail.forStatusAndDetail(<status>, <message>)`. HTTP status is
   decided here, never in use cases (see `generic_coding_guidelines.md` → Error handling).
4. **Catch-all last:** a single `@ExceptionHandler(Exception::class)` returning **500** for
   genuinely-unexpected errors. Log it (SLF4J, parameterised, never secrets). Because the base
   class's handlers are more specific, they win over this catch-all for framework exceptions.

### Anti-pattern (why we extend the base class)

A bare `@ExceptionHandler(Exception::class)` in a plain `@RestControllerAdvice` that does **not**
extend `ResponseEntityExceptionHandler` runs *before* Spring's default resolver and matches
**everything** — so validation errors, unknown paths, and wrong HTTP methods all get turned into
**500**. Always extend `ResponseEntityExceptionHandler` so 4xx keep their real status.

### Reference

```kotlin
@RestControllerAdvice
internal class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    // Per domain exception, e.g.:
    // @ExceptionHandler(MemberNotFoundException::class)
    // fun handleMemberNotFound(ex: MemberNotFoundException): ProblemDetail =
    //     ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.message ?: "Not found")

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ProblemDetail {
        log.error("Unhandled exception", ex)
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred",
        )
    }
}
```

---

## JPA entities with Kotlin

Kotlin classes are `final` with no zero-arg constructor by default, but Hibernate needs entities
to be **non-final** (to create lazy proxies) and to have a **no-arg constructor**. Configure both
in the service's `build.gradle.kts`:

- Apply `kotlin("plugin.jpa")` — generates the no-arg constructor for `@Entity` / `@Embeddable` /
  `@MappedSuperclass`.
- Add an `allOpen { … }` block for the same annotations. `kotlin("plugin.spring")`'s all-open only
  opens Spring stereotypes (`@Component`, `@Service`, …), **not** JPA entities:
  ```kotlin
  allOpen {
      annotation("jakarta.persistence.Entity")
      annotation("jakarta.persistence.MappedSuperclass")
      annotation("jakarta.persistence.Embeddable")
  }
  ```
- Write JPA entity models as a **mutable `class`** (properties as `var`), **not** a `data class` —
  `data class` `equals`/`hashCode`/`copy` misbehave with JPA identity and lazy proxies. (Domain
  entities, by contrast, are immutable `data class`es — see `clean_arch_backend_developer_context.md`.)

---

## Overriding a Spring-Boot-managed dependency version

Spring Boot's dependency-management plugin pins many third-party versions via its BOM and **wins
over** a `version.ref` set in the Gradle version catalog. To actually bump one (e.g. Testcontainers),
override Boot's version property in `build.gradle.kts` rather than only editing the catalog:

```kotlin
// keeps the catalog the single source of truth while overriding Boot's managed version
extra["testcontainers.version"] = libs.versions.testcontainers.get()
```

Verify the bump took effect (e.g. `./gradlew dependencies` or a passing test on the new version) —
editing the catalog alone silently has no effect for BOM-managed libraries.
