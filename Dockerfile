# ============================================================
# SmartFix - multi-stage Dockerfile
#
# Stage 1 : build the runnable Spring Boot fat jar
# Stage 2 : minimal JRE image that runs the jar
#
# Local Docker Compose validation is handled by docker-compose.yml.
# NOTE: the database is deliberately NOT bundled here; PostgreSQL is
# provided by the `db` service in docker-compose.yml.
# ============================================================

# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy only the POM first so dependency resolution can be cached by Docker.
COPY pom.xml .

# Copy sources and build the application.
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B clean verify

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as a non-root user (good container hygiene).
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app app
COPY --from=build /workspace/target/smartfix-*.jar /app/app.jar
RUN mkdir /app/uploads && chown -R app:app /app
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
