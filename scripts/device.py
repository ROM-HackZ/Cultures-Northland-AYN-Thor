"""Guard ADB taps with the focused app and the display's logical bounds."""

import argparse
import re
import subprocess
import time
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial")
    parser.add_argument("--display", type=int, default=0)
    parser.add_argument("--expect-package")
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("focus")
    capture = commands.add_parser("capture", help="Save a physical display screenshot without device temp files")
    capture.add_argument("--surface", required=True, type=int)
    capture.add_argument("--out", required=True, type=Path)
    tap = commands.add_parser("tap")
    tap.add_argument("x", type=float)
    tap.add_argument("y", type=float)
    double_tap = commands.add_parser("double-tap", help="Two taps without separate ADB connection delays")
    double_tap.add_argument("x", type=float)
    double_tap.add_argument("y", type=float)
    trackpad = commands.add_parser("trackpad", help="Move relative to GameHub's current cursor")
    trackpad.add_argument("dx", type=int)
    trackpad.add_argument("dy", type=int)
    trackpad.add_argument("--click", action="store_true")
    args = parser.parse_args()
    prefix = [args.adb] + (["-s", args.serial] if args.serial else [])

    if args.command == "capture":
        result = subprocess.run(prefix + ["exec-out", "screencap", "-p", "-d", str(args.surface)],
                                check=True, capture_output=True)
        if not result.stdout.startswith(b"\x89PNG\r\n\x1a\n"):
            parser.error("Device did not return a PNG screenshot")
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_bytes(result.stdout)
        print(f"Captured display surface {args.surface}: {args.out}")
        return

    def adb(*values):
        return subprocess.run(prefix + list(values), check=True, capture_output=True, text=True).stdout

    dump = adb("shell", "dumpsys", "input")
    focused_section = dump.split("FocusedWindows:", 1)[1].split("FocusRequests:", 1)[0]
    match = re.search(rf"displayId={args.display}, name='([^']+)'", focused_section)
    if not match:
        parser.error(f"Display {args.display} has no focused window")
    name = match[1]
    if args.expect_package and f" {args.expect_package}/" not in name:
        parser.error(f"Expected {args.expect_package}, focused window is {name}")
    geometry = re.search(rf"Display: {args.display}\s+logicalSize=(\d+)x(\d+)", dump)
    if not geometry:
        parser.error("Display logical bounds are missing")
    width, height = int(geometry[1]), int(geometry[2])
    print(f"Focused window: {name}")
    print(f"Logical display: {width}x{height}")
    if args.command == "focus":
        return
    if args.command == "trackpad":
        x, y = min(500, width // 2), min(500, height // 2)
        if not (0 <= x + args.dx < width and 0 <= y + args.dy < height):
            parser.error("Trackpad gesture is outside the display")
        adb("shell", "input", "-d", str(args.display), "swipe", str(x), str(y),
            str(x + args.dx), str(y + args.dy), "400")
        if args.click:
            time.sleep(1)
            adb("shell", "input", "-d", str(args.display), "tap", str(x), str(y))
        return
    if not (0 <= args.x < width and 0 <= args.y < height):
        parser.error("Tap is outside the logical display bounds")
    # adb input already uses logical display coordinates. Applying the input
    # window's rotation again would turn a correct point into a wrong one.
    print(f"Logical tap: {args.x},{args.y}")
    if args.command == "double-tap":
        script = f"input -d {args.display} tap {round(args.x)} {round(args.y)}\n" * 2
        subprocess.run(prefix + ["shell", "sh"], input=script, text=True, check=True)
        return
    adb("shell", "input", "-d", str(args.display), "tap", str(round(args.x)), str(round(args.y)))


if __name__ == "__main__":
    main()
