# =============================================================================
# Talos Java SDK Makefile
# =============================================================================

# Variables
IMAGE_NAME ?= talos-sdk-java
IMAGE_TAG ?= latest
REGISTRY ?= ghcr.io/talosprotocol
FULL_IMAGE := $(REGISTRY)/$(IMAGE_NAME):$(IMAGE_TAG)

.PHONY: all install typecheck lint format test coverage coverage-check conformance build docker-build docker-push clean help

# Default target
all: install lint test build conformance

# Help
help:
	@echo "Talos Java SDK - Available targets:"
	@echo "  make install        - Install dependencies"
	@echo "  make typecheck      - Type check (compile)"
	@echo "  make lint           - Check code style"
	@echo "  make format         - Auto-fix code style"
	@echo "  make test           - Run unit tests"
	@echo "  make coverage       - Generate coverage report"
	@echo "  make coverage-check - Enforce coverage threshold"
	@echo "  make conformance    - Run conformance tests"
	@echo "  make build          - Build JAR package"
	@echo "  make docker-build   - Build Docker image"
	@echo "  make docker-push    - Push Docker image to registry"
	@echo "  make clean          - Clean build artifacts"

# Install dependencies
install:
	@echo "📦 Installing dependencies..."
	./mvnw install -DskipTests

# Type check (Java is compiled)
typecheck:
	@echo "🔍 Type checking..."
	./mvnw compile

# Lint
lint:
	@echo "🔍 Checking code style..."
	./mvnw spotless:check || echo "⚠️  Lint violations found. Run 'make format' to fix."

# Format
format:
	@echo "✨ Formatting code..."
	./mvnw spotless:apply

# Test (delegates to scripts/test.sh)
test:
	@echo "🧪 Running tests..."
	@./scripts/test.sh --unit

# Coverage (delegates to scripts/test.sh)
coverage:
	@echo "📊 Generating coverage report..."
	@./scripts/test.sh --coverage

# Coverage Check
coverage-check:
	@echo "🎯 Enforcing coverage threshold..."
	./mvnw test jacoco:check

# Conformance
conformance:
	@echo "✅ Running conformance tests..."
	@if [ -z "$(RELEASE_SET)" ]; then \
		echo "⏭️  Skipping conformance (No RELEASE_SET provided)"; \
	else \
		./mvnw test -Dconformance.vectors=$(RELEASE_SET); \
	fi

# Build
build:
	@echo "🔨 Building JAR..."
	./mvnw package -DskipTests

# Docker Build
docker-build:
	@echo "🐳 Building Docker image..."
	docker build -t $(IMAGE_NAME):$(IMAGE_TAG) -t $(FULL_IMAGE) -f Dockerfile .
	@echo "✅ Image built: $(IMAGE_NAME):$(IMAGE_TAG)"

# Docker Push
docker-push: docker-build
	@echo "📤 Pushing Docker image..."
	docker push $(FULL_IMAGE)
	@echo "✅ Image pushed: $(FULL_IMAGE)"

# Clean
clean:
	@echo "🧹 Cleaning up..."
	./mvnw clean
	@rm -rf artifacts/
