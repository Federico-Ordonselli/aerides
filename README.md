# Aerides

Aerides is a multi-tenant web app where gaming groups (guilds, clans, tabletop RPG tables) organize sessions: events with role-based slots, sign-ups, automatic waitlists, reminders and attendance stats.

Under the hood it is a **resource-booking business application**: roles, approvals, notifications, audit log and reporting.

**About the name**: the Tower of the Winds in Athens, the *Aerides*, is a public landmark that showed the winds and the time of day: the place to know when to gather.

[![Backend](https://github.com/Federico-Ordonselli/aerides/actions/workflows/backend.yml/badge.svg)](https://github.com/Federico-Ordonselli/aerides/actions/workflows/backend.yml)
[![Frontend](https://github.com/Federico-Ordonselli/aerides/actions/workflows/frontend.yml/badge.svg)](https://github.com/Federico-Ordonselli/aerides/actions/workflows/frontend.yml)
[![E2E](https://github.com/Federico-Ordonselli/aerides/actions/workflows/e2e.yml/badge.svg)](https://github.com/Federico-Ordonselli/aerides/actions/workflows/e2e.yml)
[![Docker images](https://github.com/Federico-Ordonselli/aerides/actions/workflows/docker.yml/badge.svg)](https://github.com/Federico-Ordonselli/aerides/actions/workflows/docker.yml)
[![Infrastructure](https://github.com/Federico-Ordonselli/aerides/actions/workflows/infra.yml/badge.svg)](https://github.com/Federico-Ordonselli/aerides/actions/workflows/infra.yml)
[![Secrets](https://github.com/Federico-Ordonselli/aerides/actions/workflows/secrets.yml/badge.svg)](https://github.com/Federico-Ordonselli/aerides/actions/workflows/secrets.yml)

> Full scope, domain model and the 10 business rules: [docs/SPEC.md](docs/SPEC.md). Built in six milestones, see [Roadmap](#roadmap).

## Tech stack

| Layer    | Technologies |
|----------|--------------|
| Backend  | Java 21, Spring Boot 4.1, Spring Security (JWT resource server), Spring Data JPA / Hibernate 7, PostgreSQL 17, Flyway, MapStruct, Bucket4j, springdoc-openapi |
| Frontend | Angular 22 (standalone components, signals, zoneless), Angular Material 3, RxJS, STOMP (rx-stomp), FullCalendar, Chart.js via ng2-charts, ngx-translate (IT/EN) |
| Testing  | JUnit 5, Mockito, Testcontainers, MockMvc, WireMock, GreenMail, Awaitility, Jest, Playwright |
| DevOps   | Docker, Docker Compose, nginx, GitHub Actions, GitHub Container Registry, Terraform (Azure Container Apps + PostgreSQL Flexible Server), Render + Neon (demo), Mailpit |

## Architecture

```mermaid
flowchart LR
    browser([Browser<br/>Angular SPA])
    subgraph stack[docker compose / Azure Container Apps]
        nginx[nginx<br/>static files + reverse proxy]
        subgraph backend[Spring Boot backend]
            api[REST API<br/>JWT, @PreAuthorize per group]
            ws[STOMP broker<br/>/topic, /user/queue]
            jobs[Scheduled jobs<br/>reminders, email retries,<br/>expired sockets]
            listeners[After-commit listeners<br/>email, Discord, WebSocket]
        end
        db[(PostgreSQL<br/>Flyway)]
        mail[SMTP<br/>Mailpit in dev]
    end
    rawg[[RAWG API]]
    discord[[Discord webhook]]

    browser -->|HTTPS /api| nginx
    browser <-->|WebSocket /ws| nginx
    nginx --> api
    nginx <--> ws
    api --> db
    api -. domain events .-> listeners
    listeners --> ws
    listeners --> mail
    listeners --> discord
    jobs --> db
    jobs --> mail
    api -->|game search, cached| rawg
```

- **One origin**: nginx serves the Angular build and forwards `/api` and `/ws` to the backend, so the browser never makes a cross-origin call and the refresh cookie can be `SameSite=Strict`. The backend is never exposed directly.
- **Package by feature** in the backend (`auth`, `group`, `event`, `signup`, ...), each with its controller, service, repository and DTOs. Entities never leave the service layer.
- **Side effects after the commit**: services publish domain events in their transaction; WebSocket messages, emails and Discord posts are sent by listeners only once the data is committed (see [Real-time and notifications](#real-time-and-notifications)).
- **PostgreSQL does the hard guarantees**: row locks against overbooking, partial unique indexes, `ON CONFLICT` for exactly-once emails. Each one has an integration test that fails without it.

## Screenshots

> _Placeholders: the images go in [`docs/screenshots/`](docs/screenshots/) (start the stack with demo data, see [Local development](#local-development))._

| | |
|---|---|
| **Event detail**, live sign-ups and waitlist: `docs/screenshots/event-detail.png` | **Calendar** of the group's events: `docs/screenshots/calendar.png` |
| **Statistics**: attendance, events per month, unfilled roles: `docs/screenshots/stats.png` | **Event form** with slots and weekly recurrence: `docs/screenshots/event-form.png` |

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

## Live demo

> **URL: [https://aerides.onrender.com/](https://aerides.onrender.com/)** · demo users `giulia@example.com` (owner), `marco@example.com` (officer), `luca@example.com` (member), password `aerides-demo`. All six accounts and what each one tests: [docs/demo-accounts.md](docs/demo-accounts.md).
> ⏳ **Cold start**: the free instance sleeps after 15 minutes of inactivity, so the first request can take about a minute. After that it's fast.
> Try it: log in as Luca in one browser and open "Raid: Vault of Glass", then as Giulia in another browser withdraw from the Tank slot: Luca is promoted and sees it live.

The demo runs on [Render](https://render.com)'s free tier, from [`render.yaml`](render.yaml). It's a quick, free way to show the app; the production-like deployment is the [Azure Terraform configuration](#deployment-on-azure-terraform).

- **One service instead of two**: free Render services can't receive private-network traffic, so the usual nginx-in-front-of-the-backend layout would need two public services. Instead, [`deploy/render/Dockerfile`](deploy/render/Dockerfile) builds the Angular app into the backend's `static/` folder, and the `demo` Spring profile serves it (`SinglePageAppConfig`). App, API and WebSocket keep one origin, so the `SameSite=Strict` refresh cookie and `/ws` work exactly as behind nginx. Static files and Angular routes get their own public security chain (GET only, outside `/api`, `/ws`, `/actuator`); everything else still goes through the normal default-deny chain.
- **No emails**: the free tier blocks SMTP ports, and a public demo shouldn't email addresses people type in. In the `demo` profile `LoggingMailSender` writes a log line instead of sending; templates, the `notifications` table and the exactly-once reminder logic run as usual.
- **Database**: a free [Neon](https://neon.com) PostgreSQL in Frankfurt, because Render's free database is deleted after 30 days and the link has to keep working. Same least-privilege setup as on Azure: Flyway migrates as Neon's owner role, the connection pool uses the rows-only `aerides_app` role. It uses the direct endpoint (Neon's pooler is in transaction mode, which would drop the session's `lock_timeout` and Flyway's lock) and `sslmode=verify-full`. Setup steps: [deploy/render/README.md](deploy/render/README.md).
- **Demo data**: `DemoDataSeeder` (profiles `dev` and `demo`) creates the users, group and events above at the first startup, through the real services, so waitlist, approvals and audit log follow the business rules. Three past raids are moved into the past with SQL and completed, so the statistics page has numbers. It runs in one transaction, and only if the demo users don't exist yet.
- Test the same image locally: `docker build -f deploy/render/Dockerfile -t aerides-demo .`

## Local development

Requirements: JDK 21, Node 24, Docker (for PostgreSQL and the integration tests).

```bash
# 1. Infrastructure only
docker compose up -d postgres mailpit

# 2. Backend on :8080. The "dev" profile provides a development JWT secret and,
#    on an empty database, creates the demo data (users above, password aerides-demo)
cd backend
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run

# 3. Frontend on :4200, proxying /api and /ws (WebSocket) to the backend
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

# End-to-end, against the running stack (docker compose up)
cd e2e
npm ci && npx playwright install chromium
npx playwright test    # register → group → event → waitlist → live promotion + email
```

| Suite | What it covers |
|-------|----------------|
| Backend unit (`*Test`, 123) | services with mocked repositories: business rules, permissions, edge cases |
| Backend integration (`*IT`, 187) | the real API through the security filters, on PostgreSQL (Testcontainers): access matrix, concurrency, locks, emails (GreenMail), Discord (WireMock), WebSocket clients |
| Frontend (Jest) | components, services, validators, interceptors, i18n keys |
| End-to-end (Playwright) | the main flow with two users in two browsers, against the whole stack |

The tests that guard a rule (no overbooking, exactly-once emails, lock timeout, access checks) were each checked by removing the guard and watching the test fail.

### Continuous integration

Six GitHub Actions workflows ([`.github/workflows`](.github/workflows)), each triggered only by the folders it covers (secret scanning covers everything):

- **Backend**: `./mvnw verify` with Testcontainers (Docker is available on the runners).
- **Frontend**: lint, Jest, production build (fails over the bundle budget).
- **E2E**: `docker compose up --wait`, then Playwright with Chromium; traces and screenshots are uploaded when it fails.
- **Docker images**: built on every pull request, pushed to GitHub Container Registry from `main`, tagged `latest` and `sha-<commit>`.
- **Infrastructure**: `terraform fmt -check` and `terraform validate`.
- **Secrets**: gitleaks on the commits of every push and pull request, the whole history on demand. Reviewed false positives are listed, with the reason, in [`.gitleaksignore`](.gitleaksignore).

## Deployment on Azure (Terraform)

[`infra/`](infra/) describes a production-like deployment: Azure Container Apps (nginx public, backend on an internal ingress, one replica because broker and rate limits are in memory), PostgreSQL 17 Flexible Server, Log Analytics, secrets as Container Apps secrets. It is **written and validated in CI, never applied**. Design choices, the known gap (private networking) and how to run it are in [infra/README.md](infra/README.md).

## Project structure

```
backend/   Spring Boot API, package by feature under dev.ordonselli.aerides
  auth/      register, login, refresh, logout, security configuration
  user/      user entity and profile endpoint
  group/     groups, memberships, roles, invite codes, per-group authorization (GroupSecurity)
  game/      RAWG client and per-group game catalog
  event/     events, slots, weekly series, paginated list with JPA Specifications
  signup/    sign-ups, waitlist promotion, approvals (row locks against overbooking)
  audit/     audit log written in the same transaction as each change, paginated audit page
  stats/     group statistics: plain SQL aggregations through JdbcClient
  realtime/  STOMP over WebSocket: authentication on CONNECT, authorization on SUBSCRIBE, token expiry, live messages
  notification/  emails (Thymeleaf, IT/EN), reminder and retry jobs, Discord webhook
  demo/      demo data seeder (dev and demo profiles), Render demo configuration
  common/    error handling (ProblemDetail), rate limiting, validation, OpenAPI, cache, clock
frontend/  Angular app
  core/      auth (service, interceptor, guards), HTTP error handling, i18n, layout, realtime (STOMP client)
  shared/    reusable validators, components (empty state, error state, confirm dialog) and page models
  features/  one folder per feature, lazy-loaded (auth, home, groups, games, events, stats, audit)
e2e/       Playwright end-to-end tests, run against the whole stack
infra/     Terraform for Azure (written and validated, never applied)
deploy/    Render demo image (one container: Angular served by Spring Boot)
.github/   CI workflows
```

## Security design

- **Access token**: short-lived JWT (15 min, HS256) sent in the `Authorization: Bearer` header and validated by Spring Security's OAuth2 resource server. The frontend keeps it in memory only, never in `localStorage`.
- **Refresh token**: a random 256-bit value in an `HttpOnly`, `Secure`, `SameSite=Strict` cookie, valid only for `/api/auth`. The database stores only its SHA-256 hash.
- **Rotation and reuse detection**: every refresh revokes the old token and issues a new one. If a revoked token is presented again, it was stolen and replayed, so all of the user's sessions are revoked.
- **Errors**: every error is an RFC 9457 `ProblemDetail` JSON body, including 401 and 403 responses raised by the security filters.
- **Passwords**: hashed with BCrypt, between 8 and 72 characters (BCrypt ignores anything after 72 bytes).
- **No user enumeration through login timing**: login always runs one BCrypt comparison, against a dummy hash created at startup with the same encoder when the email is unknown. Both cases take the same time and return the same "Invalid email or password" message.
- **Rate limiting** (Bucket4j, token bucket): `POST /api/auth/login` allows 5 attempts per minute per IP + email, 20 per minute per IP, and 20 per 15 minutes per account whatever the IP (against distributed attacks on one account); `POST /api/auth/refresh` allows 20 per minute per IP. IPv6 clients are grouped by /64 network, since one client usually controls a whole /64. Going over the limit returns `429 Too Many Requests` with a `ProblemDetail` body and a `Retry-After` header. The limits are configurable (`aerides.rate-limit.*`, see `.env.example`).
  - The buckets live **in memory**, in a bounded Caffeine cache, so each backend instance counts on its own, and a flood of distinct keys can push old buckets out of the cache. A production deployment would move this to a shared store (Redis via Bucket4j's distributed backends) or to the API gateway / ingress.
  - The per-account limit is deliberately looser than the per-IP ones: whoever can trigger it can also block the real user from logging in for a while. Strict lockouts trade brute-force protection for that denial-of-service risk.
  - The client IP comes from `X-Forwarded-For`, which Spring trusts only when it's set by a proxy on a private network. nginx **overwrites** that header with the real client address, so a client can't fake a new IP on every request. Locally the backend port is published on `127.0.0.1` only. In production the backend must not be publicly reachable, and `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` should list only the real edge proxy (e.g. Azure ingress), which is the one setting the header.
- **WebSocket**: browsers can't send an `Authorization` header with the WebSocket handshake, so `/ws` itself is public and the checks happen on the STOMP frames. `CONNECT` must carry a valid access token (same validation as the REST API); `SUBSCRIBE` is allowed only to the user's own notification queue and to events they could read over REST (members of the group, drafts for officers only); `SEND` is never allowed, every change goes through the REST API. A refused frame gets an `ERROR` and the connection is closed. The handshake is limited to the configured origins.
  - **Losing access closes the socket**: a subscription is authorized once, on `SUBSCRIBE`. When a member is removed, leaves, or is demoted from officer to member (so they can't read drafts any more), `MembershipService` publishes a `GroupAccessRevokedEvent`. After the commit, `RevokedAccessListener` sends an `ERROR` ("Access revoked") to every open session of that user, and Spring closes them; closing a session removes all its subscriptions from the broker. The client reconnects on its own and every subscription is authorized again with the current role. Closing the whole session instead of single subscriptions is simpler and can't miss one; the cost is a short reconnect in the user's other groups. The event page reloads on "Access revoked": the REST call fails, so it stops watching the event and doesn't retry a subscription the server would refuse. Deleting a group needs nothing: its events are deleted too, so their topics never receive another message. Covered by `WebSocketIT` (remove, leave, demote).
  - **Sessions expire with their token**: the token is checked on `CONNECT`, and its `exp` is kept per session (`TokenExpiryRegistry`). After it, `SUBSCRIBE` is refused with "Authentication required", and a job (`ExpiredSessionSweeper`, every minute) closes the sessions that outlived their token. The frontend already treats that error as "refresh and reconnect", so a logged-in user just gets a new session, while a socket opened with a stolen token, or by a session revoked elsewhere, stops receiving messages at most a minute after the token expires. The registry is in memory, like the broker (one backend instance). Covered by `WebSocketIT` and `StompAuthInterceptorTest`.
- **Input bounds**: passwords are limited in **bytes** as well as characters (`@MaxUtf8Bytes(72)`), because BCrypt rejects anything over 72 bytes and `é` is one character but two bytes. Login fields have maximum sizes, because the email becomes part of a rate-limit key held in memory.

### Group access rules

Every group-scoped endpoint is protected with `@PreAuthorize("@groupSecurity.hasRole(#groupId, '...')")`:

| Action | Member | Officer | Owner |
|---|:---:|:---:|:---:|
| See the group, its members and games | ✓ | ✓ | ✓ |
| Edit name and description, add/remove games | | ✓ | ✓ |
| See the invite code | | ✓ | ✓ |
| Change roles, remove members, regenerate the invite code, set the Discord webhook, delete the group | | | ✓ |
| See published events, sign up, withdraw | ✓ | ✓ | ✓ |
| See drafts; create, edit, publish, cancel, complete events; approve or reject sign-ups | | ✓ | ✓ |
| See the group statistics | ✓ | ✓ | ✓ |
| Read the audit log | | ✓ | ✓ |

- **Non-members get 404, not 403**: the API doesn't even confirm that the group exists. Members without the required role get 403.
- Services still load the caller's membership, and every group-owned lookup filters by group id (`findByIdAndGroupId`), so an id from another group is never found even if a check were forgotten.
- The whole matrix is covered by `GroupAccessIT` and `EventAccessIT` (every endpoint × outsider, member, officer, owner), plus tests that reach another group's event, slot, sign-up or game through this group's URL.
- The Discord webhook URL is a secret: only the owner can read it, it never appears in the audit log, and only `https://discord.com/api/webhooks/...` URLs are accepted, so the server can't be pointed at internal addresses (SSRF).
- The owner can't leave the group or change their own role; ownership transfer isn't supported yet.

### Known trade-off: registration reveals whether an email exists

`POST /api/auth/register` answers `409 Conflict` when the email is already registered, so someone could check whether an address has an account. This is a deliberate choice. Hiding it properly would mean an email-verification flow: always answer "check your inbox" and send either a confirmation link or an "you already have an account" email. That costs extra work and makes signing up clumsier, which isn't justified for this app's data. The impact is limited by the fact that you can only *check* addresses, not list them, and the endpoint can be rate-limited the same way as login if it ever becomes a problem.

## Events and sign-ups

- **No overbooking under concurrency** (business rule 4): a sign-up locks its slot row with `SELECT ... FOR UPDATE` (`@Lock(PESSIMISTIC_WRITE)`) before counting the confirmed seats, so parallel requests for the same slot run one after the other. `SignupConcurrencyIT` fires 20 parallel sign-ups at a 3-seat slot and checks that exactly 3 are confirmed and every position is unique; without the lock the same test ends with 10 confirmed. Withdraw, approve and capacity changes take the same lock, and the sign-up row is read only after it, so its status is current.
- **Lock timeout**: every database connection runs with `lock_timeout = 5s` (Hikari `connection-init-sql`, `DB_LOCK_TIMEOUT`). A transaction stuck while holding a slot lock no longer makes the requests behind it wait forever: they fail with `503` and `Retry-After: 1`, and since they were rolled back, retrying is safe. `SignupLockTimeoutIT` holds a slot lock from another thread and checks that the sign-up fails fast; without the timeout the same test waits for the lock and gets `201`.
- **One active sign-up per user per event** is enforced by a partial unique index in PostgreSQL, which also covers two parallel requests for *different* slots (they don't share a lock).
- **Members who leave or are removed** give up their sign-ups on upcoming published events, in the same transaction that deletes the membership, and their seats go to the waitlist. `MembershipService` publishes a `MembershipEndedEvent`, and `SignupService` handles it with a plain (synchronous) `@EventListener`, so the `group` package doesn't depend on `signup`. The withdrawal deadline doesn't apply (a non-member can't keep a seat), while sign-ups on events that already started stay, because they are attendance history.
- **Waitlist**: a full slot puts the user on the waitlist, in FIFO order by `position`. When a confirmed user withdraws, or an officer adds seats, the first waitlisted users are promoted in the same transaction, and a `SignupPromotedEvent` is published (see [Real-time and notifications](#real-time-and-notifications)).
- **Withdrawal deadline** (rule 5): confirmed users can't withdraw after `startsAt - withdrawDeadlineHours`. Waitlisted and pending users hold no seat, so they can always leave.
- **Editing safely**: the client sends back the `version` it loaded, and the update is refused with 409 if the event changed in the meantime. Every update also forces a version increment, so slot-only changes are covered too. Capacity can't go below the confirmed participants, and a slot with active sign-ups can't be removed.
- **Weekly series**: a series is stored as real events sharing a `series_id`, because sign-ups, reminders and statistics all need real rows. Weeks are added in the organizer's time zone, so "Thursday 21:00 in Rome" stays at 21:00 after the daylight saving change. Editing or cancelling acts on one occurrence.
- **Calendar**: FullCalendar (MIT packages only) is loaded with `@defer`, in its own chunk, only when the calendar view is opened; it asks the API only for the visible date range.

## Real-time and notifications

Services publish domain events (`EventChangedEvent`, `SignupPromotedEvent`, `EventPublishedEvent`, `EventAlmostFullEvent`) inside their transaction. Listeners react with `@TransactionalEventListener`, i.e. **only after the commit**: nobody is told about a change that was rolled back. Services don't know about WebSocket, email or Discord.

- **Live updates** (STOMP over WebSocket, in-memory broker): every change to an event or its sign-ups sends `{eventId, type}` on `/topic/events/{id}`, and the open pages reload the event over REST. The message carries no data on purpose: the event detail differs per user (their own sign-up, their permissions), so one shared copy wouldn't fit everyone, and REST stays the single source of truth. A promoted user also gets a personal message on `/user/queue/notifications`, shown as a toast.
- **Promotion email** (rule 3): sent `@Async` after the commit, so the user who withdrew doesn't wait for the SMTP server. Written in the recipient's language (Thymeleaf templates + `messages_*.properties`).
- **Reminders exactly once** (rule 7): a job runs every 5 minutes and emails the confirmed participants of published events starting within 24 hours. "Exactly once" is guaranteed by PostgreSQL, not by timing: each email first inserts a `notifications` row with `ON CONFLICT (user_id, dedup_key) DO NOTHING`, and only the run that inserted it sends the email. `NotificationIT` runs the job twice in parallel and checks there is one email; without the check it gets two.
- **Failed emails are retried** (`FailedNotificationRetrier`, every 10 minutes, 3 attempts in total). A retry first claims the row with one atomic `UPDATE ... WHERE status = 'FAILED'`, so two runs never retry the same email twice (`FailedNotificationRetryIT` runs two in parallel). An email is retried only while it is still useful: the event is published and hasn't started, and the user still has a confirmed seat; otherwise it becomes `ABANDONED`. The trade-off: if the mail server accepted the message but the reply timed out, the retry is a duplicate; a rare duplicate before the event is better than a reminder that never arrives.
- **Discord** (rule 9): if the group has a webhook, a message is posted when an event is published and when a sign-up leaves exactly one free seat. It runs in the background and every error is logged and swallowed, so Discord being down never breaks the main flow. Mentions are disabled (`allowed_mentions`), so an event called "@everyone" pings nobody, and the webhook URL (a secret) is never logged.
- **A bounded pool for `@Async`** (`AsyncConfig`): emails and Discord messages run on their own pool (`notify-*` threads, 2 to 4 threads, a queue of 100, set in `aerides.async.*`). Without it, Spring Boot's executor backs off because the WebSocket broker registers executors of its own, and Spring starts a new thread for every task. When the pool and queue are full, the request thread sends the message itself (slower, but nothing is lost; rejecting would turn a request that already committed into a 500). On shutdown, queued tasks get 20 seconds to finish. `AsyncConfigTest` runs the real listeners next to extra executors and checks the thread they run on.
- **Trade-offs**: the in-memory broker works for a single backend instance; with more, a broker relay (RabbitMQ) would be needed. Emails use one time zone for everyone (`APP_TIME_ZONE`), because users don't have their own yet.

## Statistics and audit log

- **`GET /api/groups/{id}/stats?from&to&zone`** (any member): attendance per member, events per month, most unfilled roles. Default period: the last 12 months; at most 36.
  - **Attendance** = confirmed seats on **completed** events ÷ completed events **since the member joined**. Nobody looks absent from raids held before they joined. There is no no-show tracking yet, so a confirmed seat on a completed event counts as present; withdrawals are shown next to it.
  - **Unfilled roles** = capacity − confirmed seats on completed events, grouped by role name ignoring case and spaces (role names are free text per event).
  - **Months follow the user's time zone** (`zone`, an IANA id the browser sends): an event at 00:30 on 1 November in Rome is still 31 October in UTC. Empty months are filled in by the service, so the chart axis has no gaps.
  - The queries are plain SQL with `JdbcClient` (`StatsRepository`), not JPA: they return numbers, not entities, and one `GROUP BY` is clearer and faster than loading rows to count them in Java. `StatsIT` runs them on PostgreSQL against a small data set whose every number is known.
- **`GET /api/groups/{id}/audit?action&page&size`** (officer and owner): the log, newest first. The client can choose page and size but not the order. Actor names are loaded with one query per page (no N+1); entries of a deleted user stay, without a name.
- **Frontend**: Chart.js is registered on the stats route, so it is downloaded only by whoever opens the page (its own ~160 kB chunk; the initial bundle didn't grow). The Material paginator labels are translated too (`TranslatedPaginatorIntl`), provided on the lazy routes for the same reason.
- **i18n check**: `translations.spec.ts` fails if Italian and English have different keys, or if a key written in the code (or built from an enum, like `audit.actions.*`) is missing.

## Configuration

Everything that changes between environments comes from environment variables. See [`.env.example`](.env.example). Secrets have no default value, and the backend refuses to start if `JWT_SECRET` is missing or shorter than 32 bytes (HS256's minimum), with a message that says what to do and never prints the value.

## Roadmap

- [x] **1. Foundation**: monorepo, database schema (Flyway), authentication with JWT + refresh-token rotation, error handling, Swagger, Angular shell with login and register
- [x] **2. Groups and games**: group CRUD, invite codes, member roles, audit log, RAWG game catalog
- [x] **3. Events and sign-ups**: slots, waitlist promotion, concurrency-safe sign-ups, recurrence, calendar
- [x] **4. Real-time and notifications**: WebSocket updates, reminder emails, Discord webhooks
- [x] **5. Dashboard and polish**: statistics, audit log page, i18n check, responsive layout, error states
- [x] **6. Quality and delivery**: Playwright e2e, GitHub Actions, Terraform for Azure, demo data seeder, backlog items (sign-ups of removed members, lock timeout, email retries, WebSocket token expiry)

### Future work

Improvements that are not bugs, deliberately left out. Milestone 6 closed the other backlog items: sign-ups of removed members, lock timeout, retry of failed emails, WebSocket sessions that expire with their token.

- **"Recognized device" cookie**: after a successful login, set a long-lived signed cookie. Logins from a recognized device skip the per-account rate limit, so an attacker who hits it can't lock the real user out. Today the lockout lasts at most 15 minutes (see [Security design](#security-design)). Left out because authentication was frozen after Milestone 1's reviews.
- **Refresh from several tabs at once** (next up): the access token lives in each tab's memory, but the refresh cookie is shared. Two tabs that refresh at the same moment (e.g. the browser restoring them at startup) send the same refresh token, so the second one looks like a replayed token, and reuse detection logs the user out on every device. The fix is frontend-only, so reuse detection stays strict: refreshes are serialized across tabs with the Web Locks API (`navigator.locks.request`), and a tab that waited reuses the new access token instead of presenting the rotated one. Browsers without Web Locks fall back to today's per-tab behaviour.
- **Shared rate-limit store**: move Bucket4j buckets to Redis (or to the gateway), so limits hold across instances and can't be reset by evicting entries from the in-memory cache. Needed only with more than one backend instance, together with a broker relay for WebSocket.
- **Azure: private network and a migration job**: the Terraform deployment (`infra/`) is not production-ready on two points. PostgreSQL has a public endpoint, which needs a virtual network to close. Flyway runs inside the backend, so the container holds the schema owner's password even though the connection pool uses a rows-only role; the migrations belong in a separate Container Apps job. Details in [infra/README.md](infra/README.md).
- **Edit a whole weekly series**: today editing and cancelling act on one occurrence at a time.
- **Per-user time zone**: emails write dates in `APP_TIME_ZONE`; users could store their own zone, like their language.
- **Real attendance**: let officers mark no-shows when completing an event; attendance would then count people who actually came, not confirmed seats.
- **Calendar styles out of the initial bundle**: FullCalendar's CSS (~30 kB) is a global stylesheet; it could be loaded together with the deferred calendar chunk.
