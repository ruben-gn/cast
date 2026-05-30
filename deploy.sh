#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

echo "==> Building fat jar..."
./gradlew buildFatJar

echo "==> Building images..."
docker compose build

echo "==> Deploying..."
docker compose up -d

echo ""
echo "Done. Webapp: http://localhost:3000"
