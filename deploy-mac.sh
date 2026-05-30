#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

# Override via env, e.g. PI_USER=me PI_HOST=host.example ./deploy-mac.sh
PI_USER="${PI_USER:-ruben}"
PI_HOST="${PI_HOST:-cast.local}"
PI_PATH='~/projects/Cast'

SCOPE=${1:-all}

deploy_backend() {
    echo "==> Building fat jar..."
    ./gradlew buildFatJar

    echo "==> Copying jar to Pi..."
    ssh "$PI_USER@$PI_HOST" "mkdir -p $PI_PATH/build/libs"
    scp build/libs/Cast-all.jar "$PI_USER@$PI_HOST:$PI_PATH/build/libs/"

    echo "==> Deploying on Pi..."
    ssh "$PI_USER@$PI_HOST" "cd $PI_PATH && git pull --ff-only && docker compose up --build -d"

    echo ""
    echo "Done. Webapp: http://$PI_HOST:3000"
}

deploy_android() {
    echo "==> Building Android APK..."
    cd android && ./gradlew assembleDebug

    echo "==> Installing on device..."
    adb install -r app/build/outputs/apk/debug/app-debug.apk
}

case "$SCOPE" in
    backend) deploy_backend ;;
    android) deploy_android ;;
    all)
        deploy_backend
        deploy_android
        ;;
    *)
        echo "Usage: $0 [all|backend|android]"
        exit 1
        ;;
esac
