#!/bin/bash
set -euo pipefail

echo "Cleaning talos-sdk-java..."
# Maven clean
./mvnw clean 2>/dev/null || mvn clean 2>/dev/null || true
# Build artifacts
rm -rf target .gradle build 2>/dev/null || true
# Coverage & reports
rm -f jacoco*.xml conformance.xml junit.xml 2>/dev/null || true
rm -rf site 2>/dev/null || true
# Surefire reports
find . -name "surefire-reports" -type d -exec rm -rf {} + 2>/dev/null || true
echo "✓ talos-sdk-java cleaned"
