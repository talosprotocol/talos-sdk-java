# talos-sdk-java Makefile

.PHONY: build test conformance clean doctor start stop

# Default target
all: build test

build:
	@echo "Building Java SDK..."
	./mvnw clean package

test:
	@echo "Running tests..."
	./mvnw test

conformance: build
	@echo "Running conformance tests..."
	java -jar target/talos-sdk-java-0.1.0.jar --vectors ../talos-contracts/test_vectors/sdk/release_sets/v1.0.0.json

doctor:
	@echo "Checking environment..."
	@java -version || echo "Java missing"
	@./mvnw -version || echo "Maven missing"

clean:
	@echo "Cleaning..."
	./mvnw clean

# Scripts wrapper
start:
	@./scripts/start.sh

stop:
	@./scripts/stop.sh
