#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

# Override via env, e.g. HOST=host.example ./deploy.sh
HOST="${HOST:-localhost}"

echo "==> Building fat jar..."
./gradlew buildFatJar

echo "==> Building images..."
docker compose build

echo "==> Deploying..."
docker compose up -d

echo ""
echo "Done. Webapp: http://$HOST:3000"
