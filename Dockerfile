# ============================================================
# Aegis Fraud-Shield — Multi-Stage Dockerfile
# ============================================================
#
# Stage 1 (builder): Compiles the application and produces a fat JAR.
# Stage 2 (runtime): Minimal JRE image — only what is needed to run.
#
# Multi-stage builds keep the final image small (no Maven, no source code)
# and demonstrate understanding of Docker layer caching and image optimization.
# ============================================================

# ── Stage 1: build ──────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-24 AS builder

WORKDIR /workspace

# Copy the POM first so Maven can download dependencies in a cached layer.
# If only source files change (not pom.xml), this layer is reused from cache.
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy the full source tree and build the fat JAR, skipping tests
# (tests run in CI; here we just need the artifact).
COPY src ./src
RUN mvn package -DskipTests -q

# ── Stage 2: runtime ─────────────────────────────────────────
FROM eclipse-temurin:24-jre-alpine AS runtime

# Non-root user for security best practice.
RUN addgroup -S fraudshield && adduser -S fraudshield -G fraudshield
USER fraudshield

WORKDIR /app

# Copy only the fat JAR from the builder stage — no Maven, no source code.
COPY --from=builder /workspace/target/*.jar app.jar

# Expose the Spring Boot default port.
EXPOSE 8080

# JVM tuning flags:
#   -XX:+UseContainerSupport        honours cgroup CPU/memory limits
#   -XX:MaxRAMPercentage=75.0       use up to 75% of container RAM for heap
#   -Djava.security.egd=...         faster startup entropy on Linux containers
ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-jar", "app.jar"]
