# =============================================================================
# talos-sdk-java Test Script
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")/.."

log() { printf '%s\n' "$*"; }
info() { printf 'ℹ️  %s\n' "$*"; }

info "Testing talos-sdk-java..."

# Run tests via Maven wrapper
./mvnw -q test

log "✓ talos-sdk-java tests passed."
