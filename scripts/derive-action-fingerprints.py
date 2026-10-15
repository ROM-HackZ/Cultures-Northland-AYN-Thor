"""Derive human-menu labels and non-reconstructive fingerprints from owned game files.

Requires Pillow. Writes metadata only; never exports or modifies game artwork.
The binary layout was checked against the owned ARM64 library and the BMD format
research at https://github.com/martianboy/cultures2-wasm/blob/master/src/bmd.rs .
No code from that repository is bundled here.
"""

import argparse
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import re
import struct
import zipfile

from PIL import Image


def block(data, offset):
    tag, _, size = struct.unpack_from("<III", data, offset)
    encoded = struct.unpack_from("<I", data, 8)[0] == 1
    if tag != 1001 or offset + 12 + size > len(data):
        raise ValueError("Invalid native data section")
    raw = data[offset + 12:offset + 12 + size]
    if encoded:
        decoded = bytearray()
        key = 71
        for i, value in enumerate(raw):
            decoded.append(((value - 1) ^ key) & 255)
            key = (key + i * 33 + 126) & 255
        raw = bytes(decoded)
    end=offset+12+size
    return raw, end+int(encoded and end<len(data))


def text_table(data):
    offsets, cursor = block(data, 28)
    strings, _ = block(data, cursor)
    result = {}
    index = 0
    for (offset,) in struct.iter_unpack("<I", offsets):
        line = strings[offset:strings.index(b"\0", offset)].decode("cp1252").lstrip("\x01\x02")
        match = re.fullmatch(r'string(n)?\s+(?:(\d+)\s+)?"(.*)"', line)
        if match:
            if match[2] is not None:
                index = int(match[2])
            result[index] = match[3]
            index += 1
    return result


def string_id(elf, command):
    offset = struct.unpack("<H", elf.at(0x12982A + command * 2, 2))[0]
    pc = 0x1D3010 + offset * 4
    registers = {0: 0}
    for _ in range(100):
        instruction = struct.unpack("<I", elf.at(pc, 4))[0]
        if instruction & 0x7F800000 == 0x52800000:
            registers[instruction & 31] = ((instruction >> 5) & 65535) << (((instruction >> 21) & 3) * 16)
        if instruction & 0x7C000000 == 0x14000000:
            immediate = instruction & 0x3FFFFFF
            if immediate & 0x2000000:
                immediate -= 0x4000000
            destination = pc + immediate * 4
            if destination == 0x46ED00:
                return registers[1]
            if destination == 0x46ED80:
                return registers[0]
            pc = destination
        else:
            pc += 4
    raise ValueError("Unrecognized native label mapping")


def render_icon(frames, pixels, rows, palette, icon):
    kind, _, _, width, height, row_start = struct.unpack_from("<IiiIII", frames, icon * 24)
    if kind != 4 or (width, height) != (33, 33):
        raise ValueError("Unexpected human-menu icon layout")
    image = Image.new("RGBA", (width, height))
    for y in range(height):
        (row,) = struct.unpack_from("<I", rows, (row_start + y) * 4)
        if row == 0xFFFFFFFF:
            continue
        x, position = row >> 22, row & 0x3FFFFF
        while True:
            run = pixels[position]
            position += 1
            if run == 0:
                break
            if run >= 128:
                x += run - 128
                continue
            for _ in range(run):
                color, alpha = pixels[position:position + 2]
                position += 2
                if x >= width:
                    raise ValueError("Icon row exceeds its width")
                image.putpixel((x, y), tuple(palette[color * 3:color * 3 + 3]) + (alpha,))
                x += 1
    return image


def fingerprints(image, cx, cy):
    def light(x, y):
        return sum(value * weight for value, weight in zip(image.getpixel((x, y)), (299, 587, 114)))

    horizontal = vertical = 0
    for y in range(8):
        for x in range(8):
            if light(cx - 12 + x * 4, cy - 14 + y * 4) > light(cx - 16 + x * 4, cy - 14 + y * 4):
                horizontal |= 1 << (y * 8 + x)
            if light(cx - 14 + x * 4, cy - 12 + y * 4) > light(cx - 14 + x * 4, cy - 16 + y * 4):
                vertical |= 1 << (y * 8 + x)
    return [f"{horizontal:016x}", f"{vertical:016x}"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--library", required=True, type=Path)
    parser.add_argument("--asset-pack", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    args = parser.parse_args()
    spec = importlib.util.spec_from_file_location("direct_inventory", Path(__file__).with_name("inspect-direct-actions.py"))
    inventory = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(inventory)
    elf = inventory.Elf(args.library)
    if hashlib.sha256(elf.data).hexdigest() != inventory.REVIEWED_SHA256:
        parser.error("Native mapping requires the reviewed game library; inspect the new binary first")
    with zipfile.ZipFile(args.asset_pack) as pack:
        labels = text_table(pack.read("assets/data/text/eng/strings/ingamegui/ingameguimisclogic.cif"))
        data = pack.read("assets/data/gui/lang/eng/bobs/ls_gui_window.bmd")
        frames, cursor = block(data, 36)
        pixels, cursor = block(data, cursor)
        rows, _ = block(data, cursor)
        palette = Image.open(io.BytesIO(pack.read("assets/data/gui/palettes/context.pcx"))).getpalette()
    result = []
    for command in range(2, 46):
        icon = struct.unpack("<I", elf.at(0x12C6B8 + (command - 3) * 4, 4))[0] if command >= 3 else 107
        # Generic placeholder aliases have no distinct visual identity. Go To (2)
        # is the use of this sprite found in the reviewed normal human menu builder.
        if icon == 107 and command != 2:
            continue
        sprite = render_icon(frames, pixels, rows, palette, icon)
        signatures = []
        for size in (49, 50):
            scaled = sprite.resize((size, size), Image.Resampling.NEAREST)
            image = Image.new("RGB", (size, size))
            image.paste(scaled, (0, 0), scaled)
            for dy in (2, 4, 6):
                signatures.append(fingerprints(image, size // 2 - 1, size // 2 + dy))
        result.append({"native_command": command, "label": labels[string_id(elf, command)],
                       "sprite_id": icon, "fingerprints": signatures})
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(f"Derived {len(result)} human-menu command fingerprints: {args.out}")


if __name__ == "__main__":
    main()
