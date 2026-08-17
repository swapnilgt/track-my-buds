# Track My Buds — Backend

Microservices backend for Track My Buds. Each service is a **self-contained, standalone
Gradle build** with its own database, migrations, Dockerfile, and config, so any service
can be lifted into its own repository later. There is **no** shared library / shared kernel
and **no** root aggregating Gradle build — services integrate only over REST and Kafka.

See the design docs in [`../.claude`](../.claude): `high_level_architecture.md`,
`api_contract.md`, `tech_stack.md`, `clean_arch_backend_developer_context.md`,
`generic_coding_guidelines.md`, and the milestone plan `implementation_plan.md`.

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK | **21 (LTS)** | Build target. The Gradle toolchain will auto-provision it if a matching JDK isn't installed. |
| Docker + Docker Compose | recent | Runs local infra (Postgres+PostGIS, Redis, Kafka, MinIO, observability) and Testcontainers integration tests. |
| Gradle | — | **Not required.** Each service ships the Gradle wrapper (`./gradlew`). |

## Layout

```
backend/
├── gradle/
│   └── libs.versions.toml     # shared version catalog (referenced by each service; copied on extraction)
├── .editorconfig
├── docker-compose.yml         # local infra + observability            (added in M0 · PR2)
├── gateway/                   # Spring Cloud Gateway — standalone build (added in M0 · PR4)
├── auth-service/              # Auth                                    (added in M1)
├── user-service/              # User (also the M0 reference service)    (added in M0 · PR3)
├── group-service/             # Groups + membership + group.events      (added in M2)
├── location-service/          # Location REST + cache + fanout          (added in M3/M4)
├── websocket-service/         # Live location over WebSocket            (added in M4)
└── notification-service/      # FCM / email / SMS from group.events     (added in M5)
```

Each service follows the clean-architecture package structure in
`clean_arch_backend_developer_context.md`, under `src/main/java/com/trackmybuds/<service>/`.

## Building a service

Each service is built independently from its own directory:

```bash
cd user-service
./gradlew build          # compile + unit tests + integration tests (Testcontainers → needs Docker)
./gradlew test           # unit + integration tests
./gradlew bootRun        # run locally (expects infra from docker-compose to be up)
```

The wrapper downloads the pinned Gradle version on first run; no system Gradle is needed.

## Local infrastructure

Bring up the shared local stack (Postgres+PostGIS, Redis, Kafka, MinIO, Prometheus,
Grafana, Zipkin) from this directory:

```bash
docker compose up -d      # docker-compose.yml is added in M0 · PR2
```

## Dependency versions

All dependency versions live in `gradle/libs.versions.toml` and are referenced via catalog
aliases (`libs.…`) in each service's `build.gradle.kts` — never hard-coded. Bump versions
there, in one place. When a service is extracted to its own repo, copy this file in and
update the `from(files(...))` path in its `settings.gradle.kts`.
