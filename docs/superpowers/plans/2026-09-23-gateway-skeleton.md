# API Gateway Skeleton Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Scaffold the API Gateway as a standalone Kotlin/Spring Boot (Spring Cloud Gateway Server MVC) service that passthrough-routes `GET /ping` to the User Service, with Actuator/observability and a WireMock routing test.

**Architecture:** A thin, stateless routing service (no domain/adapter layering, no DB). One functional `RouterFunction` route forwards `/ping` to a configurable User Service URI. Standalone Gradle build sharing the monorepo version catalog by relative path. Servlet-based (Tomcat + virtual threads), consistent with the rest of the backend.

**Tech Stack:** Kotlin 1.9.25 on JDK 21, Spring Boot 3.4.1, Spring Cloud 2024.0.0 (`spring-cloud-starter-gateway-server-webmvc`), Gradle 8.11.1 (Kotlin DSL), Micrometer + Prometheus + Brave/Zipkin, Logback + logstash-encoder, JUnit 5 + WireMock 3.13.2. No DB / JPA / Flyway / Testcontainers.

**Spec:** `docs/superpowers/specs/2026-09-23-gateway-skeleton-design.md`

## Global Constraints

- **Language/runtime:** Kotlin, base package `com.trackmybuds.gateway`; source root `src/main/kotlin`, tests `src/test/kotlin`. JDK 21 toolchain via `kotlin { jvmToolchain(21) }`.
- **JDK 21 prerequisite:** an installed JDK 21 (Temurin at `C:\Program Files\Java\jdk-21`); Gradle detects it. No foojay fallback.
- **Gateway flavor:** Spring Cloud Gateway **Server MVC** (`spring-cloud-starter-gateway-server-webmvc`) — servlet, Tomcat + virtual threads. NOT the reactive/WebFlux gateway.
- **Standalone build:** own `settings.gradle.kts` + Gradle wrapper; reference the shared catalog with `from(files("../gradle/libs.versions.toml"))`. No root aggregating build.
- **HTTP port:** 8080 (matches `backend/docker/prometheus/prometheus.yml`).
- **Stateless:** no data-jpa, flyway, postgres, or Testcontainers. Tests are in-JVM (WireMock); Docker is NOT required for the gateway build.
- **Route target:** config key `gateway.user-service-uri`, default `http://localhost:8081`.
- **Logging:** SLF4J API only; parameterised; never log secrets. Follow `spring_best_practices.md`.
- **PR:** single PR on branch `m0-pr4-gateway-skeleton`, ≤500 changed lines excluding the generated Gradle wrapper.
- **Every commit message ends with this exact footer:**
  ```
  Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_011PYPZRa5VB4SYyuLSYknHP
  ```

---

## Task 0: Create the working branch

- [ ] **Step 1: Branch off main**

```bash
cd D:/swapnil/track-my-buds
git checkout main && git pull --ff-only
git checkout -b m0-pr4-gateway-skeleton
```

---

## Task 1: Bootable gateway module (build, wrapper, config, observability, CI, docs)

Produces a compiling, bootable `gateway` module whose Spring context loads (with the Gateway Server MVC starter, no routes yet). No Docker needed.

**Files:**
- Modify: `backend/gradle/libs.versions.toml`
- Create: `backend/gateway/settings.gradle.kts`
- Create: `backend/gateway/build.gradle.kts`
- Create: `backend/gateway/gradlew`, `backend/gateway/gradlew.bat`, `backend/gateway/gradle/wrapper/gradle-wrapper.jar`, `backend/gateway/gradle/wrapper/gradle-wrapper.properties` (copied from user-service)
- Create: `backend/gateway/src/main/kotlin/com/trackmybuds/gateway/GatewayApplication.kt`
- Create: `backend/gateway/src/main/resources/application.yml`, `application-local.yml`, `application-preprod.yml`, `application-prod.yml`
- Create: `backend/gateway/src/main/resources/logback-spring.xml`
- Create: `backend/gateway/Dockerfile`
- Modify: `.github/workflows/backend-ci.yml`
- Modify: `.claude/tech_stack.md`, `.claude/implementation_readiness_checklist.md`, `.claude/clean_arch_backend_developer_context.md`
- Test: `backend/gateway/src/test/kotlin/com/trackmybuds/gateway/GatewayApplicationTest.kt`

**Interfaces:**
- Produces: a Spring Boot app `com.trackmybuds.gateway.GatewayApplication`; a config property `gateway.user-service-uri` (String, default `http://localhost:8081`); catalog aliases `libs.spring.cloud.dependencies`, `libs.spring.cloud.starter.gateway.server.webmvc`, `libs.wiremock.standalone`.

- [ ] **Step 1: Edit the version catalog** — `backend/gradle/libs.versions.toml`

Under `[versions]` add:
```toml
wiremock = "3.13.2"
```
Under `[libraries]`, **remove** this line:
```toml
spring-cloud-starter-gateway = { module = "org.springframework.cloud:spring-cloud-starter-gateway" }
```
and add:
```toml
spring-cloud-dependencies = { module = "org.springframework.cloud:spring-cloud-dependencies", version.ref = "springCloud" }
spring-cloud-starter-gateway-server-webmvc = { module = "org.springframework.cloud:spring-cloud-starter-gateway-server-webmvc" }
wiremock-standalone = { module = "org.wiremock:wiremock-standalone", version.ref = "wiremock" }
```

- [ ] **Step 2: Write `backend/gateway/settings.gradle.kts`**

```kotlin
rootProject.name = "gateway"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
```

- [ ] **Step 3: Write `backend/gateway/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
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
    implementation(platform(libs.spring.cloud.dependencies))
    implementation(libs.spring.cloud.starter.gateway.server.webmvc)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)
    implementation(libs.bundles.observability)
    implementation(libs.logstash.logback.encoder)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.wiremock.standalone)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```
(No `kotlin-jpa` plugin — the gateway has no entities.)

- [ ] **Step 4: Copy the Gradle wrapper from user-service**

```bash
cd D:/swapnil/track-my-buds/backend
mkdir -p gateway/gradle/wrapper
cp user-service/gradlew gateway/gradlew
cp user-service/gradlew.bat gateway/gradlew.bat
cp user-service/gradle/wrapper/gradle-wrapper.jar gateway/gradle/wrapper/gradle-wrapper.jar
cp user-service/gradle/wrapper/gradle-wrapper.properties gateway/gradle/wrapper/gradle-wrapper.properties
```

- [ ] **Step 5: Write the entry point** — `backend/gateway/src/main/kotlin/com/trackmybuds/gateway/GatewayApplication.kt`

```kotlin
package com.trackmybuds.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class GatewayApplication

fun main(args: Array<String>) {
    runApplication<GatewayApplication>(*args)
}
```

- [ ] **Step 6: Write `backend/gateway/src/main/resources/application.yml`**

```yaml
spring:
  application:
    name: gateway

server:
  port: 8080

# Downstream target for routes (overridden per environment).
gateway:
  user-service-uri: http://localhost:8081

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

- [ ] **Step 7: Write `application-local.yml`**

```yaml
management:
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans
  tracing:
    sampling:
      probability: 1.0
```

- [ ] **Step 8: Write `application-preprod.yml`**

```yaml
gateway:
  user-service-uri: ${USER_SERVICE_URI}

management:
  zipkin:
    tracing:
      endpoint: ${ZIPKIN_ENDPOINT}
  tracing:
    sampling:
      probability: 0.1
```

- [ ] **Step 9: Write `application-prod.yml`**

```yaml
gateway:
  user-service-uri: ${USER_SERVICE_URI}

management:
  zipkin:
    tracing:
      endpoint: ${ZIPKIN_ENDPOINT}
  tracing:
    sampling:
      probability: 0.05
```

- [ ] **Step 10: Write `logback-spring.xml`** — `backend/gateway/src/main/resources/logback-spring.xml`

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

- [ ] **Step 11: Write the `Dockerfile`** — `backend/gateway/Dockerfile`

```dockerfile
# Build from the backend/ directory as context so the shared version catalog
# (../gradle/libs.versions.toml, referenced by settings.gradle.kts) is available:
#   docker build -f gateway/Dockerfile -t trackmybuds/gateway backend/
# --- build stage ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradle/ ./gradle/
COPY gateway/ ./gateway/
WORKDIR /workspace/gateway
RUN ./gradlew --no-daemon clean bootJar

# --- run stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/gateway/build/libs/*.jar app.jar
EXPOSE 8080
# Runtime config via env (12-factor): set SPRING_PROFILES_ACTIVE and, for non-local
# profiles, USER_SERVICE_URI + ZIPKIN_ENDPOINT. Injected by compose/deploy.
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 12: Add `gateway` to the CI matrix** — `.github/workflows/backend-ci.yml`

Change:
```yaml
        service:
          - user-service
```
to:
```yaml
        service:
          - user-service
          - gateway
```

- [ ] **Step 13: Update the docs**

In `.claude/tech_stack.md`, replace the API Gateway row:
```
| API Gateway | Spring Cloud Gateway | Single entry point — routing, rate limiting, and per-request identity-token verification (Model B). Stays in the JVM/Spring ecosystem so token verification sits alongside the Firebase Admin SDK identity adapter; no separate runtime to operate |
```
with:
```
| API Gateway | Spring Cloud Gateway (Server MVC) | Single entry point — routing, rate limiting, and per-request identity-token verification (Model B). Servlet-based (`spring-cloud-starter-gateway-server-webmvc`) on Tomcat + virtual threads — consistent with the Spring MVC backend, so blocking auth/Redis filters need no reactive offloading. Token verification sits alongside the Firebase Admin SDK identity adapter; no separate runtime to operate |
```

In `.claude/implementation_readiness_checklist.md`, append to the item #6 "Decided" line (the sentence ending "Recorded in `tech_stack.md`."):
```
 Uses the Server MVC (servlet) variant (`spring-cloud-starter-gateway-server-webmvc`) — consistent with the Spring MVC + virtual-threads stack; blocking auth/Redis filters are natural.
```

In `.claude/clean_arch_backend_developer_context.md`, immediately after the intro line that ends "...follow `spring_best_practices.md`." add a new paragraph:
```
> **Infrastructure-only services** (the API Gateway) are thin routing/edge apps with no domain, use cases, or persistence — they are **exempt** from the `domain`/`adapter` layering below. They contain only an application entry point, `config` (e.g. route definitions), and resources.
```

- [ ] **Step 14: Write the context-load test** — `backend/gateway/src/test/kotlin/com/trackmybuds/gateway/GatewayApplicationTest.kt`

```kotlin
package com.trackmybuds.gateway

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest
class GatewayApplicationTest {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            // No trace export during tests.
            registry.add("management.tracing.sampling.probability") { "0.0" }
        }
    }

    @Test
    fun contextLoads() {
    }
}
```

- [ ] **Step 15: Run the test — verify it PASSES**

```bash
cd D:/swapnil/track-my-buds/backend/gateway
./gradlew --no-daemon test --tests "com.trackmybuds.gateway.GatewayApplicationTest" --rerun-tasks
```
Expected: first run downloads Gradle 8.11.1 + resolves the Spring Cloud BOM + gateway-server-webmvc starter; the Spring context loads on Tomcat and `contextLoads` passes. No Docker required.

- [ ] **Step 16: Commit**

```bash
cd D:/swapnil/track-my-buds
git add backend/gradle/libs.versions.toml backend/gateway .github .claude
git update-index --chmod=+x backend/gateway/gradlew
git commit -m "$(cat <<'EOF'
feat(gateway): bootable Spring Cloud Gateway Server MVC module skeleton

Standalone Kotlin/Spring Boot gateway on port 8080 (servlet, Gateway Server MVC),
observability + logback profiles, Dockerfile, and a context-load test. Swaps the
catalog's reactive gateway starter for the MVC one, adds it to CI, and records the
MVC decision + gateway layering exemption in the docs.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_011PYPZRa5VB4SYyuLSYknHP
EOF
)"
```
(`git update-index --chmod=+x` sets the executable bit on the copied `gradlew` so Linux CI can run it; if `git status` after shows it already executable, the command is a harmless no-op — commit regardless.)

---

## Task 2: `/ping` passthrough route + routing test

Adds the functional route forwarding `GET /ping` to the User Service, proven by a WireMock routing test (in-JVM, no Docker).

**Files:**
- Create: `backend/gateway/src/main/kotlin/com/trackmybuds/gateway/config/RoutesConfig.kt`
- Test: `backend/gateway/src/test/kotlin/com/trackmybuds/gateway/GatewayRoutingTest.kt`

**Interfaces:**
- Consumes: the `gateway.user-service-uri` property (Task 1); the Gateway Server MVC DSL (`GatewayRouterFunctions.route`, `HandlerFunctions.http`, `BeforeFilterFunctions.uri`).
- Produces: a `RouterFunction<ServerResponse>` bean `pingRoute` mapping `GET /ping` → `${gateway.user-service-uri}/ping`.

- [ ] **Step 1: Write the failing routing test** — `backend/gateway/src/test/kotlin/com/trackmybuds/gateway/GatewayRoutingTest.kt`

```kotlin
package com.trackmybuds.gateway

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTest {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    companion object {
        // Started eagerly at class load so its port is available to @DynamicPropertySource.
        private val wireMock = WireMockServer(options().dynamicPort()).also { it.start() }

        @JvmStatic
        @AfterAll
        fun stopStub() {
            wireMock.stop()
        }

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("gateway.user-service-uri") { "http://localhost:${wireMock.port()}" }
            registry.add("management.tracing.sampling.probability") { "0.0" }
        }
    }

    @Test
    fun `GET ping is routed to the user service`() {
        wireMock.stubFor(
            get(urlEqualTo("/ping")).willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""{"service":"user-service","status":"UP"}"""),
            ),
        )

        val response = restTemplate.getForEntity("/ping", String::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("""{"service":"user-service","status":"UP"}""", response.body)
    }
}
```

- [ ] **Step 2: Run the test — verify it FAILS**

```bash
cd D:/swapnil/track-my-buds/backend/gateway
./gradlew --no-daemon test --tests "com.trackmybuds.gateway.GatewayRoutingTest" --rerun-tasks
```
Expected: FAIL — with no route defined, the gateway returns 404 for `/ping` (assertion on `HttpStatus.OK` fails).

- [ ] **Step 3: Write the route** — `backend/gateway/src/main/kotlin/com/trackmybuds/gateway/config/RoutesConfig.kt`

```kotlin
package com.trackmybuds.gateway.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.function.RouterFunction
import org.springframework.web.servlet.function.ServerResponse

@Configuration
class RoutesConfig {

    // Forwards GET /ping to ${gateway.user-service-uri}/ping. http() takes no args
    // (String/URI overloads deprecated since gateway 4.1.7); the target is the uri() before-filter.
    @Bean
    fun pingRoute(
        @Value("\${gateway.user-service-uri}") userServiceUri: String,
    ): RouterFunction<ServerResponse> =
        route("ping")
            .GET("/ping", http())
            .before(uri(userServiceUri))
            .build()
}
```

- [ ] **Step 4: Run the test — verify it PASSES**

```bash
cd D:/swapnil/track-my-buds/backend/gateway
./gradlew --no-daemon test --tests "com.trackmybuds.gateway.GatewayRoutingTest" --rerun-tasks
```
Expected: PASS — the gateway forwards `/ping` to the WireMock stub and returns its `200` body. If the DSL imports don't resolve, confirm the exact package against the resolved `spring-cloud-gateway-server-webmvc` jar (classes live under `org.springframework.cloud.gateway.server.mvc.*`).

- [ ] **Step 5: Run the whole build — verify everything is green**

```bash
cd D:/swapnil/track-my-buds/backend/gateway
./gradlew --no-daemon build
```
Expected: BUILD SUCCESSFUL — both `GatewayApplicationTest` and `GatewayRoutingTest` pass. No Docker needed.

- [ ] **Step 6: Commit**

```bash
cd D:/swapnil/track-my-buds
git add backend/gateway
git commit -m "$(cat <<'EOF'
feat(gateway): passthrough GET /ping route to the user service

Functional RouterFunction route forwarding /ping to ${gateway.user-service-uri}.
Verified with a WireMock routing test (in-JVM, no Docker).

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_011PYPZRa5VB4SYyuLSYknHP
EOF
)"
```

---

## Task 3: Manual verification through the live gateway (no commit)

Confirms acceptance criterion #2/#3 end-to-end: the gateway forwards to the real User Service.

- [ ] **Step 1: Start the infra + User Service**

```bash
cd D:/swapnil/track-my-buds/backend && docker compose up -d   # wait for tmb-postgres healthy
```
In a second terminal:
```bash
cd D:/swapnil/track-my-buds/backend/user-service && SPRING_PROFILES_ACTIVE=local ./gradlew --no-daemon bootRun
```
(If Docker Desktop is not running, start it first: `D:\docker\docker-desktop\Docker Desktop.exe`.)

- [ ] **Step 2: Start the Gateway**

```bash
cd D:/swapnil/track-my-buds/backend/gateway && SPRING_PROFILES_ACTIVE=local ./gradlew --no-daemon bootRun
```
Wait for Tomcat to start on port 8080.

- [ ] **Step 3: Hit the endpoints through the gateway**

```bash
curl -s http://localhost:8080/ping
curl -s http://localhost:8080/actuator/health
```
Expected: `/ping` → `{"service":"user-service","status":"UP","dbCheckedAt":"..."}` (proxied from the User Service on 8081); `/actuator/health` → `{"status":"UP",...}`. Stop both `bootRun` processes (Ctrl+C); leave the compose stack running.

- [ ] **Step 4: No commit** — verification only. If a config needed fixing, amend the relevant task's file and re-commit under that task.

---

## Task 4: Open the pull request

- [ ] **Step 1: Push and open the PR**

```bash
cd D:/swapnil/track-my-buds
git push -u origin m0-pr4-gateway-skeleton
gh pr create --base main --title "M0 PR4: API Gateway skeleton (Spring Cloud Gateway Server MVC)" --body "$(cat <<'EOF'
Standalone Kotlin/Spring Boot API Gateway (Spring Cloud Gateway Server MVC, servlet)
that passthrough-routes GET /ping to the User Service.

- Gateway Server MVC on port 8080 (Tomcat + virtual threads); catalog swapped from the
  reactive gateway starter to spring-cloud-starter-gateway-server-webmvc
- Functional RouterFunction route: /ping -> ${gateway.user-service-uri}
- Actuator + Micrometer/Prometheus + Brave/Zipkin; logback plain/JSON profiles
- WireMock routing test (in-JVM, no Docker); gateway added to the CI matrix
- Thin routing service — exempt from the domain/adapter layering (documented)

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_011PYPZRa5VB4SYyuLSYknHP
EOF
)"
```

---

## Self-Review (completed)

- **Spec coverage:** §2 tech/conventions → Task 1 (catalog swap, MVC starter, port, JDK 21). §3 structure → Task 1 (app) + Task 2 (config); domain-layering exemption doc'd in Task 1 Step 13. §4 route → Task 2. §5 observability/logging → Task 1 (Steps 6–10). §6 testing → Task 1 (contextLoads) + Task 2 (WireMock routing). §7 build/catalog → Task 1 (Steps 1–4, 11). §8 CI → Task 1 Step 12. §9 docs → Task 1 Step 13. §10 acceptance → Tasks 2 (build) + 3 (manual). §11 scope → single branch/PR (Task 0/4).
- **Placeholder scan:** none — all steps carry real content; the DSL-import note in Task 2 Step 4 is a verified fallback, not a TODO.
- **Type consistency:** `gateway.user-service-uri` (property) consistent across application.yml, RoutesConfig `@Value`, and both tests' `@DynamicPropertySource`; `pingRoute(): RouterFunction<ServerResponse>`; WireMock classes under `com.github.tomakehurst.wiremock.*` (unchanged package in 3.x); catalog aliases (`libs.spring.cloud.dependencies`, `libs.spring.cloud.starter.gateway.server.webmvc`, `libs.wiremock.standalone`) match the `[libraries]` keys added in Task 1 Step 1.
