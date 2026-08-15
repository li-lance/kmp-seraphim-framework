#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "$0")/.." && pwd)"
fixture_root="$repository_root/build/certification/daily-board"
web_fixture_root="$repository_root/build/certification/daily-board-web"

rm -rf "$fixture_root" "$web_fixture_root"

"$repository_root/gradlew" \
  -p "$repository_root" \
  createProduct \
  -Pmanifest="$repository_root/project.yaml" \
  -Poutput="$fixture_root"

"$repository_root/gradlew" \
  -p "$fixture_root" \
  :shared:task-board:allTests \
  :shared:local-data-sql:allTests \
  :apps:android:assembleDebug

"$repository_root/gradlew" \
  -p "$repository_root" \
  createProduct \
  -Pmanifest="$repository_root/certification/web-enabled.yaml" \
  -Poutput="$web_fixture_root"

"$repository_root/gradlew" \
  -p "$web_fixture_root" \
  :shared:task-board:wasmJsNodeTest \
  :shared:local-data-web:wasmJsNodeTest
