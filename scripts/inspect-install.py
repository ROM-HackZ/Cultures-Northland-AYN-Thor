"""Read a Northland installation and save a local file/PE inventory."""

import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path
import struct


def inspect_pe(path):
    data = path.read_bytes()
    if data[:2] != b"MZ":
        raise ValueError(f"Not a Windows executable: {path.name}")
    pe = struct.unpack_from("<I", data, 0x3C)[0]
    if data[pe : pe + 4] != b"PE\0\0":
        raise ValueError(f"Invalid PE signature: {path.name}")
    machine, section_count = struct.unpack_from("<HH", data, pe + 4)
    optional_size = struct.unpack_from("<H", data, pe + 20)[0]
    optional = pe + 24
    magic = struct.unpack_from("<H", data, optional)[0]
    directories = optional + (96 if magic == 0x10B else 112)
    imports_rva = struct.unpack_from("<I", data, directories + 8)[0]
    sections = optional + optional_size

    def offset(rva):
        for index in range(section_count):
            start = sections + index * 40
            virtual_size, virtual_address, raw_size, raw_start = struct.unpack_from(
                "<IIII", data, start + 8
            )
            if virtual_address <= rva < virtual_address + max(virtual_size, raw_size):
                return raw_start + rva - virtual_address
        raise ValueError(f"Unmapped executable address in {path.name}")

    imports = []
    if imports_rva:
        cursor = offset(imports_rva)
        while any(data[cursor : cursor + 20]):
            name_rva = struct.unpack_from("<I", data, cursor + 12)[0]
            name_offset = offset(name_rva)
            end = data.index(b"\0", name_offset)
            imports.append(data[name_offset:end].decode("ascii"))
            cursor += 20
    return {
        "machine": hex(machine),
        "architecture": "x86" if machine == 0x14C else hex(machine),
        "bits": 32 if magic == 0x10B else 64,
        "imports": imports,
        "directdraw_loaded_dynamically": b"DirectDrawCreate" in data and b"DDRAW.DLL" in data,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("game_root", type=Path)
    parser.add_argument("--out", type=Path, default=Path("artifacts/installation.json"))
    args = parser.parse_args()
    root = args.game_root.resolve(strict=True)
    for required in ("Game.exe", "DataX/Libs/data0001.lib"):
        if not (root / required).is_file():
            parser.error(f"Missing Northland file: {required}")
    if args.out.resolve().is_relative_to(root):
        parser.error("Write the report outside the original game installation")
    files = []
    for path in sorted(root.rglob("*")):
        if path.is_file():
            with path.open("rb") as stream:
                digest = hashlib.file_digest(stream, "sha256").hexdigest()
            files.append({"path": path.relative_to(root).as_posix(), "bytes": path.stat().st_size, "sha256": digest})
    report = {
        "game_root": str(root),
        "file_count": len(files),
        "total_bytes": sum(row["bytes"] for row in files),
        "extensions": dict(Counter(Path(row["path"]).suffix.lower() for row in files)),
        "executables": {path.name: inspect_pe(path) for path in sorted(root.glob("*.exe"))},
        "files": files,
    }
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"Inspected {len(files)} files, {report['total_bytes']} bytes. Report: {args.out}")
    print(json.dumps(report["executables"]["Game.exe"], indent=2))


if __name__ == "__main__":
    main()
