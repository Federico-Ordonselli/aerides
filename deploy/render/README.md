# Deploying the demo (Render + Neon)

The public demo is one Render web service (the image in this folder) and a free Neon PostgreSQL.
Both free tiers don't expire. The Render service sleeps after 15 idle minutes, Neon's compute after 5.

## 1. Neon: the database

1. Sign up at [console.neon.tech](https://console.neon.tech) and create a project:
   - **Postgres version**: 17
   - **Cloud provider and region**: AWS, **Europe (Frankfurt) `eu-central-1`**, next to Render's Frankfurt region
   - Keep the default database `neondb` and its owner role `neondb_owner`
2. Open **Connect**, keep branch `main`, database `neondb`, role `neondb_owner`, and **turn off "Connection pooling"**.
   The host must NOT contain `-pooler`: the pooler runs in transaction mode, which drops `SET lock_timeout`
   and Flyway's session lock.
3. From the connection string shown,
   `postgresql://neondb_owner:<OWNER_PASSWORD>@<HOST>/neondb?sslmode=require&channel_binding=require`,
   copy two parts: `<HOST>` (like `ep-cool-name-123456.eu-central-1.aws.neon.tech`) and `<OWNER_PASSWORD>`.
4. Generate a password for the application role (Neon wants at least 60 bits of entropy; this has 256):
   `openssl rand -base64 32`

Don't create the `aerides_app` role yourself: the backend creates it at its first startup.

## 2. Render: the web service

1. [dashboard.render.com](https://dashboard.render.com) > **New** > **Blueprint**, connect GitHub, pick this repository.
   Render reads `render.yaml` and creates the `aerides` web service (free, Frankfurt).
2. It asks for the variables marked `sync: false`:

   | Variable | Value |
   |----------|-------|
   | `DB_URL` | `jdbc:postgresql://<HOST>/neondb?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory` |
   | `DB_PASSWORD` | the password from `openssl rand` (for `aerides_app`) |
   | `SPRING_FLYWAY_PASSWORD` | `<OWNER_PASSWORD>` (for `neondb_owner`) |
   | `RAWG_API_KEY` | optional, empty is fine: game search answers 503 |

   Already in `render.yaml`: `DB_USERNAME=aerides_app`, `SPRING_FLYWAY_USER=neondb_owner`, `DB_HASH_APP_PASSWORD=false`
   (Neon refuses pre-hashed passwords), and a generated `JWT_SECRET`.
3. **Apply**. The first build takes several minutes. At startup Flyway migrates as `neondb_owner`, creates
   `aerides_app` with rows-only rights, and `DemoDataSeeder` adds the demo users.

`sslmode=verify-full` checks the certificate and the host name. `DefaultJavaSSLFactory` makes pgjdbc use the JRE's
CA store, which has Neon's root (ISRG Root X1, Let's Encrypt); without it pgjdbc would look for `~/.postgresql/root.crt`.

## 3. Check it

- Open the `https://aerides-….onrender.com` URL and log in as `giulia@example.com` / `aerides-demo`.
- In Neon's **SQL Editor**: `SELECT usename, count(*) FROM pg_stat_activity WHERE datname = 'neondb' GROUP BY 1;`
  shows `aerides_app` holding the pool's connections.
- Client IP: add `LOGGING_LEVEL_DEV_ORDONSELLI_AERIDES_COMMON_WEB=DEBUG` in **Environment**, make one request, and the
  log line `... from <address>` should show your public IP (`curl https://ifconfig.me`). Then remove the variable.

## Startup time

The free instance has 0.1 CPU, so a cold start (after 15 idle minutes) is CPU-bound: the JVM spends it loading and
verifying classes, and the slowest single step is Spring Data checking each `@Query` through Hibernate's HQL parser.
Render logged `Started AeridesApplication in 257.693 seconds` with the plain fat jar, and `101.994 seconds` with this
image (2.5x faster). Render's 0.1 CPU is slower than the local simulation below, so its absolute numbers are higher.

The runtime stage of the `Dockerfile` does three things. Measured locally with the same limits
(`docker run --memory=512m --cpus=0.1`), from `docker run` to the first login + API call + page:

| Image | First response |
|-------|---------------:|
| fat jar (before) | 156 s |
| + `-XX:TieredStopAtLevel=1` (C1 compiler only) | 84 s |
| + extracted jar layout (`-Djarmode=tools extract`) | 62 s |
| + AppCDS archive from a training run | 43 s |

What it costs: the image is ~150 MB bigger (the 118 MB archive, plus the jars stored unpacked), the build takes ~1 minute longer (the training run),
and C1-only code has a lower peak speed, which a demo with a handful of users never reaches.
"Started in" is not the whole wait: Render routes traffic only once readiness is UP, 10-20 s later.

Tried and dropped:
- `spring.main.lazy-initialization` and lazy JPA repositories: "Started in" goes down, but the first request then
  creates the same beans, so the first response took the same 45 s.
- Spring AOT: 53 s, and it freezes `@Conditional` decisions at build time (the active profile, Flyway on/off,
  the role provisioner), so the same image could no longer adapt to its environment. AOT + CDS would also need a
  database during the build.

## Rotating passwords

Rotating a password: change it in Render (and, for the owner, in Neon first) and redeploy. The application role's
password is set again at every startup.
