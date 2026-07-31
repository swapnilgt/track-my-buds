# Tech Stack

## Client App
| Concern | Choice |
|---------|--------|
| Framework | Flutter |
| State management | BLoC |
| Dependency injection | GetIt |

## Backend Services
| Concern | Choice | Notes |
|---------|--------|-------|
| Language / Framework | Java / Spring Boot | |
| Architecture style | Microservices | |
| Mapping library | MapStruct | Compile-time type-safe mappers |
| Testing | JUnit 5 + Mockito | |
| Logging | SLF4J + Logback + Logstash Logback Encoder | SLF4J is the logging API; Logback is the implementation (bundled with Spring Boot); Logstash encoder outputs structured JSON in preprod and prod |
| Metrics collection | Spring Boot Actuator + Micrometer | Bundled with Spring Boot. Actuator exposes `/actuator/health` and `/actuator/prometheus`; Micrometer collects JVM, HTTP, DB pool, and Kafka metrics automatically |
| Distributed tracing | Micrometer Tracing + Brave bridge | Auto-populates `traceId` / `spanId` in logs and propagates trace context across service calls via HTTP headers |
| Metrics storage | Prometheus | Scrapes `/actuator/prometheus` from each service. Self-hosted container (local); cloud-managed TBD for preprod/prod |
| Metrics visualisation | Grafana | Queries Prometheus and renders dashboards. Self-hosted container (local); cloud-managed TBD for preprod/prod |
| Trace visualisation | Zipkin | Receives trace spans from Micrometer Tracing and visualises request flows across services. Self-hosted container (local); cloud-managed TBD for preprod/prod |

## Data Stores
| Concern | Choice | Notes |
|---------|--------|-------|
| Primary database | PostgreSQL + PostGIS | User, group, membership, and location data. PostGIS for geospatial queries. |
| Location cache | Redis | Latest known location per user and user group membership list for fast reads |
| Location fanout | Redis Pub/Sub | One channel per group (`location:group:{groupId}`); targeted real-time fanout to WebSocket instances |
| Message queue | Kafka | `group.events` for async notification fanout to FCM / email / SMS |
| Blob / object storage | MinIO (S3-compatible) | Stores user and group avatar images. S3 API means the same client code works against MinIO locally and any managed S3-compatible store in the cloud (AWS S3 / GCS / R2 — TBD for preprod/prod) |

## Real-time Communication
| Concern | Choice |
|---------|--------|
| Live location delivery to clients | WebSockets |
| Location fanout across WebSocket instances | Redis Pub/Sub |

## External Services
| Concern | Choice | Notes |
|---------|--------|-------|
| Phone OTP | Firebase Auth | Used during registration and phone number update |
| Google SSO | Firebase Auth / Google OAuth | Social login flow |
| Push notifications | Firebase Cloud Messaging (FCM) | In-app push notifications |
| Email notifications | TBD | Used for group invite and ownership change events |
| SMS notifications | TBD | Used for group invite and ownership change events |

> **Authentication model:** The app consumes the identity provider's tokens directly (verified at the API Gateway) rather than issuing its own JWTs. Firebase Auth is the current provider but is accessed behind a provider-agnostic identity interface — no API, service, downstream interface, or data model exposes Firebase-specific types, so the provider can be swapped later. Server-initiated logout is supported via the provider (Firebase Admin SDK today). See `high_level_architecture.md` and `api_contract.md`. Can be revisited if the product scales (switch to app-issued JWTs with a refresh-token strategy).
