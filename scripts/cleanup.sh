#!/bin/bash
set -euo pipefail

echo "Cleaning up..."
./mvnw clean || true
rm -rf target .gradle build
