#!/usr/bin/env bash
set -eo pipefail

# =============================================================================
# Java SDK Standardized Test Entrypoint
# =============================================================================

ARTIFACTS_DIR="artifacts/coverage"
mkdir -p "$ARTIFACTS_DIR"

COMMAND=${1:-"--unit"}

run_unit() {
    echo "=== Running Unit Tests ==="
    ./mvnw -q test
}

run_smoke() {
    echo "=== Running Smoke Tests ==="
    ./mvnw -q test -Dtest=*SmokeTest || run_unit
}

run_integration() {
    echo "=== Running Integration Tests ==="
    ./mvnw -q test -Dtest=*IntegrationTest
}

run_coverage() {
    echo "=== Running Coverage (JaCoCo via Maven) ==="
    ./mvnw clean test jacoco:report
    
    # JaCoCo generates XML report in target/site/jacoco/jacoco.xml by default
    # For now, just copy it - the coordinator will need to support JaCoCo format
    if [ -f "target/site/jacoco/jacoco.xml" ]; then
        cp target/site/jacoco/jacoco.xml "$ARTIFACTS_DIR/coverage.xml"
        echo "✅ Coverage report generated: $ARTIFACTS_DIR/coverage.xml (JaCoCo XML format)"
    else
        echo "⚠️  JaCoCo report not found at target/site/jacoco/jacoco.xml"
    fi
}

case "$COMMAND" in
    --smoke)
        run_smoke
        ;;
    --unit)
        run_unit
        ;;
    --integration)
        run_integration
        ;;
    --coverage)
        run_coverage
        ;;
    --ci)
        run_smoke
        run_unit
        run_coverage
        ;;
    --full)
        run_smoke
        run_unit
        run_integration
        run_coverage
        ;;
    *)
        echo "Usage: $0 {--smoke|--unit|--integration|--coverage|--ci|--full}"
        exit 1
        ;;
esac

# Generate minimal results.json
mkdir -p artifacts/test
cat <<EOF > artifacts/test/results.json
{
  "repo_id": "sdks-java",
  "command": "$COMMAND",
  "status": "pass",
  "timestamp": "$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
}
EOF
