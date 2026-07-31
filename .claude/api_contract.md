# API Contract

Client-facing REST contract for all backend services, exposed through the API Gateway. This is the agreed interface between the Flutter app and the backend, and between the Gateway and downstream services.

> **Depends on auth flow (#2, `implementation_readiness_checklist.md`).** The token-exchange details, JWT claims, and the `X-User-Id` propagation described here are provisional until the auth flow is finalised. Endpoint shapes are stable regardless.

---

## 1. Conventions

### Base URL & versioning
- All endpoints are under `https://{host}/api/v1`.
- Version is carried in the URL path (`/api/v1`). A breaking change bumps to `/api/v2`.
- Real-time location uses a WebSocket endpoint (see §7), not REST.

### Authentication
- Every request carries `Authorization: Bearer <identity token>` — a bearer credential proving the caller is authenticated. **The API treats this token as opaque and provider-agnostic:** the identity provider is an implementation detail behind the Gateway's verifier. It is a Firebase ID token today (auto-refreshed by the Firebase SDK on the client, so there is no app-issued token and no refresh-token endpoint), but no API consumer or downstream service should assume Firebase specifically.
- The **API Gateway** verifies the identity token through an identity-provider abstraction (Firebase implementation today), resolves the caller's internal `userId` from the `AUTH_CREDENTIAL` mapping (cached in Redis), enforces the activation gate, and forwards the `userId` to downstream services in a trusted `X-User-Id` header. Downstream services never see or verify the identity token — they consume only the generic `X-User-Id` principal, staying fully provider-agnostic.
- `me` in a path always resolves to the caller's `X-User-Id`.
- The one exception is `POST /auth/session` (§2): on first login no mapping exists yet, so the Gateway verifies the identity token and forwards without resolving a `userId`.

> **Provider-agnostic by design.** Firebase Auth is the current identity provider, but all APIs and internal interfaces are Firebase-agnostic. Provider access sits behind interfaces (token verification, first-login provisioning, session revocation), with Firebase as the current adapter, so the provider can be swapped with change isolated to that adapter (and the client SDK).

> **Caveat — revisit at scale.** We currently consume the provider's identity tokens directly (Model B): no app-issued JWT, and no refresh-token strategy on our side. This keeps Auth simple but couples request-time auth to the provider. If the product scales or we need custom token semantics, revisit and consider having Auth Service issue our own app JWT (Model A) with a refresh-token strategy.

### Activation gate
- Until a user has created their profile, the Gateway allows only: `POST /auth/session` (§2), `POST /users/me` (create profile), and `GET /users/me`.
- Any other endpoint returns `403` with `type: "https://trackmybuds/errors/account-not-activated"` while the account is inactive.

### Content types
- Request and success response bodies: `application/json`.
- Error bodies: `application/problem+json` (RFC 7807).

### Success response shape (bare resources)
- Single resource: the resource object directly.
- Collections: `{ "items": [...], "page": { ... } }` (see pagination).
- Write endpoints return the affected resource, except where a `202 Accepted` / `204 No Content` is noted.

### Error response shape (RFC 7807 Problem Details)
```json
{
  "type": "https://trackmybuds/errors/validation-failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "name must be 30 characters or fewer",
  "instance": "/api/v1/groups",
  "errors": [
    { "field": "name", "message": "must be 30 characters or fewer" }
  ]
}
```
- `errors` is present only for field-level validation failures.

### Pagination (page/offset based)
- List endpoints accept `?page={0-based}&size={default 20, max 100}`.
- Response:
```json
{
  "items": [ ... ],
  "page": { "number": 0, "size": 20, "totalElements": 42, "totalPages": 3 }
}
```

### Common conventions
- Timestamps are ISO-8601 in UTC (e.g. `2026-07-18T10:23:41Z`).
- All ids are UUID strings.
- Standard status codes:

| Code | Meaning |
|------|---------|
| 200 | OK — resource returned |
| 201 | Created — resource created |
| 202 | Accepted — accepted for async processing (location update) |
| 204 | No Content — success with no body |
| 400 | Validation error |
| 401 | Missing / invalid token |
| 403 | Authenticated but not permitted (not owner, not activated, sharing disabled) |
| 404 | Resource not found |
| 409 | Conflict (e.g. username taken, last owner, duplicate invite) |

---

## 2. Auth Service — `/api/v1/auth`

Handles first-login provisioning and session revocation only — it does **not** issue tokens and does **not** own the user profile. The client authenticates with the identity provider (Firebase today — phone OTP or Google SSO) on-device and obtains an identity token, which it uses directly against the API (see §1). Auth Service's job is to establish the session and, on first login, mint the app `userId` and create the `AUTH_CREDENTIAL` mapping (`providerUid → userId`). The `USER` profile is created separately by User Service during onboarding (§3).

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/auth/session` | Identity token | Establish a session. Verifies the identity token; on first login mints `userId` and creates the `AUTH_CREDENTIAL` mapping. Idempotent. |
| DELETE | `/auth/sessions` | Bearer | Server-initiated logout — revoke all of the caller's active sessions with the provider. |

**POST `/auth/session`**
```json
// request: Authorization: Bearer <identity token>, no body

// 200 OK
{
  "userId": "...",
  "activated": false
}
```
- Called by the client after each sign-in. Idempotent — returns the existing mapping on subsequent calls.
- `activated` is `false` until the user has created their profile (§3), signalling the client to route to onboarding.

**Logout**
- *Client-side (ordinary):* the client clears the token on the device via the provider SDK sign-out — no server call needed.
- *Server-initiated:* `DELETE /auth/sessions` → `204`. Revokes all of the caller's sessions at the provider (e.g. "log out of all devices", or on compromise). Implemented today via the Firebase Admin SDK (`revokeRefreshTokens`); the Gateway then rejects already-issued tokens by verifying them against the provider's revocation time. Exposed through a provider-agnostic interface so the mechanism can change with the provider.

---

## 3. User Service — `/api/v1/users`

Owns the user profile for its entire lifecycle, including **first-time creation**. The profile is keyed by the `userId` minted by Auth Service (supplied via `X-User-Id`), so the `AUTH_CREDENTIAL` and `USER` rows always share the same id.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/users/me` | Bearer | Create the caller's profile (onboarding). Sets the immutable `username`; activates the account. |
| GET | `/users/me` | Bearer | Get the caller's own profile (`404` before creation). |
| PATCH | `/users/me` | Bearer | Update `name`, `email`, `avatarUrl`. Never `username` or `phoneNumber`. |
| PUT | `/users/me/location-sharing` | Bearer | Toggle `locationSharingEnabled` (highly consistent). |
| PUT | `/users/me/phone` | Bearer | Update phone number; requires a Firebase ID token proving ownership of the new number. |
| POST | `/users/me/avatar/upload-url` | Bearer | Get a pre-signed MinIO upload URL for a new avatar. |
| GET | `/users` | Bearer | Batch-fetch public profiles by id: `?ids=a,b,c`. Used to render group members. |

**GET `/users/me` → 200**
```json
{
  "id": "...",
  "name": "Asha",
  "username": "asha_k",
  "phoneNumber": "+919812345678",
  "email": "asha@example.com",
  "avatarUrl": "avatars/user/{id}",
  "locationSharingEnabled": true
}
```

**POST `/users/me`** (onboarding — create profile)
```json
// request
{ "username": "asha_k", "name": "Asha", "email": "asha@example.com" }

// 201 Created → profile; activates the account
```
- `username` and `name` are required; `email` and `avatarUrl` are optional. `username` is immutable once set.
- Returns `409` if a profile already exists for this `userId` or the username is taken.

**PUT `/users/me/location-sharing`** — `{ "enabled": false }` → `200` profile. Written synchronously (no eventual-consistency tolerance).

**PUT `/users/me/phone`** — `{ "firebaseIdToken": "<token-for-new-number>" }` → `200` profile.

**POST `/users/me/avatar/upload-url`** → `200`
```json
{ "uploadUrl": "https://minio/...signed...", "avatarUrl": "avatars/user/{id}" }
```
Client `PUT`s the image bytes to `uploadUrl`, then `PATCH /users/me` with `{ "avatarUrl": "avatars/user/{id}" }`.

**GET `/users?ids=a,b,c` → 200** — `items` of public profiles (`id`, `name`, `username`, `avatarUrl` only).

---

## 4. Group Service — groups & membership

### Groups — `/api/v1/groups`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/groups` | Bearer | Create a group; caller becomes `OWNER`. |
| GET | `/groups` | Bearer | List groups the caller is an `ACTIVE` member of (paginated). |
| GET | `/groups/{groupId}` | Member | Group details. |
| PATCH | `/groups/{groupId}` | Owner | Update `name` / `avatarUrl`. |

**POST `/groups`** — `{ "name": "Weekend Trip", "avatarUrl": null }` → `201` group.

### Membership — `/api/v1/groups/{groupId}/members`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/groups/{groupId}/members` | Member | List members. Owners also see `PENDING` invites; non-owners see only `ACTIVE`. |
| POST | `/groups/{groupId}/members` | Owner | Invite a user → creates a `PENDING` membership. |
| POST | `/groups/{groupId}/members/me/accept` | Invited | Accept own pending invite → `ACTIVE`. |
| DELETE | `/groups/{groupId}/members/me` | Member | Leave the group, or decline a pending invite. |
| DELETE | `/groups/{groupId}/members/{userId}` | Owner | Remove another member. |
| PUT | `/groups/{groupId}/members/{userId}/role` | Owner | Promote (`OWNER`) or demote (`MEMBER`). |

**POST `/groups/{groupId}/members`** — `{ "username": "ravi_p" }` (or `{ "userId": "..." }`) → `201` membership (`status: PENDING`), or `409` if already a member/invited.

**PUT `/groups/{groupId}/members/{userId}/role`** — `{ "role": "MEMBER" }` → `200`, or `409` `last-owner` if demoting the only owner.

**DELETE** endpoints → `204`.

### Invites (caller-scoped) — `/api/v1/invites`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/invites` | Bearer | List the caller's own `PENDING` invites across all groups. |

Serves the "you've been invited" view without the client knowing group ids up front. Accept/decline use the membership endpoints above.

---

## 5. Location Service — `/api/v1/locations`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/locations` | Bearer | Submit the caller's own location update. |
| GET | `/locations?groupId={groupId}` | Member | Last known location of every `ACTIVE` member of the group who has sharing enabled. |

**POST `/locations`** → `202 Accepted`
```json
{ "latitude": 12.97, "longitude": 77.59, "accuracy": 8.0, "recordedAt": "2026-07-18T10:23:41Z" }
```
- Returns `403` `location-sharing-disabled` if the caller has `locationSharingEnabled = false`.
- `202` because the write + fanout complete asynchronously within the 30s consistency window.

**GET `/locations?groupId={groupId}` → 200**
```json
{
  "items": [
    { "userId": "...", "latitude": 12.97, "longitude": 77.59, "recordedAt": "2026-07-18T10:23:20Z" }
  ]
}
```
- Members with sharing disabled or no recorded location are omitted.
- This is the REST snapshot used on first load and for WebSocket catch-up (see §7).

---

## 6. Notification Service

No client-facing REST API. Consumes `group.events` from Kafka and dispatches FCM / email / SMS. Event schema is tracked as checklist item #4.

---

## 7. Real-time location — WebSocket (WebSocket Service)

Live location is delivered over a WebSocket, not REST.

- **Connect:** `wss://{host}/ws/locations` with the identity token supplied during the handshake (`Authorization: Bearer <identity token>` header, or `?token=` where headers aren't available to the client).
- **Subscribe / unsubscribe** to a group's live feed (membership is verified once on subscribe):
```json
// client → server
{ "action": "subscribe",   "groupId": "..." }
{ "action": "unsubscribe", "groupId": "..." }
```
- **Server → client** location push:
```json
{ "type": "location", "groupId": "...", "userId": "...", "latitude": 12.97, "longitude": 77.59, "recordedAt": "2026-07-18T10:23:41Z" }
```

**Catch-up on connect (avoids a race):** the client subscribes over the WebSocket first, then calls `GET /locations?groupId={groupId}` for the current snapshot. Subscribing before snapshotting guarantees no update is missed in the gap between the two.

---

## 8. Routing summary (Gateway → service)

| Path prefix | Service |
|-------------|---------|
| `/api/v1/auth/**` | Auth Service |
| `/api/v1/users/**` | User Service |
| `/api/v1/groups/**` | Group Service |
| `/api/v1/invites/**` | Group Service |
| `/api/v1/locations/**` | Location Service |
| `/ws/locations` | WebSocket Service |
