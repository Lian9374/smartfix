# ============================================================
# SmartFix - multi-stage Dockerfile
#
# Shared Java 21 runtime, CI JAR target and local source-build target.
#
# Local Docker Compose validation is handled by docker-compose.yml.
# NOTE: the database is deliberately NOT bundled here; PostgreSQL is
# provided by the `db` service in docker-compose.yml.
# ============================================================

# Shared runtime for local source builds and CI's already-tested JAR.
FROM eclipse-temurin:21-jre AS runtime-base
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app \
    && mkdir -p /app/uploads && chown app:app /app/uploads
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65", "-jar", "/app/app.jar"]

# CI supplies a minimal build context containing only app.jar. No Maven rebuild.
# This image is for verification only, not the Elastic Beanstalk deployment.
FROM runtime-base AS ci-runtime
ARG COMMIT_SHA
ARG JAR_SHA256
LABEL org.opencontainers.image.revision=$COMMIT_SHA \
      com.smartfix.jar-sha256=$JAR_SHA256 \
      com.smartfix.purpose="container-test-only"
COPY --chown=app:app app.jar /app/app.jar

# ---- Local source build stage (unchanged default workflow) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy only the POM first so dependency resolution can be cached by Docker.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Copy sources and build the application.
COPY src ./src
RUN mvn -B clean verify

# ---- Runtime stage ----
FROM runtime-base AS runtime
COPY --from=build --chown=app:app /workspace/target/smartfix-*.jar /app/app.jar
