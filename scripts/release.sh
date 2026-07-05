#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [[ ! -f "$ROOT_DIR/my-release-key.jks" ]]; then
  echo "Missing my-release-key.jks at the repository root." >&2
  exit 1
fi

load_secret_property() {
  local key="$1"
  local file="$ROOT_DIR/appsecrets.properties"
  [[ -f "$file" ]] || return 0

  awk -F= -v key="$key" '
    $0 ~ /^[[:space:]]*#/ { next }
    $0 !~ /=/ { next }
    {
      name=$1
      sub(/^[[:space:]]+/, "", name)
      sub(/[[:space:]]+$/, "", name)
      if (name == key) {
        value=substr($0, index($0, "=") + 1)
        sub(/^[[:space:]]+/, "", value)
        sub(/[[:space:]]+$/, "", value)
        print value
        exit
      }
    }
  ' "$file"
}

export RELEASE_KEY_ALIAS="${RELEASE_KEY_ALIAS:-$(load_secret_property RELEASE_KEY_ALIAS)}"
export RELEASE_STORE_PASSWORD="${RELEASE_STORE_PASSWORD:-$(load_secret_property RELEASE_STORE_PASSWORD)}"
export RELEASE_KEY_PASSWORD="${RELEASE_KEY_PASSWORD:-$(load_secret_property RELEASE_KEY_PASSWORD)}"

if [[ -z "${RELEASE_KEY_ALIAS:-}" ]]; then
  echo "Missing RELEASE_KEY_ALIAS in appsecrets.properties or the environment." >&2
  exit 1
fi

if [[ -z "${RELEASE_STORE_PASSWORD:-}" ]]; then
  echo "Missing RELEASE_STORE_PASSWORD in appsecrets.properties or the environment." >&2
  exit 1
fi

export RELEASE_KEY_PASSWORD="${RELEASE_KEY_PASSWORD:-$RELEASE_STORE_PASSWORD}"

mkdir -p .gradle-home tmp

if [[ ! -d "${ANDROID_HOME:-}/platforms" ]]; then
  export ANDROID_HOME="$ROOT_DIR/.android-sdk"
fi
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export GRADLE_USER_HOME="$ROOT_DIR/.gradle-home"

gradle -Djava.io.tmpdir="$ROOT_DIR/tmp" :app:release
