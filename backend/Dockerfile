# --- Build stage: compile and package the jar with the Maven Wrapper ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Download dependencies first: this layer is cached until pom.xml changes
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline

COPY src src
# Tests run in CI (they need Docker for Testcontainers), not inside the image build
RUN ./mvnw -B -q package -DskipTests

# --- Runtime stage: only the JRE and the jar ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as an unprivileged user
RUN addgroup -S aerides && adduser -S aerides -G aerides
USER aerides

COPY --from=build /workspace/target/aerides-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
