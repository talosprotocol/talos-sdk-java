# Universal Makefile Interface
all: install lint test build conformance

install:
	./mvnw install -DskipTests

typecheck:
	# Java is compiled, so compile acts as typecheck
	./mvnw compile

lint:
	# Check style (Spotless check)
	./mvnw spotless:check

format:
	# Auto-fix style
	./mvnw spotless:apply

test:
	# Unit tests
	./mvnw test

conformance:
	# Conformance via JUnit using system properties
	@if [ -z "$(RELEASE_SET)" ]; then \
		echo "Skipping conformance (No RELEASE_SET provided)"; \
	else \
		./mvnw test -Dconformance.vectors=$(RELEASE_SET); \
	fi

build:
	./mvnw package -DskipTests

clean:
	./mvnw clean
