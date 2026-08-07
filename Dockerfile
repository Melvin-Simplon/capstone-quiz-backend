# syntax=docker/dockerfile:1
# ──────────────────────────────────────────────────────────────────────────────
# Multi-stage build for the AKS track (piste "AKS", see helm/ and
# .github/workflows/aks-deploy.yml). The App Service track (deploy.yml) never
# uses this image — App Service builds/deploys the jar directly.
# ──────────────────────────────────────────────────────────────────────────────

# ── Build stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Copy the wrapper + POM first so `./mvnw dependency:go-offline` is its own
# Docker layer, cached across builds unless pom.xml itself changes -- avoids
# re-downloading the whole dependency tree on every source-code-only change.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline

COPY src/ src/
# Same "**" includes-fix reasoning as pom.xml's own build.resources block
# (missing db/migration/*.sql was a real bug hit earlier in this TP) --
# building inside Docker goes through the exact same pom.xml, so nothing
# extra needed here, just flagging why this step can be trusted to package
# Flyway migrations correctly.
RUN ./mvnw -B clean package -DskipTests

# ── Runtime stage ────────────────────────────────────────────────────────────
# JRE, not JDK -- smaller image, no compiler needed to run a prebuilt jar.
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Runs as non-root -- AKS's default Pod Security Standards (baseline/restricted,
# commonly enforced via namespace labels) reject containers that try to run as
# UID 0.
RUN useradd --system --create-home --shell /usr/sbin/nologin spring
USER spring

COPY --from=build /app/target/azure-quiz-backend-*.jar app.jar

EXPOSE 8080

# SPRING_PROFILES_ACTIVE=prod is set via the Helm chart's env (values.yaml),
# not hardcoded here -- same reasoning as app-service.tf: local/dev runs
# of this exact image (e.g. `docker run` without overriding it) would
# otherwise silently pick up the "default" profile's Azurite/localhost-only
# settings, which don't exist inside a container.
ENTRYPOINT ["java", "-jar", "app.jar"]
