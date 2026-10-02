# ==============================================================================
# Multi-Stage Production Dockerfile for ScaleLink URL Shortener
# ==============================================================================
# Stage 1: Build stage (Maven + Eclipse Temurin JDK 21 Alpine)
# ==============================================================================
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Cache Maven dependencies layer
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and package executable fat JAR
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==============================================================================
# Stage 2: High-Performance Minimal JRE Runtime
# ==============================================================================
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="Akshat <akshat@scalelink.io>"
LABEL description="ScaleLink High-Throughput Distributed URL Shortener Engine"

WORKDIR /app

# Run as a non-privileged user for hardened container security
RUN addgroup -S scalegroup && adduser -S scaleuser -G scalegroup

# Copy fat JAR from build stage
COPY --from=builder /build/target/*.jar app.jar
RUN chown -R scaleuser:scalegroup /app

USER scaleuser

# Render sets the PORT environment variable dynamically
ENV PORT=8080
EXPOSE ${PORT}

# Production JVM Flags:
# - Container-aware memory sizing (MaxRAMPercentage=75%)
# - Fast non-blocking entropy source for UUID / Hashing
# - Virtual Threads friendly
ENTRYPOINT ["sh", "-c", "java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom -jar app.jar --server.port=${PORT}"]
