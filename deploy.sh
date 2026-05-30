#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

# Local, gitignored overrides (HOST). See deploy.env.example.
[ -f deploy.env ] && source deploy.env

HOST="${HOST:-localhost}"

echo "==> Building fat jar..."
./gradlew buildFatJar

echo "==> Building images..."
docker compose build

echo "==> Deploying..."
docker compose up -d

echo ""
echo "Done. Webapp: http://$HOST:3000"
