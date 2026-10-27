# Demo image for Render: ONE container with the Angular app served by Spring Boot (profile "demo").
# Build context is the repository root: docker build -f deploy/render/Dockerfile .
# The regular images (backend/Dockerfile, frontend/Dockerfile with nginx) are unchanged.

# --- Stage 1: compile the Angular app ---
FROM node:24-alpine AS web
WORKDIR /workspace
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ .
RUN npm run build

# --- Stage 2: package the jar, with the Angular build inside classpath:/static ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY backend/mvnw backend/pom.xml ./
COPY backend/.mvn .mvn
RUN ./mvnw -B -q dependency:go-offline
COPY backend/src src
COPY --from=web /workspace/dist/frontend/browser src/main/resources/static
RUN ./mvnw -B -q package -DskipTests
# Extracted layout: plain jars on disk instead of jars nested in the fat jar, faster to open and needed by CDS
# (renamed first: the thin jar keeps the source's name, and app.jar doesn't change with the version)
RUN mv target/aerides-*.jar app.jar && java -Djarmode=tools -jar app.jar extract --destination extracted

# --- Stage 3: runtime, only the JRE and the application ---
# The free instance has 0.1 CPU, so a cold start is CPU-bound (loading and verifying classes).
# Measured locally with the same limits (512 MB, 0.1 CPU), from `docker run` to the first login + API call + page:
# 156 s with the fat jar, 43 s with this stage. The three steps below, and what each one costs, are in README.md.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S aerides && adduser -S aerides -G aerides
USER aerides
# 1. The extracted layout from the build stage. Owned by aerides already: a chown in a later layer would copy every file
COPY --from=build --chown=aerides:aerides /workspace/extracted extracted
# 2. AppCDS training run, with the same JRE that runs the app (the archive only works with the JVM that wrote it): start the context, stop right after the refresh and dump the loaded classes to app.jsa.
#    There is no database at build time, so nothing may connect during the refresh: Flyway is off, Hibernate
#    gets the dialect instead of reading JDBC metadata, and the runners (DemoDataSeeder) don't run before the exit.
#    Paths are absolute: the archive records the classpath and is only used when it matches at runtime.
RUN java -XX:ArchiveClassesAtExit=/app/extracted/app.jsa \
      -XX:+UseSerialGC -XX:MaxRAMPercentage=60 -XX:TieredStopAtLevel=1 \
      -Dspring.context.exit=onRefresh -Dspring.profiles.active=demo \
      -DDB_URL=jdbc:postgresql://localhost:5432/training -DDB_USERNAME=training -DDB_PASSWORD=training \
      -DJWT_SECRET=training-only-secret-not-used-anywhere-0123456789abcdef -DRENDER_EXTERNAL_URL=http://localhost \
      -Dspring.flyway.enabled=false -Dspring.jpa.hibernate.ddl-auto=none \
      -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
      -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
      -jar /app/extracted/app.jar
ENV SPRING_PROFILES_ACTIVE=demo
EXPOSE 8080
# The database comes in DB_URL (already a JDBC URL), DB_USERNAME and DB_PASSWORD, as everywhere else.
# The free instance has 512 MB: a smaller heap share leaves room for metaspace and threads,
# and the serial GC has the lowest memory overhead on a single CPU.
# 3. TieredStopAtLevel=1: only the C1 compiler, which compiles sooner and cheaper; C2's peak speed doesn't matter here.
# -Xshare:auto: if the archive doesn't match (different JVM or classpath) the JVM starts without it, slower but fine.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1", \
            "-XX:SharedArchiveFile=/app/extracted/app.jsa", "-Xshare:auto", "-jar", "/app/extracted/app.jar"]
