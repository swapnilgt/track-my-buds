# User Service Reference Skeleton — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Scaffold the User Service as the reference Kotlin/Spring Boot backend service, proving the full clean-arch → Flyway → JPA → Testcontainers → observability path via a working DB-backed `GET /ping`.

**Architecture:** Clean architecture (domain / adapter-in-web / adapter-out-persistence). A single `Ping` vertical slice reads a seeded `heartbeat` row through every layer. Standalone Gradle build sharing the monorepo version catalog by relative path. Kotlin idioms: data classes, primary-constructor injection, hand-written extension-function mappers, no annotation processor.

**Tech Stack:** Kotlin 1.9.25 on JDK 21 (installed Temurin 21, detected by Gradle's toolchain — no auto-download), Spring Boot 3.4.1, Spring MVC, Gradle 8.11.1 (Kotlin DSL), Flyway, Spring Data JPA, PostgreSQL+PostGIS, Micrometer + Prometheus + Brave/Zipkin, Logback + logstash-encoder, JUnit 5 + MockK + Testcontainers.

**Spec:** `docs/superpowers/specs/2026-09-07-user-service-skeleton-design.md`

## Global Constraints

- **Language/runtime:** Kotlin (base package `com.trackmybuds.userservice`); source root `src/main/kotlin`, tests `src/test/kotlin`. JDK 21 toolchain via `kotlin { jvmToolchain(21) }`.
- **JDK 21 prerequisite:** an installed JDK 21 is required (Temurin 21 at `C:\Program Files\Java\jdk-21`); Gradle detects it. There is **no** foojay auto-download fallback — a missing JDK 21 fails the build with a clear message. Gradle itself runs on the system default JDK 17.
- **Standalone build:** own `settings.gradle.kts` + Gradle wrapper; reference the shared catalog with `from(files("../gradle/libs.versions.toml"))`. No root aggregating build.
- **HTTP port:** 8081 (fixed — matches `backend/docker/prometheus/prometheus.yml`).
- **DB (local):** database `user_service`, role `user_service`, password `localdev` — the per-service role from `backend/docker/postgres/initdb/init-multiple-databases.sh`. Never the `postgres` superuser.
- **Schema:** Flyway owns it; JPA `ddl-auto: validate` only. Never `update`/`create`.
- **Mapping:** hand-written extension functions only — no MapStruct, no annotation processor.
- **JPA entities:** mutable Kotlin `class` (relies on `kotlin-jpa` no-arg plugin), never `data class`.
- **Logging:** SLF4J API only; parameterised statements; never log secrets.
- **PR:** single PR on branch `m0-pr3-user-service-skeleton`, ≤500 changed lines excluding the generated Gradle wrapper. The `.claude/*.md` Kotlin doc edits and the spec/plan docs ride along in this branch.
- **Every commit message ends with this exact footer:**
  ```
  Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_011PYPZRa5VB4SYyuLSYknHP
  ```
- **Docker Desktop must be running** for the Testcontainers tests (the `postgis/postgis:17-3.5` image is already pulled locally).

---

## Task 0: Create the working branch

- [ ] **Step 1: Branch off main**

```bash
cd D:/swapnil/track-my-buds
git checkout -b m0-pr3-user-service-skeleton
```

---

## Task 1: Bootable module foundation (build, wrapper, config, migration, observability)

Produces a compiling, bootable `user-service` module whose Spring context loads against a real Postgres (Testcontainers) with Flyway `V1` applied. No domain/web layers yet.

**Files:**
- Modify: `backend/gradle/libs.versions.toml`
- Create: `backend/user-service/settings.gradle.kts`
- Create: `backend/user-service/build.gradle.kts`
- Create: `backend/user-service/gradle/wrapper/gradle-wrapper.properties`
- Create: `backend/user-service/gradle/wrapper/gradle-wrapper.jar` (fetched)
- Create: `backend/user-service/gradlew`, `backend/user-service/gradlew.bat` (fetched)
- Create: `backend/user-service/src/main/kotlin/com/trackmybuds/userservice/UserServiceApplication.kt`
- Create: `backend/user-service/src/main/resources/application.yml`
- Create: `backend/user-service/src/main/resources/application-local.yml`
- Create: `backend/user-service/src/main/resources/application-preprod.yml`
- Create: `backend/user-service/src/main/resources/application-prod.yml`
- Create: `backend/user-service/src/main/resources/logback-spring.xml`
- Create: `backend/user-service/src/main/resources/db/migration/V1__baseline.sql`
- Create: `backend/user-service/Dockerfile`
- Test: `backend/user-service/src/test/kotlin/com/trackmybuds/userservice/integration/PingIntegrationTest.kt`

**Interfaces:**
- Produces: a Spring Boot app `com.trackmybuds.userservice.UserServiceApplication`; a version catalog exposing `libs.plugins.kotlin.jvm|kotlin.spring|kotlin.jpa`, `libs.bundles.observability`, `libs.mockk`, `libs.jackson.module.kotlin`, `libs.kotlin.reflect`, `libs.logstash.logback.encoder`, `libs.flyway.core`, `libs.flyway.database.postgresql`, `libs.postgresql`, `libs.testcontainers.*`; a `heartbeat` table with one seeded row (id `00000000-0000-0000-0000-000000000001`).

- [ ] **Step 1: Edit the version catalog** — add Kotlin + MockK + Kotlin Jackson/reflect, remove MapStruct.

In `backend/gradle/libs.versions.toml`, under `[versions]` add:
```toml
kotlin = "1.9.25"
mockk = "1.13.13"
```
Under `[libraries]`, **remove** these three lines:
```toml
mapstruct = { module = "org.mapstruct:mapstruct", version.ref = "mapstruct" }
mapstruct-processor = { module = "org.mapstruct:mapstruct-processor", version.ref = "mapstruct" }
```
(and the `mapstruct = "1.6.3"` line under `[versions]`), then add:
```toml
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
jackson-module-kotlin = { module = "com.fasterxml.jackson.module:jackson-module-kotlin" }
kotlin-reflect = { module = "org.jetbrains.kotlin:kotlin-reflect", version.ref = "kotlin" }
```
Under `[bundles]`, **remove** the line `mapstruct = ["mapstruct"]`.
Under `[plugins]`, add:
```toml
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-spring = { id = "org.jetbrains.kotlin.plugin.spring", version.ref = "kotlin" }
kotlin-jpa = { id = "org.jetbrains.kotlin.plugin.jpa", version.ref = "kotlin" }
```

- [ ] **Step 2: Write `settings.gradle.kts`**

```kotlin
rootProject.name = "user-service"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
```
(No foojay auto-provisioning: JDK 21 is installed at `C:\Program Files\Java\jdk-21` and Gradle auto-detects installed JDKs from `C:\Program Files\Java`. If a build machine lacks a JDK 21, the build fails fast with "No compatible toolchains found" — the fix is to install JDK 21, not auto-download.)

- [ ] **Step 3: Write `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = "com.trackmybuds"
version = "0.0.1-SNAPSHOT"

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)
    implementation(libs.bundles.observability)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.logstash.logback.encoder)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mockk)
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

- [ ] **Step 4: Fetch the Gradle 8.11.1 wrapper** (no system Gradle available)

```bash
cd D:/swapnil/track-my-buds/backend/user-service
mkdir -p gradle/wrapper
curl -fSL -o gradle/wrapper/gradle-wrapper.jar \
  https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar
curl -fSL -o gradlew     https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradlew
curl -fSL -o gradlew.bat https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradlew.bat
```
Then create `gradle/wrapper/gradle-wrapper.properties`:
```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.11.1-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```
Fallback if the raw URLs fail: install Gradle once (`choco install gradle` or download `gradle-8.11.1-bin.zip` from services.gradle.org) and run `gradle wrapper --gradle-version 8.11.1`.

- [ ] **Step 5: Write the application entry point** — `src/main/kotlin/com/trackmybuds/userservice/UserServiceApplication.kt`

```kotlin
package com.trackmybuds.userservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class UserServiceApplication

fun main(args: Array<String>) {
    runApplication<UserServiceApplication>(*args)
}
```

- [ ] **Step 6: Write `src/main/resources/application.yml`** (base)

```yaml
spring:
  application:
    name: user-service
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true

server:
  port: 8081

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      probes:
        enabled: true
```

- [ ] **Step 7: Write `src/main/resources/application-local.yml`**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/user_service
    username: user_service
    password: localdev

management:
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans
  tracing:
    sampling:
      probability: 1.0
```

- [ ] **Step 8: Write `src/main/resources/application-preprod.yml`**

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

management:
  zipkin:
    tracing:
      endpoint: ${ZIPKIN_ENDPOINT}
  tracing:
    sampling:
      probability: 0.1
```

- [ ] **Step 9: Write `src/main/resources/application-prod.yml`**

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

management:
  zipkin:
    tracing:
      endpoint: ${ZIPKIN_ENDPOINT}
  tracing:
    sampling:
      probability: 0.05
```

- [ ] **Step 10: Write `src/main/resources/logback-spring.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <springProfile name="local">
        <include resource="org/springframework/boot/logging/logback/console-appender.xml"/>
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>

    <springProfile name="preprod,prod">
        <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
        </appender>
        <root level="INFO">
            <appender-ref ref="JSON"/>
        </root>
    </springProfile>

    <!-- No active profile (e.g. tests): plain console so logs are visible. -->
    <springProfile name="!local &amp; !preprod &amp; !prod">
        <include resource="org/springframework/boot/logging/logback/console-appender.xml"/>
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>
</configuration>
```

- [ ] **Step 11: Write the Flyway baseline** — `src/main/resources/db/migration/V1__baseline.sql`

```sql
CREATE TABLE heartbeat (
    id         UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO heartbeat (id) VALUES ('00000000-0000-0000-0000-000000000001');
```

- [ ] **Step 12: Write the `Dockerfile`** (for later compose integration; not built/verified at M0)

```dockerfile
# NOTE: build from the backend/ directory as context (so ../gradle/libs.versions.toml
# is available), or copy the catalog into the service on repo extraction.
# --- build stage ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew --no-daemon clean bootJar

# --- run stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 13: Write the failing context-load test** — `src/test/kotlin/com/trackmybuds/userservice/integration/PingIntegrationTest.kt`

```kotlin
package com.trackmybuds.userservice.integration

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Testcontainers
class PingIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:17-3.5").asCompatibleSubstituteFor("postgres")
        ).withDatabaseName("user_service")

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("management.tracing.sampling.probability") { "0.0" }
        }
    }

    @Test
    fun contextLoads() {
    }
}
```

- [ ] **Step 14: Run the test — verify it PASSES** (context loads, Flyway applies V1)

Run:
```bash
cd D:/swapnil/track-my-buds/backend/user-service
./gradlew test --tests "com.trackmybuds.userservice.integration.PingIntegrationTest"
```
Expected: BUILD SUCCESSFUL; the first run downloads Gradle 8.11.1 and uses the installed JDK 21 toolchain (detected at `C:\Program Files\Java\jdk-21`), then starts a `postgis/postgis:17-3.5` container, Flyway logs "Migrating schema ... to version 1 - baseline", and `contextLoads` passes.

- [ ] **Step 15: Commit**

```bash
cd D:/swapnil/track-my-buds
git add backend/gradle/libs.versions.toml backend/user-service .claude docs/superpowers
git commit -m "$(cat <<'EOF'
feat(user-service): bootable Kotlin/Spring Boot module skeleton

Standalone Gradle build, Flyway baseline, observability config, and a
Testcontainers context-load test. Switches backend to Kotlin (docs + catalog).

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_011PYPZRa5VB4SYyuLSYknHP
EOF
)"
```

---

## Task 2: Persistence layer — heartbeat read through the full stack

Adds the domain `Heartbeat` entity + `HeartbeatRepository` port and its JPA-backed adapter, and proves the seeded row reads back through Postgres.

**Files:**
- Create: `.../domain/entity/Heartbeat.kt`
- Create: `.../domain/repository/HeartbeatRepository.kt`
- Create: `.../adapter/out/persistence/HeartbeatJpaEntity.kt`
- Create: `.../adapter/out/persistence/HeartbeatJpaRepository.kt`
- Create: `.../adapter/out/persistence/HeartbeatPersistenceMappers.kt`
- Create: `.../adapter/out/persistence/HeartbeatRepositoryImpl.kt`
- Test: modify `PingIntegrationTest.kt` (add one test + an autowired field)

**Interfaces:**
- Produces:
  - `domain.entity.Heartbeat(id: java.util.UUID, createdAt: java.time.Instant)` — data class.
  - `domain.repository.HeartbeatRepository` with `fun findMarker(): Heartbeat?`.
- Consumes: the `heartbeat` table + seeded row from Task 1.

- [ ] **Step 1: Write the failing persistence test** — add to `PingIntegrationTest.kt`

Add these imports:
```kotlin
import com.trackmybuds.userservice.domain.repository.HeartbeatRepository
import org.junit.jupiter.api.Assertions.assertNotNull
import org.springframework.beans.factory.annotation.Autowired
```
Add inside the class body (not the companion object):
```kotlin
    @Autowired
    lateinit var heartbeatRepository: HeartbeatRepository

    @Test
    fun `findMarker returns the seeded heartbeat row`() {
        val marker = heartbeatRepository.findMarker()
        assertNotNull(marker)
    }
```

- [ ] **Step 2: Run the test — verify it FAILS**

Run:
```bash
./gradlew test --tests "com.trackmybuds.userservice.integration.PingIntegrationTest"
```
Expected: compilation failure — `HeartbeatRepository` is unresolved.

- [ ] **Step 3: Write the domain entity** — `domain/entity/Heartbeat.kt`

```kotlin
package com.trackmybuds.userservice.domain.entity

import java.time.Instant
import java.util.UUID

data class Heartbeat(
    val id: UUID,
    val createdAt: Instant,
)
```

- [ ] **Step 4: Write the repository port** — `domain/repository/HeartbeatRepository.kt`

```kotlin
package com.trackmybuds.userservice.domain.repository

import com.trackmybuds.userservice.domain.entity.Heartbeat

interface HeartbeatRepository {
    fun findMarker(): Heartbeat?
}
```

- [ ] **Step 5: Write the JPA entity** — `adapter/out/persistence/HeartbeatJpaEntity.kt` (mutable class, no-arg via plugin)

```kotlin
package com.trackmybuds.userservice.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "heartbeat")
class HeartbeatJpaEntity(
    @Id
    var id: UUID,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,
)
```

- [ ] **Step 6: Write the Spring Data repository** — `adapter/out/persistence/HeartbeatJpaRepository.kt`

```kotlin
package com.trackmybuds.userservice.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface HeartbeatJpaRepository : JpaRepository<HeartbeatJpaEntity, UUID>
```

- [ ] **Step 7: Write the persistence mapper** — `adapter/out/persistence/HeartbeatPersistenceMappers.kt`

```kotlin
package com.trackmybuds.userservice.adapter.out.persistence

import com.trackmybuds.userservice.domain.entity.Heartbeat

fun HeartbeatJpaEntity.toDomain(): Heartbeat =
    Heartbeat(id = id, createdAt = createdAt)
```

- [ ] **Step 8: Write the repository adapter** — `adapter/out/persistence/HeartbeatRepositoryImpl.kt`

```kotlin
package com.trackmybuds.userservice.adapter.out.persistence

import com.trackmybuds.userservice.domain.entity.Heartbeat
import com.trackmybuds.userservice.domain.repository.HeartbeatRepository
import org.springframework.stereotype.Repository

@Repository
class HeartbeatRepositoryImpl(
    private val jpaRepository: HeartbeatJpaRepository,
) : HeartbeatRepository {
    override fun findMarker(): Heartbeat? =
        jpaRepository.findAll().firstOrNull()?.toDomain()
}
```

- [ ] **Step 9: Run the test — verify it PASSES**

Run:
```bash
./gradlew test --tests "com.trackmybuds.userservice.integration.PingIntegrationTest"
```
Expected: BUILD SUCCESSFUL; both `contextLoads` and `findMarker returns the seeded heartbeat row` pass. (`ddl-auto: validate` now validates `HeartbeatJpaEntity` against the `heartbeat` table — a mismatch would fail startup.)

- [ ] **Step 10: Commit**

```bash
cd D:/swapnil/track-my-buds
git add backend/user-service
git commit -m "$(cat <<'EOF'
feat(user-service): heartbeat persistence adapter + repository port

Domain Heartbeat entity, HeartbeatRepository port, and JPA-backed adapter
with an extension-function mapper. Verified end-to-end via Testcontainers.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_011PYPZRa5VB4SYyuLSYknHP
EOF
)"
```

---

## Task 3: Ping use case (domain logic + unit test)

Adds the inbound `PingUseCase` port, its `Pong` result, the `@Service` implementation, and an in-memory mock adapter for fast unit testing.

**Files:**
- Create: `.../domain/usecase/Pong.kt`
- Create: `.../domain/usecase/PingUseCase.kt`
- Create: `.../domain/usecase/PingUseCaseImpl.kt`
- Create: `.../adapter/out/mock/InMemoryHeartbeatRepository.kt`
- Test: `.../unit/PingUseCaseImplTest.kt`

**Interfaces:**
- Consumes: `domain.repository.HeartbeatRepository` (Task 2); `domain.entity.Heartbeat` (Task 2).
- Produces:
  - `domain.usecase.Pong(service: String, status: String, dbCheckedAt: java.time.Instant)` — data class.
  - `domain.usecase.PingUseCase` with `fun ping(): Pong`. `status` is `"UP"` when `findMarker()` is non-null, else `"DOWN"`.
  - `adapter.out.mock.InMemoryHeartbeatRepository(marker: Heartbeat? = <a default marker>)` implementing `HeartbeatRepository`.

- [ ] **Step 1: Write the mock adapter** — `adapter/out/mock/InMemoryHeartbeatRepository.kt`

```kotlin
package com.trackmybuds.userservice.adapter.out.mock

import com.trackmybuds.userservice.domain.entity.Heartbeat
import com.trackmybuds.userservice.domain.repository.HeartbeatRepository
import java.time.Instant
import java.util.UUID

class InMemoryHeartbeatRepository(
    private val marker: Heartbeat? = Heartbeat(UUID.randomUUID(), Instant.now()),
) : HeartbeatRepository {
    override fun findMarker(): Heartbeat? = marker
}
```

- [ ] **Step 2: Write the failing unit test** — `src/test/kotlin/com/trackmybuds/userservice/unit/PingUseCaseImplTest.kt`

```kotlin
package com.trackmybuds.userservice.unit

import com.trackmybuds.userservice.adapter.out.mock.InMemoryHeartbeatRepository
import com.trackmybuds.userservice.domain.usecase.PingUseCaseImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PingUseCaseImplTest {

    @Test
    fun `ping returns UP and service name when marker present`() {
        val useCase = PingUseCaseImpl(InMemoryHeartbeatRepository(), "user-service")
        val pong = useCase.ping()
        assertEquals("user-service", pong.service)
        assertEquals("UP", pong.status)
    }

    @Test
    fun `ping returns DOWN when marker absent`() {
        val useCase = PingUseCaseImpl(InMemoryHeartbeatRepository(marker = null), "user-service")
        assertEquals("DOWN", useCase.ping().status)
    }
}
```

- [ ] **Step 3: Run the test — verify it FAILS**

Run:
```bash
./gradlew test --tests "com.trackmybuds.userservice.unit.PingUseCaseImplTest"
```
Expected: compilation failure — `PingUseCaseImpl` / `Pong` unresolved.

- [ ] **Step 4: Write the `Pong` result** — `domain/usecase/Pong.kt`

```kotlin
package com.trackmybuds.userservice.domain.usecase

import java.time.Instant

data class Pong(
    val service: String,
    val status: String,
    val dbCheckedAt: Instant,
)
```

- [ ] **Step 5: Write the `PingUseCase` port** — `domain/usecase/PingUseCase.kt`

```kotlin
package com.trackmybuds.userservice.domain.usecase

interface PingUseCase {
    fun ping(): Pong
}
```

- [ ] **Step 6: Write the implementation** — `domain/usecase/PingUseCaseImpl.kt`

```kotlin
package com.trackmybuds.userservice.domain.usecase

import com.trackmybuds.userservice.domain.repository.HeartbeatRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class PingUseCaseImpl(
    private val heartbeatRepository: HeartbeatRepository,
    @Value("\${spring.application.name}") private val serviceName: String,
) : PingUseCase {
    override fun ping(): Pong {
        val status = if (heartbeatRepository.findMarker() != null) "UP" else "DOWN"
        return Pong(service = serviceName, status = status, dbCheckedAt = Instant.now())
    }
}
```

- [ ] **Step 7: Run the test — verify it PASSES**

Run:
```bash
./gradlew test --tests "com.trackmybuds.userservice.unit.PingUseCaseImplTest"
```
Expected: BUILD SUCCESSFUL; both unit tests pass (no Spring context, no Docker).

- [ ] **Step 8: Commit**

```bash
cd D:/swapnil/track-my-buds
git add backend/user-service
git commit -m "$(cat <<'EOF'
feat(user-service): ping use case with in-memory mock adapter + unit tests

PingUseCase port + Pong result + @Service impl; UP/DOWN derived from the
heartbeat marker. Unit-tested via a hand-written mock repository.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_011PYPZRa5VB4SYyuLSYknHP
EOF
)"
```

---

## Task 4: Web layer — `GET /ping` endpoint end-to-end

Adds the REST controller, response DTO, DTO mapper, and the global exception handler, then proves `GET /ping` returns `200 {service, status:"UP", dbCheckedAt}` through the full stack against real Postgres.

**Files:**
- Create: `.../adapter/in/web/dto/PingResponse.kt`
- Create: `.../adapter/in/web/mapper/PingWebMappers.kt`
- Create: `.../adapter/in/web/PingController.kt`
- Create: `.../adapter/in/web/GlobalExceptionHandler.kt`
- Test: modify `PingIntegrationTest.kt` (add endpoint test + autowired `TestRestTemplate`)

**Interfaces:**
- Consumes: `domain.usecase.PingUseCase` + `Pong` (Task 3).
- Produces: `adapter.in.web.dto.PingResponse(service: String, status: String, dbCheckedAt: java.time.Instant)`; `fun Pong.toResponse(): PingResponse`; a controller mapping `GET /ping`.

- [ ] **Step 1: Write the failing endpoint test** — add to `PingIntegrationTest.kt`

Add these imports:
```kotlin
import com.trackmybuds.userservice.adapter.`in`.web.dto.PingResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
```
Add inside the class body:
```kotlin
    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Test
    fun `GET ping returns 200 with status UP and service name`() {
        val response = restTemplate.getForEntity("/ping", PingResponse::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("UP", response.body?.status)
        assertEquals("user-service", response.body?.service)
    }
```
(Note: `` `in` `` is backticked because `in` is a Kotlin keyword used as a package name.)

- [ ] **Step 2: Run the test — verify it FAILS**

Run:
```bash
./gradlew test --tests "com.trackmybuds.userservice.integration.PingIntegrationTest"
```
Expected: compilation failure — `PingResponse` unresolved (and, once compiling, a 404 on `/ping`).

- [ ] **Step 3: Write the response DTO** — `adapter/in/web/dto/PingResponse.kt`

```kotlin
package com.trackmybuds.userservice.adapter.`in`.web.dto

import java.time.Instant

data class PingResponse(
    val service: String,
    val status: String,
    val dbCheckedAt: Instant,
)
```

- [ ] **Step 4: Write the DTO mapper** — `adapter/in/web/mapper/PingWebMappers.kt`

```kotlin
package com.trackmybuds.userservice.adapter.`in`.web.mapper

import com.trackmybuds.userservice.adapter.`in`.web.dto.PingResponse
import com.trackmybuds.userservice.domain.usecase.Pong

fun Pong.toResponse(): PingResponse =
    PingResponse(service = service, status = status, dbCheckedAt = dbCheckedAt)
```

- [ ] **Step 5: Write the controller** — `adapter/in/web/PingController.kt`

```kotlin
package com.trackmybuds.userservice.adapter.`in`.web

import com.trackmybuds.userservice.adapter.`in`.web.dto.PingResponse
import com.trackmybuds.userservice.adapter.`in`.web.mapper.toResponse
import com.trackmybuds.userservice.domain.usecase.PingUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class PingController(
    private val pingUseCase: PingUseCase,
) {
    @GetMapping("/ping")
    fun ping(): PingResponse = pingUseCase.ping().toResponse()
}
```

- [ ] **Step 6: Write the global exception handler** — `adapter/in/web/GlobalExceptionHandler.kt`

```kotlin
package com.trackmybuds.userservice.adapter.`in`.web

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

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

- [ ] **Step 7: Run the full test class — verify it PASSES**

Run:
```bash
./gradlew test --tests "com.trackmybuds.userservice.integration.PingIntegrationTest"
```
Expected: BUILD SUCCESSFUL; `contextLoads`, `findMarker ...`, and `GET ping returns 200 ...` all pass.

- [ ] **Step 8: Run the whole build — verify everything is green**

Run:
```bash
./gradlew build
```
Expected: BUILD SUCCESSFUL; both the unit test and the integration test run and pass.

- [ ] **Step 9: Commit**

```bash
cd D:/swapnil/track-my-buds
git add backend/user-service
git commit -m "$(cat <<'EOF'
feat(user-service): GET /ping endpoint end-to-end

REST controller + response DTO + extension-function mapper + RFC 7807
exception handler. Verified via Testcontainers hitting the live endpoint.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_011PYPZRa5VB4SYyuLSYknHP
EOF
)"
```

---

## Task 5: Manual boot verification against the live stack

Confirms acceptance criteria #2/#3 by running the service against the real Docker Compose infrastructure (not Testcontainers).

- [ ] **Step 1: Bring up the infra**

```bash
cd D:/swapnil/track-my-buds/backend
docker compose up -d
```
Wait until `docker compose ps` shows `tmb-postgres` healthy.

- [ ] **Step 2: Run the service with the local profile**

```bash
cd D:/swapnil/track-my-buds/backend/user-service
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```
Expected: Flyway applies `V1` (or reports it already applied), the app starts on port 8081.

- [ ] **Step 3: Hit the endpoints** (in a second terminal)

```bash
curl -s http://localhost:8081/ping
curl -s http://localhost:8081/actuator/health
curl -s http://localhost:8081/actuator/prometheus | head -5
```
Expected: `/ping` → `{"service":"user-service","status":"UP","dbCheckedAt":"..."}`; `/actuator/health` → `{"status":"UP",...}`; `/actuator/prometheus` → metric lines. Stop `bootRun` (Ctrl+C) when done.

- [ ] **Step 4: No commit** — verification only. If any config needed fixing, amend the relevant task's file and re-commit under that task.

---

## Task 6: Open the pull request

- [ ] **Step 1: Push and open the PR**

```bash
cd D:/swapnil/track-my-buds
git push -u origin m0-pr3-user-service-skeleton
gh pr create --base main --title "M0 PR3: User Service reference skeleton (Kotlin)" --body "$(cat <<'EOF'
Reference Kotlin/Spring Boot service proving the full clean-arch → Flyway →
JPA → Testcontainers → observability path via a DB-backed GET /ping.

- Standalone Gradle build (own wrapper), shared version catalog by relative path
- Clean-arch ping slice: controller → use case → repository port → JPA → Postgres
- Flyway V1 baseline (heartbeat table + seed); ddl-auto: validate
- Actuator + Micrometer/Prometheus + Brave/Zipkin; logback plain/JSON profiles
- Unit test (use case) + Testcontainers integration test (context load, repo read, endpoint)
- Backend language switched to Kotlin (tech-stack docs + version catalog updated)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Self-Review (completed)

- **Spec coverage:** §2 tech/conventions → Task 1 (build/catalog/toolchain). §3 ping slice → Tasks 2–4. §4 package layout → Tasks 2–4 (empty packages intentionally omitted). §5 persistence/migration → Task 1 (V1) + Task 2 (entity/validate). §6 config/profiles → Task 1 (Steps 6–9). §7 observability/logging → Task 1 (build deps + Steps 6,10) + Task 5 (prometheus check). §8 testing → Task 3 (unit) + Tasks 1/2/4 (integration methods). §9 build/catalog → Task 1. §10 acceptance → Tasks 4/5. §11 scope → single branch/PR (Task 0/6).
- **Placeholder scan:** none — all steps carry real content; the Dockerfile note is a documented caveat, not a TODO.
- **Type consistency:** `HeartbeatRepository.findMarker(): Heartbeat?`, `Heartbeat(id: UUID, createdAt: Instant)`, `PingUseCase.ping(): Pong`, `Pong(service, status, dbCheckedAt)`, `PingResponse(service, status, dbCheckedAt)`, `fun Pong.toResponse()`, `fun HeartbeatJpaEntity.toDomain()` — consistent across Tasks 2/3/4 and the Interfaces blocks.
