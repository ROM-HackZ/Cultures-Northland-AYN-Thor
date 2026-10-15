"""Read-only inventory of Northland's native action interfaces and Android access gates.

Does not attach to the game, call its functions, change packages or alter the device.
Retail libraries and generated reports belong in ignored private directories.
"""

import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess


REVIEWED_SHA256 = "96e49d3bb5a3fcd137a5224135900e72d3a19472612ed53c2e4167f5fbceb116"
INTERFACES = {
    "human_commands": "Logic_UserControl_GetHumanCommands",
    "vehicle_commands": "Logic_UserControl_GetVehicleCommands",
    "house_commands": "Logic_UserControl_GetHouseCommands",
    "human_labels": "StringTool_GetLogicUserCommandsForHumanTypeNamePtr",
    "vehicle_labels": "StringTool_GetLogicUserCommandsForVehicleTypeNamePtr",
    "house_labels": "StringTool_GetLogicUserCommandsForHouseTypeNamePtr",
    "single_human": "Selection_GetSelectedSingleHumanId",
    "human_group_count": "Selection_GetSelectedMultipleHumanNumber",
    "human_group_member": "Selection_GetSelectedMultipleHumanId",
    "single_vehicle": "Selection_GetSelectedSingleVehicleId",
    "vehicle_group_count": "Selection_GetSelectedMultipleVehiclesNumber",
    "vehicle_group_member": "Selection_GetSelectedMultipleVehicleId",
    "selected_house": "Selection_GetSelectedHouseId",
}


class Elf:
    def __init__(self, path):
        self.data = path.read_bytes()
        if self.data[:6] != b"\x7fELF\x02\x01" or struct.unpack_from("<H", self.data, 18)[0] != 183:
            raise ValueError("Expected a little-endian ARM64 ELF library")
        offset = struct.unpack_from("<Q", self.data, 40)[0]
        step, count = struct.unpack_from("<HH", self.data, 58)
        if step != 64 or offset + count * step > len(self.data):
            raise ValueError("Invalid ELF section table")
        self.sections = [struct.unpack_from("<IIQQQQIIQQ", self.data, offset + i * step)
                         for i in range(count)]

    def symbols(self):
        result = {}
        for section in self.sections:
            if section[1] != 11:
                continue
            strings = self.sections[section[6]]
            table = self.data[strings[4]:strings[4] + strings[5]]
            if section[9] != 24:
                raise ValueError("Unexpected dynamic symbol size")
            for offset in range(section[4], section[4] + section[5], 24):
                name, info, _, index, address, size = struct.unpack_from("<IBBHQQ", self.data, offset)
                if not index or info >> 4 not in (1, 2):
                    continue
                end = table.find(b"\0", name)
                if end < 0:
                    raise ValueError("Unterminated symbol name")
                result[table[name:end].decode("ascii")] = {"rva": hex(address), "size": size}
        return result

    def at(self, address, size):
        for section in self.sections:
            if section[1] != 8 and section[3] <= address and address + size <= section[3] + section[5]:
                offset = section[4] + address - section[3]
                return self.data[offset:offset + size]
        raise ValueError(f"No file-backed section at {address:#x}")


def inspect_library(path):
    elf = Elf(path)
    symbols = elf.symbols()
    interfaces = {}
    for key, fragment in INTERFACES.items():
        matches = {name: value for name, value in symbols.items() if fragment in name}
        if len(matches) != 1:
            raise ValueError(f"Expected one exported {key}; found {len(matches)}")
        name, details = next(iter(matches.items()))
        interfaces[key] = {"symbol": name, **details}
    for key, suffix in {
        "dispatcher": "CGuiManagerDefaultMessageHandlerElement22XGui_BE_Message_HandleEN9NXBaseGui17TXGuiMessageTypesEjjjj",
        "frame_call": "CGuiManager12FrameCall_DoEv",
        "input_singleton": "CInputManager13sTheObjectPtrE",
        "gui_singleton": "CGuiManager13sTheObjectPtrE",
    }.items():
        scope = "NC2InGameGuiInputManager" if key == "input_singleton" else "NC2InGameGuiManager"
        matches = [(name, value) for name, value in symbols.items() if scope in name and suffix in name]
        if len(matches) != 1:
            raise ValueError(f"Expected one exported {key}; found {len(matches)}")
        name, details = matches[0]
        interfaces[key] = {"symbol": name, **details}
    digest = hashlib.sha256(elf.data).hexdigest()
    result = {"sha256": digest, "reviewed_binary": digest == REVIEWED_SHA256, "interfaces": interfaces}
    if digest == REVIEWED_SHA256:
        # Addresses are from the reviewed constructor disassembly, never runtime call addresses.
        shortcuts = []
        for i in range(84):
            assignment, key, char, ctrl, shift = struct.unpack("<IIBBBx", elf.at(0x12C1F0 + i * 12, 12))
            if assignment in (37, 38, 39):
                shortcuts.append({"assignment": assignment, "virtual_key": key,
                                  "character": chr(char) if char else None,
                                  "control": bool(ctrl), "shift": bool(shift)})
        result["native_action_shortcuts"] = shortcuts
        result["reviewed_contract"] = {
            "human_handler_bytes": 104, "human_handler_max_commands": 25,
            "gui_user_message": 0x800, "human_command_message_base": 3000,
            "vehicle_command_message_base": 3600, "house_command_message_base": 3300,
            "gui_default_handler_member_offset": "0x288",
            "human_group_policy": "intersection of engine-provided group-mode commands",
            "runtime_calls_verified": False,
        }
    return result


def inspect_device(adb_path, serial):
    prefix = [adb_path] + (["-s", serial] if serial else [])

    def run(*args):
        process = subprocess.run(prefix + list(args), capture_output=True, text=True, timeout=15)
        return {"exit": process.returncode, "stdout": process.stdout.strip(), "stderr": process.stderr.strip()}

    result = {"shell_identity": run("shell", "id"),
              "build_debuggable": run("shell", "getprop", "ro.debuggable"),
              "build_type": run("shell", "getprop", "ro.build.type"),
              "boot_flash_locked": run("shell", "getprop", "ro.boot.flash.locked"),
              "run_as_game": run("shell", "run-as", "com.runesoft.cultures", "id"),
              "su_executable": run("shell", "command", "-v", "su")}
    pid = run("shell", "pidof", "com.runesoft.cultures")["stdout"]
    if re.fullmatch(r"[0-9]+", pid):
        result["game_maps_access"] = run("shell", "head", "-n", "1", f"/proc/{pid}/maps")
        result["game_memory_access"] = run("shell", "head", "-c", "1", f"/proc/{pid}/mem")
    result["runtime_attachment_attempted"] = False
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--library", type=Path)
    parser.add_argument("--adb", help="Also perform read-only device checks")
    parser.add_argument("--serial")
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    if not args.library and not args.adb:
        parser.error("Provide --library and/or --adb")
    report = {}
    if args.library:
        report["library"] = inspect_library(args.library)
    if args.adb:
        report["device"] = inspect_device(args.adb, args.serial)
    output = json.dumps(report, indent=2) + "\n"
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(output, encoding="utf-8")
        print(f"Wrote read-only direct-action report: {args.out}")
    else:
        print(output, end="")


if __name__ == "__main__":
    main()
