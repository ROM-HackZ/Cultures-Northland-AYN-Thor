#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
sdk="${ANDROID_HOME:-$HOME/Android/Sdk}"
classpath="artifacts/android/panel-classes:$sdk/platforms/android-36/android.jar"
for name in annotation bcprov bcutil bctls bcpkix; do
    classpath="$classpath:artifacts/android-deps/$name.jar"
done
out="artifacts/wireless-check"
mkdir -p "$out"
javac -encoding UTF-8 -classpath "$classpath" -d "$out" tests/android/{WirelessActivationCheck,ShellReplyCheck}.java
java -cp "$out:$classpath" org.northland.thor.WirelessActivationCheck
java -cp "$out:$classpath" org.northland.thor.ShellReplyCheck
python3 - <<'PY'
import zipfile
from pathlib import Path
with zipfile.ZipFile('artifacts/android/northland-panel.apk') as apk:
    dex=[name for name in apk.namelist() if name.startswith('classes') and name.endswith('.dex')]
    assert dex and set(dex)=={p.name for p in Path('artifacts/android/panel-dex').glob('classes*.dex')}, 'DEX files missing from APK'
    assert all(apk.read(name)==Path('artifacts/android/panel-dex',name).read_bytes() for name in dex), 'Stale DEX in APK'
    assert not any(b'rikka/shizuku' in apk.read(name) for name in dex), 'Shizuku code is still bundled'
    assert not any(name.endswith('.so') for name in apk.namelist()), 'Unexpected native dependency'
assert 'shizuku' not in Path('android/companion/AndroidManifest.xml').read_text().lower()
print('APK contains all DEX files and no Shizuku API or native pairing library')
PY
