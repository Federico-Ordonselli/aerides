# Raidboard

Raidboard is a multi-tenant web app where gaming groups (guilds, clans, tabletop RPG tables) organize sessions: events with role-based slots, sign-ups, automatic waitlists, reminders and attendance stats.

Under the hood it is a **resource-booking business application**: roles, approvals, notifications, audit log and reporting.

> 🚧 Work in progress, built milestone by milestone. See [Roadmap](#roadmap).

## Tech stack

| Layer    | Technologies |
|----------|--------------|
| Backend  | Java 21, Spring Boot 4.1, Spring Security (JWT resource server), Spring Data JPA / Hibernate 7, PostgreSQL 17, Flyway, MapStruct, springdoc-openapi |
| Frontend | Angular 22 (standalone components, signals, zoneless), PrimeNG, ngx-translate (IT/EN), Jest |
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
  common/    error handling (ProblemDetail), OpenAPI, clock, shared security helpers
frontend/  Angular app
  core/      auth (service, interceptor, guards), HTTP error handling, i18n, layout
  shared/    reusable validators and components
  features/  one folder per feature, lazy-loaded (auth, home, ...)
```

## Security design

- **Access token**: short-lived JWT (15 min, HS256) sent in the `Authorization: Bearer` header and validated by Spring Security's OAuth2 resource server. The frontend keeps it in memory only, never in `localStorage`.
- **Refresh token**: a random 256-bit value in an `HttpOnly`, `Secure`, `SameSite=Strict` cookie, valid only for `/api/auth`. The database stores only its SHA-256 hash.
- **Rotation and reuse detection**: every refresh revokes the old token and issues a new one. If a revoked token is presented again, it was stolen and replayed, so all of the user's sessions are revoked.
- **Errors**: every error is an RFC 9457 `ProblemDetail` JSON body, including 401 and 403 responses raised by the security filters.
- **Passwords**: hashed with BCrypt, between 8 and 72 characters (BCrypt ignores anything after 72 bytes).

## Configuration

Everything that changes between environments comes from environment variables. See [`.env.example`](.env.example). Secrets have no default value, and the backend refuses to start if `JWT_SECRET` is missing or shorter than 32 characters.

## Roadmap

- [x] **1. Foundation**: monorepo, database schema (Flyway), authentication with JWT + refresh-token rotation, error handling, Swagger, Angular shell with login and register
- [ ] **2. Groups and games**: group CRUD, invite codes, member roles, audit log, RAWG game catalog
- [ ] **3. Events and sign-ups**: slots, waitlist promotion, concurrency-safe sign-ups, recurrence, calendar
- [ ] **4. Real-time and notifications**: WebSocket updates, reminder emails, Discord webhooks
- [ ] **5. Dashboard and polish**: statistics, audit log page, responsive layout
- [ ] **6. Quality and delivery**: Playwright e2e, GitHub Actions, Terraform for Azure, demo data
