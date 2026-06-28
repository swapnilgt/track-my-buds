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
| Location cache | Redis | Latest known location per user and user group membership list for fast reads |
| Location fanout | Redis Pub/Sub | One channel per group (`location:group:{groupId}`); targeted real-time fanout to WebSocket instances |
| Message queue | Kafka | `group.events` for async notification fanout to FCM / email / SMS |

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
