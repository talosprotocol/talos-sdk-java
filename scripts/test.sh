#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# Run tests via Maven wrapper
./mvnw -q test
