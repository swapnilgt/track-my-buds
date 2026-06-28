# Environments

Three environments are maintained: local, pre-production, and production. Cloud infrastructure platform for pre-production and production is TBD.

---

## Local

**Purpose:** Day-to-day development on a developer's machine.

**How it runs:** All services and infrastructure are spun up via Docker Compose. Each microservice runs as a container alongside its infrastructure dependencies.

### Infrastructure
| Component | Setup |
|-----------|-------|
| PostgreSQL + PostGIS | Single container |
| Redis | Single container (serves both location cache and Pub/Sub) |
| Kafka | Single broker container with a Zookeeper container |
| All backend services | Individual containers via Docker Compose |
| API Gateway | Single container |

### External Services
| Service | Local Behaviour |
|---------|----------------|
| Firebase Auth (OTP + Google SSO) | Use Firebase Emulator Suite locally |
| Firebase FCM (push notifications) | Use Firebase Emulator Suite locally |
| Email notifications | Mailhog container — catches all outbound email, viewable via browser UI |
| SMS notifications | Stubbed — log SMS content to console instead of sending |

### Configuration
- No SSL/TLS
- Debug-level logging on all services
- Spring Boot DevTools enabled for hot reload
- All infrastructure ports exposed on localhost for direct inspection (e.g. PostgreSQL on 5432, Redis on 6379)
- Permissive CORS — accepts requests from any origin
- Secrets managed via `.env` file (not committed to version control)

---

## Pre-Production

**Purpose:** QA, integration testing, and staging before a production release. Mirrors production behaviour at reduced scale.

**How it runs:** Cloud platform TBD.

### Infrastructure
| Component | Setup |
|-----------|-------|
| PostgreSQL + PostGIS | Single instance (no read replicas) |
| Redis | Single instance |
| Kafka | Single broker |
| Backend services | Single instance per service |
| API Gateway | Single instance |

### External Services
| Service | Pre-Production Behaviour |
|---------|------------------------|
| Firebase Auth | Separate Firebase project for pre-production |
| Firebase FCM | Separate Firebase project for pre-production |
| Email notifications | Provider sandbox mode — emails delivered only to allowlisted test addresses |
| SMS notifications | Provider test mode — SMS sent only to verified test numbers |

### Configuration
- SSL/TLS enforced
- Info-level logging
- CORS restricted to pre-production client origins
- Secrets managed via environment variables injected at deploy time (no `.env` files)
- Database seeded with anonymised or synthetic test data — no real user data
- No direct infrastructure port exposure — all access via API Gateway; infrastructure accessible for debugging only via VPN or SSH tunnel

---

## Production

**Purpose:** Live environment serving real users.

**How it runs:** Cloud platform TBD.

### Infrastructure
| Component | Setup | Future provision |
|-----------|-------|-----------------|
| PostgreSQL + PostGIS | Single instance | Add read replicas and HA failover when load requires |
| Redis | Single instance | Move to replicated / clustered setup when load requires |
| Kafka | Single broker | Expand to multi-broker cluster when load requires |
| Backend services | Single instance per service | Add auto-scaling and load balancing when load requires |
| API Gateway | Single instance | Add load balancing when load requires |

### External Services
| Service | Production Behaviour |
|---------|---------------------|
| Firebase Auth | Production Firebase project |
| Firebase FCM | Production Firebase project |
| Email notifications | Provider production mode — real emails sent to all users |
| SMS notifications | Provider production mode — real SMS sent to all users |

### Configuration
- SSL/TLS enforced
- Warn-level logging (errors and warnings only; no debug output)
- CORS restricted to production client origins only
- Secrets managed via a secrets manager (TBD — e.g. AWS Secrets Manager, GCP Secret Manager)
- Monitoring and alerting enabled on all services and infrastructure
- No direct infrastructure port exposure — all access via API Gateway

---

## Environment Comparison

| Concern | Local | Pre-Production | Production |
|---------|-------|----------------|------------|
| Infrastructure platform | Docker Compose | TBD | TBD |
| SSL/TLS | No | Yes | Yes |
| Service instances | 1 per service | 1 per service | 1 per service (auto-scaling added later) |
| PostgreSQL | Single container | Single instance | Single instance (replicas added later) |
| Redis | Single container | Single instance | Single instance (clustering added later) |
| Kafka | Single broker | Single broker | Single broker (multi-broker added later) |
| Firebase | Emulator Suite | Separate project | Production project |
| Email | Mailhog (local) | Sandbox mode | Production mode |
| SMS | Console stub | Test mode | Production mode |
| Logging level | Debug | Info | Warn |
| Secrets | `.env` file | Env vars at deploy | Secrets manager (TBD) |
| Real user data | No | No (synthetic only) | Yes |
| Infrastructure port exposure | Yes (localhost only) | No (VPN / SSH tunnel only) | No |
