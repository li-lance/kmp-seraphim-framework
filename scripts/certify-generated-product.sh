#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "$0")/.." && pwd)"
fixture_root="$repository_root/build/certification/daily-board"

rm -rf "$fixture_root"
"$repository_root/gradlew" \
  -p "$repository_root" \
  createProduct \
  -Pmanifest="$repository_root/project.yaml" \
  -Poutput="$fixture_root"

"$repository_root/gradlew" \
  -p "$fixture_root" \
  :shared:task-board:allTests \
  :apps:android:assembleDebug
