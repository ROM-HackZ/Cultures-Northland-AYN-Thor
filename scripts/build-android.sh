#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
sdk="${ANDROID_HOME:-$HOME/Android/Sdk}"
tools="$sdk/build-tools/36.0.0"
platform="$sdk/platforms/android-36/android.jar"
python3 scripts/fetch-android-deps.py
deps="artifacts/android-deps"
jars=("$deps/annotation.jar" "$deps/bcprov.jar" "$deps/bcutil.jar" "$deps/bctls.jar" "$deps/bcpkix.jar")
classpath="$platform"
for dependency in "${jars[@]}"; do classpath="$classpath:$dependency"; done
out="artifacts/android"
mkdir -p "$out/classes" "$out/dex"
find "$out/classes" "$out/dex" -type f \( -name '*.class' -o -name '*.dex' \) -delete
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -classpath "$platform" -d "$out/classes" android/shared/src/org/northland/thor/*.java android/bridge/src/org/northland/thor/*.java
find "$out/classes" -name '*.class' -print0 | xargs -0 "$tools/d8" --min-api 26 --lib "$platform" --output "$out/dex"
(cd "$out/dex" && jar cf ../northland-bridge.jar classes.dex)
echo "Built $out/northland-bridge.jar"
mkdir -p "$out/panel-classes" "$out/panel-dex"
find "$out/panel-classes" "$out/panel-dex" -type f \( -name '*.class' -o -name '*.dex' \) -delete
find "$deps/libadb-src" -name '*.java' > "$out/libadb-sources.list"
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -classpath "$classpath" -d "$out/panel-classes" @"$out/libadb-sources.list" android/shared/src/org/northland/thor/*.java android/bridge/src/org/northland/thor/*.java android/companion/src/org/northland/thor/*.java
find "$out/panel-classes" -name '*.class' -print0 | xargs -0 "$tools/d8" --min-api 26 --lib "$platform" --output "$out/panel-dex" "${jars[@]}"
"$tools/aapt2" link -I "$platform" --manifest android/companion/AndroidManifest.xml -o "$out/panel-unsigned.apk"
(cd "$out/panel-dex" && zip -q -u ../panel-unsigned.apk classes*.dex)
"$tools/zipalign" -f 4 "$out/panel-unsigned.apk" "$out/panel-aligned.apk"
key=".local/signing/debug.keystore"
if [[ ! -f "$key" ]]; then
    mkdir -p "$(dirname "$key")"
    keytool -genkeypair -keystore "$key" -storepass android -keypass android -alias androiddebugkey \
        -dname "CN=Northland Thor Development" -keyalg RSA -keysize 2048 -validity 10000 -noprompt
fi
"$tools/apksigner" sign --ks "$key" --ks-pass pass:android --out "$out/northland-panel.apk" "$out/panel-aligned.apk"
"$tools/apksigner" verify "$out/northland-panel.apk"
echo "Built $out/northland-panel.apk (development signature)"
