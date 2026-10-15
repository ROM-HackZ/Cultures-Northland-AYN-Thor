#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
out="artifacts/groups-check"
mkdir -p "$out"
javac -d "$out" android/shared/src/org/northland/thor/{GroupCommand,WheelState,GroupDirections}.java tests/android/ControlGroupsCheck.java
java -cp "$out" org.northland.thor.ControlGroupsCheck
