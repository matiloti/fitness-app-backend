# =============================================================================
# FitTrack Pro Backend - Dockerfile
# =============================================================================
# Multi-stage build for optimized production image
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1: Build
# -----------------------------------------------------------------------------
# Using non-alpine image to avoid native library issues on ARM64
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Copy Gradle files first (for caching)
COPY gradle gradle
COPY gradlew .
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY gradle.properties .

# Gradle options to avoid native library issues on ARM64
ENV GRADLE_OPTS="-Dorg.gradle.native=false"

# Download dependencies (cached layer)
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon --no-watch-fs

# Copy source code
COPY src src

# Build the application
RUN ./gradlew bootJar --no-daemon --no-watch-fs -x test

# -----------------------------------------------------------------------------
# Stage 2: Runtime
# -----------------------------------------------------------------------------
# Using non-alpine for consistency (larger but more compatible)
FROM eclipse-temurin:21-jre AS runtime

WORKDIR /app

# Create non-root user for security (Debian-style)
RUN groupadd -g 1001 fittrack && \
    useradd -u 1001 -g fittrack -m fittrack

# Create directories for uploads and logs
RUN mkdir -p /app/uploads /app/logs && \
    chown -R fittrack:fittrack /app

# Copy JAR from builder
COPY --from=builder /app/build/libs/*.jar app.jar

# Switch to non-root user
USER fittrack

# Expose port
EXPOSE 8080

# Health check (using curl which is available in Debian-based images)
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# JVM options for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
