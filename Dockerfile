# =============================================================================
# Talos Java SDK - Tool Image (Monorepo-Root Context)
# =============================================================================

# Builder stage
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /workspace

# Copy Maven files
COPY sdks/java/pom.xml ./sdks/java/
COPY sdks/java/.mvn ./sdks/java/.mvn
COPY sdks/java/mvnw ./sdks/java/mvnw

# Download dependencies
WORKDIR /workspace/sdks/java
RUN mvn dependency:go-offline -B

# Copy source
WORKDIR /workspace
COPY sdks/java ./sdks/java
COPY scripts ./scripts

# Build
WORKDIR /workspace/sdks/java
RUN mvn package -DskipTests -B

# Test runner stage
FROM maven:3.9-eclipse-temurin-17

# OCI labels
LABEL org.opencontainers.image.source="https://github.com/talosprotocol/talos"
LABEL org.opencontainers.image.description="Talos Java SDK Tool Image"
LABEL org.opencontainers.image.licenses="Apache-2.0"

WORKDIR /workspace/sdks/java

# Install runtime dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    make \
    bash \
    && rm -rf /var/lib/apt/lists/*

# Copy from builder
COPY --from=builder /workspace/sdks/java ./
COPY --from=builder /workspace/scripts /workspace/scripts

# Create non-root user
RUN useradd -m -u 1001 talos && chown -R talos:talos /workspace
USER talos

# Default: run CI tests
CMD ["scripts/test.sh", "--ci"]
