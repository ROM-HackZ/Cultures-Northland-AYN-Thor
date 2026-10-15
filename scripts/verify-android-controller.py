"""Exercise Thor evdev -> bridge -> original Android Northland. Use an idle tutorial.

This sends synthetic events through the actual Odin Controller node, rather than
calling our button mapping directly. It cannot assess physical stick feel or sound.
The user's controls must be released during the short test.
"""

import argparse
import json
import re
import subprocess
import time
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial")
    parser.add_argument("--case", required=True, choices=("camera", "cursor", "select", "order", "drag", "actions", "back", "zoom", "wheel", "wheel-confirm", "focus", "panel-loss"))
    parser.add_argument("--out", type=Path, default=Path("artifacts/controller-check"))
    parser.add_argument("--pan-direction", choices=("left", "right"), default="right",
                        help="Camera test direction; choose away from the current map edge")
    args = parser.parse_args()
    adb = [args.adb] + (["-s", args.serial] if args.serial else [])

    def shell(*parts):
        return subprocess.run(adb + ["shell", *parts], capture_output=True, text=True, check=True).stdout

    def state():
        return json.loads(shell("cat", "/data/local/tmp/northland-thor/state.json"))

    def await_state(check):
        deadline = time.monotonic() + 3
        while time.monotonic() < deadline:
            current = state()
            if check(current):
                return current
            time.sleep(.08)
        raise RuntimeError("Bridge did not reach expected state: " + repr(current))

    dump = shell("dumpsys", "input")
    if not any(line.startswith("app_process64 /system/bin org.northland.thor.InputBridge controller")
               for line in shell("ps", "-A", "-o", "ARGS").splitlines()):
        parser.error("The controller bridge must be running")
    start = dump.index("FocusedWindows:")
    focus = dump[start:dump.index("FocusRequests:", start)]
    if not re.search(r"displayId=0, name='[^'\n]* com\.runesoft\.cultures/", focus):
        parser.error("Northland must be focused on the top screen")
    listing = shell("getevent", "-lp")
    devices = re.findall(r'add device \d+: (/dev/input/event\d+)\s+name:\s+"([^"]+)"', listing)
    matches = [path for path, name in devices if name == "Odin Controller"]
    if len(matches) != 1:
        parser.error("Exactly one Odin Controller is required")
    device = matches[0]
    initial = await_state(lambda s: s["focused"] and s["armed"])
    if initial["mouse"] or initial["keys"] or initial["wheel"] or initial.get("navigation",False):
        parser.error("Release all controller controls before testing")
    args.out.mkdir(parents=True, exist_ok=True)

    def capture(phase):
        for suffix, surface in (("", "4630946441858561667"), ("-bottom", "4630946482288158084")):
            r = subprocess.run(adb + ["exec-out", "screencap", "-p", "-d", surface],
                               capture_output=True, check=True)
            if not r.stdout.startswith(b"\x89PNG\r\n\x1a\n"):
                raise RuntimeError("Device did not return a screenshot")
            (args.out / f"{args.case}-{phase}{suffix}.png").write_bytes(r.stdout)

    # Only these known controller axes and buttons can be written by this tool.
    cases = {
        "camera": [(3, 2, -20000 if args.pan_direction == "left" else 20000)],
        "cursor": [(3, 0, 20000)],
        "select": [(1, 0x130, 1)],
        "order": [(1, 0x133, 1)],
        "drag": [(1, 0x130, 1), (3, 0, 20000)],
        "actions": [(1, 0x134, 1)],
        "back": [(1, 0x131, 1)],
        "zoom": [(1, 0x13e, 1)],
        "wheel": [(3, 9, 32767), (3, 0, 32767)],
        "focus": [(3, 2, 20000)],
        "wheel-confirm": [(3, 9, 32767), (3, 0, -32767)],
        "panel-loss": [(3, 2, 20000)],
    }
    events = cases[args.case]

    def emit(values):
        # Numeric arguments plus a node discovered by an exact hardware name.
        lines = [f"sendevent {device} {kind} {code} {value}" for kind, code, value in values]
        lines.append(f"sendevent {device} 0 0 0")
        subprocess.run(adb + ["shell", "sh"], input="\n".join(lines) + "\n", text=True, check=True)

    capture("before")
    held = None
    return_to_game = False
    restart_panel = False
    try:
        emit(events)
        if args.case in ("camera", "focus", "panel-loss"):
            pan_key = 21 if args.case == "camera" and args.pan_direction == "left" else 22
            held = await_state(lambda s: pan_key in s["keys"])
        elif args.case == "cursor":
            held = await_state(lambda s: s["x"] > initial["x"])
        elif args.case in ("wheel", "wheel-confirm"):
            choice = 6 if args.case == "wheel-confirm" else 2
            held = await_state(lambda s: s["wheel"] and s["choice"] == choice)
            assert (held["x"],held["y"])==(initial["x"],initial["y"]), "Left stick moved cursor under the command wheel"
            if args.case=="wheel":
                events.extend([(3,2,-32767),(3,5,32767)])
                emit(events[-2:]);time.sleep(.3)
                held=state()
                assert held["choice"]==choice and not held["keys"] and not held["mouse"], "Right stick changed wheel choice or leaked input"
            if args.case == "wheel-confirm":
                emit([(1, 0x130, 1)])
                try:
                    held = await_state(lambda s: not s["wheel"] and s["mouse"] == 0)
                finally:
                    emit([(1, 0x130, 0)])
        elif args.case in ("select", "order"):
            mask = 1 if args.case == "select" else 2
            held = await_state(lambda s: s["mouse"] == mask)
        elif args.case == "drag":
            held = await_state(lambda s: s["mouse"] == 1 and s["x"] > initial["x"])
            emit([(1, 0x133, 1)])
            try:
                time.sleep(.2)
                assert state()["mouse"] == 1, "X interrupted an A drag"
            finally:
                emit([(1, 0x133, 0)])
        else:
            key = {"actions": 62, "back": 111, "zoom": 54}[args.case]
            held = await_state(lambda s: key in s["keys"])
        time.sleep(.35)
        capture("held")
        if args.case == "focus":
            shell("am", "start", "--display", "0", "-n", "org.northland.thor/.SetupActivity")
            return_to_game = True
            away = await_state(lambda s: not s["focused"] and not s["armed"] and not s["keys"] and not s["mouse"])
            capture("away")
            shell("am", "start", "--display", "0", "-n", "com.runesoft.cultures/com.rune_soft.culturesnorthland.MainActivity")
            return_to_game = False
            returning = await_state(lambda s: s["focused"] and not s["armed"] and not s["keys"] and not s["mouse"])
            capture("waiting-for-neutral")
            held = {"while-held": held, "away": away, "returned-with-control-held": returning}
        if args.case == "panel-loss":
            shell("am", "force-stop", "org.northland.thor")
            restart_panel = True
            disconnected = await_state(lambda s: s["focused"] and not s["armed"] and not s["keys"])
            capture("panel-stopped")
            shell("am", "start", "--display", "0", "-n", "org.northland.thor/.SetupActivity", "--ez", "show-panel", "true")
            restart_panel = False
            time.sleep(1)
            held = {"while-panning": held, "panel-disconnected": disconnected}
    finally:
        try:
            emit([(kind, code, 0) for kind, code, _ in events])
        finally:
            if return_to_game:
                shell("am", "start", "--display", "0", "-n", "com.runesoft.cultures/com.rune_soft.culturesnorthland.MainActivity")
            if restart_panel:
                shell("am", "start", "--display", "0", "-n", "org.northland.thor/.SetupActivity", "--ez", "show-panel", "true")
    released = await_state(lambda s: s["armed"] and not s["keys"] and not s["mouse"] and not s["wheel"])
    time.sleep(.15)
    capture("after")
    report = {"case": args.case, "synthetic_evdev": True, "initial": initial, "held": held,
              "released": released, "engine_result": "Review before/held/after screenshots; state alone is not game proof"}
    (args.out / f"{args.case}.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"{args.case}: controller event path and release passed. Inspect screenshots in {args.out}")


if __name__ == "__main__":
    main()
