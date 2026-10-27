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

# --- Stage 3: runtime, only the JRE and the jar ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S raidboard && adduser -S raidboard -G raidboard
USER raidboard
COPY --from=build /workspace/target/raidboard-*.jar app.jar
ENV SPRING_PROFILES_ACTIVE=demo
EXPOSE 8080
# The database comes in DB_URL (already a JDBC URL), DB_USERNAME and DB_PASSWORD, as everywhere else.
# The free instance has 512 MB: a smaller heap share leaves room for metaspace and threads,
# and the serial GC has the lowest memory overhead on a single CPU.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-XX:+UseSerialGC", "-jar", "/app/app.jar"]
