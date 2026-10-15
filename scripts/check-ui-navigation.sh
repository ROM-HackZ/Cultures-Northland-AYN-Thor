#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out="artifacts/ui-navigation-check"
mkdir -p "$out"
javac -d "$out" android/shared/src/org/northland/thor/DirectionRepeat.java android/bridge/src/org/northland/thor/{MenuGeometry,UiGeometry}.java tests/android/UiNavigationCheck.java
java -cp "$out" org.northland.thor.UiNavigationCheck "$@"
