# High Level Architecture

## Overview
Track My Buds is built on a microservices architecture with a Flutter mobile client. Services communicate via REST over HTTPS for standard operations, WebSockets for real-time location delivery, and Kafka for async event streaming between services.

---

## Architecture Diagram

```mermaid
graph TD
    App["Flutter Client App"]

    subgraph Gateway Layer
        GW["API Gateway\n(JWT validation · Rate limiting · Routing)"]
    end

    subgraph Backend Services
        AuthSvc["Auth Service"]
        UserSvc["User Service"]
        GroupSvc["Group Service"]
        LocationSvc["Location Service"]
        WSSvc["WebSocket Service"]
        NotifSvc["Notification Service"]
    end

    subgraph Data Stores
        PG[("PostgreSQL + PostGIS")]
        Redis[("Redis (cache)")]
        Kafka[["Kafka"]]
    end

    subgraph External Services
        FirebaseAuth["Firebase Auth\n(Phone OTP · Google SSO)"]
        FCM["Firebase FCM\n(Push Notifications)"]
        EmailProv["Email Provider (TBD)"]
        SMSProv["SMS Provider (TBD)"]
    end

    App -->|"HTTPS / REST"| GW
    App <-->|"WebSocket"| GW

    GW --> AuthSvc
    GW --> UserSvc
    GW --> GroupSvc
    GW --> LocationSvc
    GW --> WSSvc

    AuthSvc -->|"Phone OTP + Google SSO"| FirebaseAuth

    AuthSvc --> PG
    UserSvc --> PG
    GroupSvc --> PG
    LocationSvc --> PG
    LocationSvc --> Redis

    GroupSvc -->|"group.events"| Kafka
    LocationSvc -->|"location.updates"| Kafka

    Kafka -->|"group.events"| NotifSvc
    Kafka -->|"location.updates"| WSSvc

    WSSvc -->|"live location push"| App

    NotifSvc --> FCM
    NotifSvc --> EmailProv
    NotifSvc --> SMSProv
```

---

## Services

### API Gateway
- Single entry point for all client traffic (REST and WebSocket)
- Validates JWT tokens on every request before forwarding
- Enforces account activation state — rejects requests from accounts without a username set
- Applies rate limiting

### Auth Service
- Handles user registration and login via two flows:
  - **Phone number + OTP**: delegates OTP delivery and verification to Firebase Auth
  - **Google SSO**: delegates to Firebase Auth / Google OAuth
- Issues JWT tokens on successful authentication
- Handles OTP re-verification when a user updates their phone number

### User Service
- Manages user profile fields: name, email, avatar, phone number, locationSharingEnabled
- Phone number updates are gated behind OTP re-verification (coordinated with Auth Service)
- `locationSharingEnabled` changes are written synchronously to PostgreSQL with no eventual consistency tolerance (highly consistent per NFR)

### Group Service
- Manages group lifecycle: create, update name and avatar, delete
- Manages membership: invite user, accept invite, remove member, user self-remove
- Manages ownership: promote user to owner, demote owner
- Publishes events to Kafka topic `group.events` for all membership and ownership changes

### Location Service
- Receives location updates pushed by client devices
- Persists location to PostgreSQL + PostGIS (source of truth)
- Caches latest location per user in Redis for low-latency reads
- Publishes to Kafka topic `location.updates` for WebSocket fanout
- Exposes query endpoint for last known location of all users in a group (reads from Redis cache, falls back to PostgreSQL)
- Supports geospatial queries via PostGIS:
  - Group centerpoint (`ST_Centroid` + `ST_Collect` over member locations)
  - Nearest pinned location to the whole group (KNN via `<->` operator or minimum total distance)

### WebSocket Service
- Maintains persistent WebSocket connections with active clients
- Consumes `location.updates` from Kafka
- Fans out location updates to all connected clients who are members of the relevant group

### Notification Service
- Consumes `group.events` from Kafka
- Dispatches notifications via three channels for each qualifying event:
  - **FCM** (Firebase Cloud Messaging) — in-app push notification to the target user's device
  - **Email** — via email provider (TBD) for invite and ownership change events
  - **SMS** — via SMS provider (TBD) for invite and ownership change events

---

## Data Stores

### PostgreSQL + PostGIS
Primary database for all persistent data.

| Data | Notes |
|------|-------|
| Users | Profile fields, auth metadata |
| Groups | Name, avatar |
| Group Memberships | User ↔ Group association, ownership flag |
| Location history | lat, long, timestamp per user; PostGIS geometry column for spatial queries |

PostGIS enables:
- `ST_Centroid` + `ST_Collect` — compute centerpoint of all group members' locations
- `<->` KNN operator — find nearest pinned location to group centerpoint
- `SUM(ST_Distance(...))` — find pinned location with minimum total distance to all members

### Redis
- Stores latest known location per user as a cache
- Used by Location Service for fast reads when serving live group location view
- PostgreSQL is the source of truth; Redis is eviction-tolerant

---

## Message Queue (Kafka)

| Topic | Producer | Consumer | Purpose |
|-------|----------|----------|---------|
| `group.events` | Group Service | Notification Service | Trigger FCM/email/SMS on invite, promote, demote events |
| `location.updates` | Location Service | WebSocket Service | Fan out location updates to connected clients across WebSocket server instances |

---

## External Services

| Purpose | Provider |
|---------|----------|
| Phone OTP (registration + phone number update) | Firebase Auth |
| Google SSO | Firebase Auth / Google OAuth |
| In-app push notifications | Firebase Cloud Messaging (FCM) |
| Email notifications | TBD |
| SMS notifications | TBD |

---

## NFR → Architecture Decisions

| NFR | Architecture Decision |
|-----|-----------------------|
| Login and Signup highly available | Auth Service scaled horizontally behind API Gateway; Firebase Auth handles OTP delivery |
| Location sharing toggle highly consistent | Synchronous write to PostgreSQL; no cache layer for `locationSharingEnabled` field |
| Remove user from group highly available and consistent | Synchronous write to PostgreSQL in Group Service |
| Demote owner highly available and consistent | Synchronous write to PostgreSQL in Group Service |
| Location updates highly available, consistent within 30s | Write to PostgreSQL + Redis cache; Kafka → WebSocket Service fans out within tolerance |
| Live location reads, max 5 min delay | Served from Redis cache; cache TTL aligned to 30s location update consistency window |
| Notifications max 5 min delay | Async via Kafka `group.events` → Notification Service → FCM / Email / SMS |
| Group metadata eventually consistent (5 min) | Written to PostgreSQL; no strict cache invalidation required within tolerance |
| User self-remove eventually consistent (5 min) | Written to PostgreSQL; propagated asynchronously |
| Promoting owner eventually consistent (5 min) | Written to PostgreSQL; propagated asynchronously |
