#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out="artifacts/snapshot-check"
mkdir -p "$out"
javac -d "$out" android/shared/src/org/northland/thor/*.java tests/android/ActionMenuSnapshotCheck.java
java -cp "$out" org.northland.thor.ActionMenuSnapshotCheck
