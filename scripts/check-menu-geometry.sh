#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out="artifacts/menu-check"
mkdir -p "$out"
javac -d "$out" android/bridge/src/org/northland/thor/MenuGeometry.java tests/android/MenuGeometryCheck.java
java -cp "$out" org.northland.thor.MenuGeometryCheck "$@"
