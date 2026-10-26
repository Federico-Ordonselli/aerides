# Raidboard — Specification

Raidboard is a multi-tenant web app where gaming groups (guilds, clans, tabletop RPG tables) organize sessions: events with role-based slots, sign-ups, automatic waitlists, reminders and attendance stats. Architecturally it is a **resource-booking business application** with roles, approvals, notifications, audit log and reporting.

This document is the reference for scope and rules. The README describes what is built and how to run it.

## Tech stack

**Backend** (`/backend`)
- Java 21, **Spring Boot 4.1**, Maven (wrapper). *Originally planned on Boot 3; moved to 4.1 because 3.x is out of open-source support.*
- Spring Web, Spring Security with JWT (short-lived access token + rotating refresh token stored hashed), method security with `@PreAuthorize` for per-group roles
- Spring Data JPA / Hibernate, PostgreSQL, Flyway migrations (no `ddl-auto=update`)
- Bean Validation, MapStruct, errors as `ProblemDetail` via `@RestControllerAdvice`
- Pagination and dynamic filtering (Specifications)
- springdoc-openapi (Swagger UI)
- Spring WebSocket with STOMP for live sign-up updates
- `@Scheduled` jobs + Spring Mail (Mailpit in local dev), Thymeleaf email templates (IT/EN)
- Spring Boot Actuator, Bucket4j rate limiting on auth endpoints
- Tests: JUnit 5, Mockito, Testcontainers (PostgreSQL), WireMock
- Package by feature under `dev.ordonselli.raidboard`: `auth`, `user`, `group`, `game`, `event`, `signup`, `notification`, `realtime`, `stats`, `audit`, `common`

**Frontend** (`/frontend`)
- Angular (latest stable): standalone components, signals, new control flow, zoneless, no NgModules
- Reactive forms with custom validators
- HTTP interceptors (JWT, refresh on 401, global error handling), functional route guards for roles, lazy-loaded feature routes
- RxJS where it fits (debounced search, WebSocket stream)
- **Angular Material** for UI components. *PrimeNG was the original choice; dropped because PrimeNG 22 requires a commercial/community license key and shows a license banner without it.*
- **FullCalendar** (MIT packages) for the calendar view, **Chart.js via ng2-charts** for statistics
- i18n: Italian and English (ngx-translate, runtime switch)
- Tests: Jest for unit tests, Playwright for end-to-end flows
- Structure: `core/` (auth, interceptors, guards, layout), `shared/`, `features/`

**DevOps**
- `docker-compose.yml`: backend, frontend (nginx), PostgreSQL, Mailpit — `docker compose up` starts everything
- GitHub Actions: backend build + tests (with Testcontainers), frontend lint + tests + build, Docker images
- `/infra`: Terraform for Azure (Container Apps + Azure Database for PostgreSQL Flexible Server) — written, never applied
- Configuration via environment variables, `.env.example`, no secrets in the repository

## Domain model

- **User**: id, email (unique), passwordHash (BCrypt), displayName, avatarUrl, locale, createdAt
- **Group**: id, name, slug, description, inviteCode, discordWebhookUrl (nullable), createdAt
- **Membership**: user, group, role (OWNER | OFFICER | MEMBER), joinedAt; unique (user, group)
- **Game**: group, rawgId, name, slug, coverUrl (cached locally from the RAWG API)
- **Event**: group, game, title, description, startsAt, durationMinutes, status (DRAFT | PUBLISHED | CANCELLED | COMPLETED), requiresApproval, withdrawDeadlineHours, recurrenceRule (nullable, weekly only), createdBy, `@Version`
- **EventSlot**: event, roleName, capacity
- **Signup**: event, slot, user, status (CONFIRMED | WAITLISTED | PENDING | REJECTED | WITHDRAWN), position, createdAt; at most one active signup per user per event
- **Notification**: user, type, payload, status, sentAt
- **AuditEntry**: group, actor, action, entityType, entityId, details (JSONB), createdAt
- **RefreshToken**: user, tokenHash, expiresAt, revoked

## Business rules

1. Only OWNER and OFFICER can create, edit or cancel events. Only OWNER can change member roles or remove members.
2. Signing up to a full slot puts the user on the waitlist (FIFO by `position`).
3. When a confirmed user withdraws, the first waitlisted user of that slot is promoted **in the same transaction**, gets an email, and all clients viewing the event get a WebSocket update.
4. Concurrent sign-ups for the last seat must never overbook. Use a pessimistic lock on the slot row (or optimistic locking with retry), proven by an integration test that fires parallel requests.
5. Withdrawals are rejected after `startsAt - withdrawDeadlineHours`.
6. If `requiresApproval` is true, sign-ups start as PENDING until an OFFICER approves or rejects them.
7. A scheduled job sends reminder emails 24h before PUBLISHED events, exactly once per user per event.
8. Every create, update or delete on groups, events and memberships writes an AuditEntry.
9. If the group has a Discord webhook, post a message when an event is published and when it is 1 seat from full. Failures must not break the main flow.
10. Users must never read or modify data of groups they don't belong to, with tests proving it.

## Milestones

1. **Foundation** ✅ — monorepo, Maven and Angular skeletons, docker-compose, Flyway V1 with all tables, auth (register, login, refresh, logout), security config, global error handling, Swagger. Frontend: login, register, auth service with signals, interceptor, guard, protected home. Tests for auth. *Added: rate limiting with Bucket4j, login timing protection.*
2. **Groups and games** ✅ — group CRUD, invite code join, membership roles and permissions, audit entries, RAWG client (API key from env, timeouts, graceful failure), per-group game catalog. Frontend: group list, group detail with members and roles, game search with debounce.
3. **Events and sign-ups** ✅ — event CRUD with slots and weekly recurrence, sign-up / withdraw / approve / reject, waitlist promotion, concurrency handling and its integration test, paginated and filterable event list. Frontend: event calendar and list, event form with custom validators (future date, slot capacities > 0), event detail with sign-up actions.
4. **Real-time and notifications** ✅ — STOMP WebSocket topic per event, reminder scheduler, emails (Thymeleaf, IT/EN), Discord webhook. Frontend: live-updating sign-up list, toast notifications.
5. **Dashboard and polish** — stats endpoints (attendance per member, events per month, most-unfilled roles), audit log page for OWNER/OFFICER, i18n IT/EN, responsive layout, empty and error states.
6. **Quality and delivery** — Playwright e2e for the main flow (register → create group → create event → sign up from a second user → waitlist promotion), GitHub Actions workflows, Terraform for Azure, demo data seeder (dev profile only), final README with architecture diagram (Mermaid), screenshot placeholders, run instructions and demo credentials. Plus the backlog items tracked in the README.
