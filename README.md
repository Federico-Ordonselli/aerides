# Raidboard

Raidboard is a multi-tenant web app where gaming groups (guilds, clans, tabletop RPG tables) organize sessions: events with role-based slots, sign-ups, automatic waitlists, reminders and attendance stats.

Under the hood it is a **resource-booking business application**: roles, approvals, notifications, audit log and reporting.

> 🚧 Work in progress, built milestone by milestone. See [Roadmap](#roadmap).

## Tech stack

| Layer    | Technologies |
|----------|--------------|
| Backend  | Java 21, Spring Boot 4.1, Spring Security (JWT resource server), Spring Data JPA / Hibernate 7, PostgreSQL 17, Flyway, MapStruct, Bucket4j, springdoc-openapi |
| Frontend | Angular 22 (standalone components, signals, zoneless), Angular Material 3, ngx-translate (IT/EN), Jest; coming next: FullCalendar (calendar view), Chart.js via ng2-charts (stats) |
| Testing  | JUnit 5, Mockito, Testcontainers, MockMvc, Jest |
| DevOps   | Docker, Docker Compose, nginx, Mailpit |

## Quick start (Docker)

Requirements: Docker with Compose.

```bash
cp .env.example .env
# set JWT_SECRET in .env, for example with: openssl rand -base64 48
docker compose up --build
```

| URL | What |
|-----|------|
| http://localhost:4200 | Web app |
| http://localhost:8080/swagger-ui.html | API documentation (Swagger UI) |
| http://localhost:8080/actuator/health | Health check |
| http://localhost:8025 | Mailpit (catches every email sent in development) |

**Game search** needs a free RAWG API key: get one at [rawg.io/apidocs](https://rawg.io/apidocs) and set `RAWG_API_KEY` in `.env`. Without it everything else works, and the search box says the catalog is unavailable.

## Local development

Requirements: JDK 21, Node 24, Docker (for PostgreSQL and the integration tests).

```bash
# 1. Infrastructure only
docker compose up -d postgres mailpit

# 2. Backend on :8080 (the "dev" profile provides a development JWT secret)
cd backend
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run

# 3. Frontend on :4200, proxying /api to the backend
cd frontend
npm install
npm start
```

Alternative to step 1 and 2: `./mvnw spring-boot:test-run` starts the backend with a throw-away PostgreSQL container managed by Testcontainers.

## Tests and quality checks

```bash
cd backend
./mvnw verify          # unit tests (*Test) + integration tests on real PostgreSQL (*IT, needs Docker)

cd frontend
npm run lint           # ESLint (angular-eslint)
npm test               # Jest unit tests
npm run build          # production build
```

## Project structure

```
backend/   Spring Boot API, package by feature under dev.ordonselli.raidboard
  auth/      register, login, refresh, logout, security configuration
  user/      user entity and profile endpoint
  group/     groups, memberships, roles, invite codes, per-group authorization (GroupSecurity)
  game/      RAWG client and per-group game catalog
  audit/     audit log written in the same transaction as each change
  common/    error handling (ProblemDetail), rate limiting, validation, OpenAPI, cache, clock
frontend/  Angular app
  core/      auth (service, interceptor, guards), HTTP error handling, i18n, layout
  shared/    reusable validators and components (empty state, confirm dialog)
  features/  one folder per feature, lazy-loaded (auth, home, groups, games)
```

## Security design

- **Access token**: short-lived JWT (15 min, HS256) sent in the `Authorization: Bearer` header and validated by Spring Security's OAuth2 resource server. The frontend keeps it in memory only, never in `localStorage`.
- **Refresh token**: a random 256-bit value in an `HttpOnly`, `Secure`, `SameSite=Strict` cookie, valid only for `/api/auth`. The database stores only its SHA-256 hash.
- **Rotation and reuse detection**: every refresh revokes the old token and issues a new one. If a revoked token is presented again, it was stolen and replayed, so all of the user's sessions are revoked.
- **Errors**: every error is an RFC 9457 `ProblemDetail` JSON body, including 401 and 403 responses raised by the security filters.
- **Passwords**: hashed with BCrypt, between 8 and 72 characters (BCrypt ignores anything after 72 bytes).
- **No user enumeration through login timing**: login always runs one BCrypt comparison, against a dummy hash created at startup with the same encoder when the email is unknown. Both cases take the same time and return the same "Invalid email or password" message.
- **Rate limiting** (Bucket4j, token bucket): `POST /api/auth/login` allows 5 attempts per minute per IP + email, 20 per minute per IP, and 20 per 15 minutes per account whatever the IP (against distributed attacks on one account); `POST /api/auth/refresh` allows 20 per minute per IP. IPv6 clients are grouped by /64 network, since one client usually controls a whole /64. Going over the limit returns `429 Too Many Requests` with a `ProblemDetail` body and a `Retry-After` header. The limits are configurable (`raidboard.rate-limit.*`, see `.env.example`).
  - The buckets live **in memory**, in a bounded Caffeine cache, so each backend instance counts on its own, and a flood of distinct keys can push old buckets out of the cache. A production deployment would move this to a shared store (Redis via Bucket4j's distributed backends) or to the API gateway / ingress.
  - The per-account limit is deliberately looser than the per-IP ones: whoever can trigger it can also block the real user from logging in for a while. Strict lockouts trade brute-force protection for that denial-of-service risk.
  - The client IP comes from `X-Forwarded-For`, which Spring trusts only when it's set by a proxy on a private network. nginx **overwrites** that header with the real client address, so a client can't fake a new IP on every request. Locally the backend port is published on `127.0.0.1` only. In production the backend must not be publicly reachable, and `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` should list only the real edge proxy (e.g. Azure ingress), which is the one setting the header.
- **Input bounds**: passwords are limited in **bytes** as well as characters (`@MaxUtf8Bytes(72)`), because BCrypt rejects anything over 72 bytes and `é` is one character but two bytes. Login fields have maximum sizes, because the email becomes part of a rate-limit key held in memory.

### Group access rules

Every group-scoped endpoint is protected with `@PreAuthorize("@groupSecurity.hasRole(#groupId, '...')")`:

| Action | Member | Officer | Owner |
|---|:---:|:---:|:---:|
| See the group, its members and games | ✓ | ✓ | ✓ |
| Edit name and description, add/remove games | | ✓ | ✓ |
| See the invite code | | ✓ | ✓ |
| Change roles, remove members, regenerate the invite code, set the Discord webhook, delete the group | | | ✓ |

- **Non-members get 404, not 403**: the API doesn't even confirm that the group exists. Members without the required role get 403.
- Services still load the caller's membership, and every group-owned lookup filters by group id (`findByIdAndGroupId`), so an id from another group is never found even if a check were forgotten.
- The whole matrix is covered by `GroupAccessIT` (every endpoint × outsider, member, officer, owner).
- The Discord webhook URL is a secret: only the owner can read it, it never appears in the audit log, and only `https://discord.com/api/webhooks/...` URLs are accepted, so the server can't be pointed at internal addresses (SSRF).
- The owner can't leave the group or change their own role; ownership transfer isn't supported yet.

### Known trade-off: registration reveals whether an email exists

`POST /api/auth/register` answers `409 Conflict` when the email is already registered, so someone could check whether an address has an account. This is a deliberate choice. Hiding it properly would mean an email-verification flow: always answer "check your inbox" and send either a confirmation link or an "you already have an account" email. That costs extra work and makes signing up clumsier, which isn't justified for this app's data. The impact is limited by the fact that you can only *check* addresses, not list them, and the endpoint can be rate-limited the same way as login if it ever becomes a problem.

## Configuration

Everything that changes between environments comes from environment variables. See [`.env.example`](.env.example). Secrets have no default value, and the backend refuses to start if `JWT_SECRET` is missing or shorter than 32 characters.

## Roadmap

- [x] **1. Foundation**: monorepo, database schema (Flyway), authentication with JWT + refresh-token rotation, error handling, Swagger, Angular shell with login and register
- [x] **2. Groups and games**: group CRUD, invite codes, member roles, audit log, RAWG game catalog
- [ ] **3. Events and sign-ups**: slots, waitlist promotion, concurrency-safe sign-ups, recurrence, calendar
- [ ] **4. Real-time and notifications**: WebSocket updates, reminder emails, Discord webhooks
- [ ] **5. Dashboard and polish**: statistics, audit log page, responsive layout
- [ ] **6. Quality and delivery**: Playwright e2e, GitHub Actions, Terraform for Azure, demo data

### Backlog (Milestone 6)

Tracked improvements that are not bugs, deliberately left for later:

- **"Recognized device" cookie**: after a successful login, set a long-lived signed cookie. Logins from a recognized device skip the per-account rate limit, so an attacker who hits it can't lock the real user out. Today the lockout lasts at most 15 minutes (see [Security design](#security-design)).
- **Shared rate-limit store**: move Bucket4j buckets to Redis (or to the gateway), so limits hold across instances and can't be reset by evicting entries from the in-memory cache.
