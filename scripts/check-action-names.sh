#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out="artifacts/action-check"
mkdir -p "$out"
javac -d "$out" android/bridge/src/org/northland/thor/{MenuGeometry,ActionNames}.java tests/android/ActionNamesCheck.java
java -cp "$out" org.northland.thor.ActionNamesCheck "$@"
