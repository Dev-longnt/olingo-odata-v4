# syntax=docker/dockerfile:1

# Stage 1: Build the application
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

# Copy Gradle wrapper and build files first for better layer caching
COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./

# Copy all module source code
COPY client/ client/
COPY server/ server/

# Make gradlew executable and build
RUN chmod +x gradlew
RUN ./gradlew :server:build -x test --no-daemon

# Stage 2: Run the application
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy the built jar from the server module
COPY --from=builder /workspace/server/build/libs/*.jar app.jar

# Create non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser appuser
RUN chown appuser:appuser /app
USER appuser

EXPOSE 8080

# Add health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-Xmx512m", "-Xms256m", "-jar", "app.jar"]