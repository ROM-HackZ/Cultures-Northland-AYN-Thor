"""Fetch the pinned Shizuku API binaries used by the local Android build."""
import hashlib
import io
import json
from pathlib import Path
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    out = ROOT / "artifacts/android-deps"
    out.mkdir(parents=True, exist_ok=True)
    for dep in json.loads((ROOT / "android/dependencies.json").read_text()):
        archive = out / (dep["name"] + "." + dep.get("format","aar"))
        data = archive.read_bytes() if archive.exists() else b""
        if hashlib.sha256(data).hexdigest() != dep["sha256"]:
            with urllib.request.urlopen(dep["url"], timeout=30) as response:
                data = response.read()
            if hashlib.sha256(data).hexdigest() != dep["sha256"]:
                raise RuntimeError("Dependency checksum mismatch: " + dep["name"])
            temp = archive.with_suffix(".tmp")
            temp.write_bytes(data)
            temp.replace(archive)
        if dep.get("format")!="jar":
            with zipfile.ZipFile(io.BytesIO(data)) as zipped:
                (out / (dep["name"] + ".jar")).write_bytes(zipped.read("classes.jar"))
    print("Pinned Shizuku API dependencies ready")


if __name__ == "__main__":
    main()
