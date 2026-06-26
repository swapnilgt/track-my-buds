# Tech Stack

## Client App
| Concern | Choice |
|---------|--------|
| Framework | Flutter |
| State management | BLoC |
| Dependency injection | GetIt |

## Backend Services
| Concern | Choice |
|---------|--------|
| Language / Framework | Java / Spring Boot |
| Architecture style | Microservices |

## Data Stores
| Concern | Choice | Notes |
|---------|--------|-------|
| Primary database | PostgreSQL + PostGIS | User, group, membership, and location data. PostGIS for geospatial queries. |
| Cache | Redis | Latest known location per user for fast reads |
| Message queue | Kafka | `group.events` for notification fanout; `location.updates` for WebSocket fanout |

## Real-time Communication
| Concern | Choice |
|---------|--------|
| Live location delivery | WebSockets |

## External Services
| Concern | Choice | Notes |
|---------|--------|-------|
| Phone OTP | Firebase Auth | Used during registration and phone number update |
| Google SSO | Firebase Auth / Google OAuth | Social login flow |
| Push notifications | Firebase Cloud Messaging (FCM) | In-app push notifications |
| Email notifications | TBD | Used for group invite and ownership change events |
| SMS notifications | TBD | Used for group invite and ownership change events |
