"""Fetch verified local-build dependencies and the pinned LibADB Java sources."""
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
        if dep.get('format')=='source_zip':
            destination=out/'libadb-src'
            selected=set()
            with zipfile.ZipFile(io.BytesIO(data)) as zipped:
                for name in zipped.namelist():
                    parts=Path(name).parts
                    if len(parts)<2:continue
                    relative=Path(*parts[1:])
                    prefix=Path(dep['source_subdir'])
                    if not relative.is_relative_to(prefix) or relative.suffix!='.java':continue
                    target=destination/relative.relative_to(prefix)
                    if not target.resolve().is_relative_to(destination.resolve()):raise ValueError('Unsafe source archive path')
                    target.parent.mkdir(parents=True,exist_ok=True)
                    target.write_bytes(zipped.read(name));selected.add(target)
            for old in destination.rglob('*.java'):
                if old not in selected:old.unlink()
            # Local changes: bounded sockets and reliable cancellation during a
            # pairing handshake. Match exact upstream text; fail on source drift.
            edits={
                'AdbConnection.java': [('this.mSocket = new Socket(host, port);',
                    'this.mSocket = new Socket();\n            this.mSocket.connect(new java.net.InetSocketAddress(host, port), 5000);\n            this.mSocket.setSoTimeout(15000);')],
                'PairingConnectionCtx.java': [
                    ('private State mState = State.Ready;', 'private volatile State mState = State.Ready;\n    private volatile Socket transport;'),
                    ('Socket socket = new Socket(mHost, mPort);', 'Socket socket = new Socket();\n        transport = socket;\n        socket.connect(new java.net.InetSocketAddress(mHost, mPort), 5000);\n        socket.setSoTimeout(15000);'),
                    ('public void close() {', 'public void close() {\n        mState = State.Stopped;\n        try { if (transport != null) transport.close(); } catch (IOException ignored) { }'),
                    ('Log.d(TAG, theirPeerInfo.toString());', '// Northland Thor: omit verbose peer identity logging.')]
            }
            for filename,replacements in edits.items():
                path=destination/'io/github/muntashirakon/adb'/filename
                source=path.read_text(encoding='utf-8')
                for before,after in replacements:
                    if source.count(before)!=1:raise ValueError('LibADB patch mismatch: '+filename)
                    source=source.replace(before,after)
                path.write_text(source,encoding='utf-8')
        elif dep.get("format")!="jar":
            with zipfile.ZipFile(io.BytesIO(data)) as zipped:
                (out / (dep["name"] + ".jar")).write_bytes(zipped.read("classes.jar"))
    print("Pinned Android dependencies ready")


if __name__ == "__main__":
    main()
