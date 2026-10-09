#!/bin/sh

# 该轻量包装器将 Gradle 下载到项目 work 目录，避免依赖用户全局 Gradle。
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION="9.0.0"
GRADLE_HOME="${GRADLE_USER_HOME:-$PROJECT_DIR/work/gradle-home}"
GRADLE_DIST_DIR="$GRADLE_HOME/gradle-$GRADLE_VERSION"
GRADLE_BIN="$GRADLE_DIST_DIR/bin/gradle"
GRADLE_ZIP="$GRADLE_HOME/gradle-$GRADLE_VERSION-bin.zip"

if [ ! -x "$GRADLE_BIN" ]; then
    mkdir -p "$GRADLE_HOME"
    if [ ! -f "$GRADLE_ZIP" ]; then
        curl --fail --location --silent --show-error \
            "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" \
            --output "$GRADLE_ZIP"
    fi
    unzip -q -o "$GRADLE_ZIP" -d "$GRADLE_HOME"
fi

exec "$GRADLE_BIN" "$@"
