"""Record Thor gamepad events and our bridge state; never inject input or record system keys."""

import argparse
import json
import queue
import re
import subprocess
import threading
import time
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial")
    parser.add_argument("--seconds", type=int, default=90)
    parser.add_argument("--out", type=Path, default=Path("artifacts/trigger-recording.jsonl"))
    args = parser.parse_args()
    adb = [args.adb] + (["-s", args.serial] if args.serial else [])
    listing = subprocess.run(adb + ["shell", "getevent", "-lp"], capture_output=True, text=True, check=True).stdout
    devices = re.findall(r'add device \d+: (/dev/input/event\d+)\s+name:\s+"([^"]+)"', listing)
    matches = [path for path, name in devices if name == "Odin Controller"]
    if len(matches) != 1:
        parser.error("Exactly one Odin Controller is required")
    events = queue.Queue()
    reader = subprocess.Popen(adb + ["shell", "getevent", "-t", matches[0]], stdout=subprocess.PIPE, text=True)

    def read():
        for line in reader.stdout:
            match = re.search(r"([0-9a-f]{4}) ([0-9a-f]{4}) ([0-9a-f]{8})\s*$", line)
            if not match:
                continue
            kind, code, value = (int(v, 16) for v in match.groups())
            if (kind == 3 and code in (0, 1, 2, 5, 9, 10, 16, 17)) or (kind == 1 and 0x130 <= code <= 0x13e):
                events.put({"event": "controller", "type": kind, "code": code, "value": value})

    threading.Thread(target=read, daemon=True).start()
    args.out.parent.mkdir(parents=True, exist_ok=True)
    start = time.monotonic()
    previous = None
    event_count = 0
    print("Recording physical gamepad events and bridge response", flush=True)
    try:
        with args.out.open("w", encoding="utf-8") as out:
            while time.monotonic() - start < args.seconds:
                batch = []
                while not events.empty():
                    batch.append(events.get_nowait())
                    event_count += 1
                result = subprocess.run(adb + ["shell", "cat", "/data/local/tmp/northland-thor/state.json"],
                                        capture_output=True, text=True)
                state = json.loads(result.stdout) if result.returncode == 0 else {"bridge": "unavailable"}
                if state != previous:
                    batch.append({"event": "bridge", "state": state})
                    previous = state
                for item in batch:
                    item["seconds"] = round(time.monotonic() - start, 3)
                    out.write(json.dumps(item) + "\n")
                out.flush()
                time.sleep(.12)
    finally:
        reader.terminate()
        try:
            reader.wait(timeout=3)
        except subprocess.TimeoutExpired:
            reader.kill()
    print(f"Recorded {event_count} gamepad events to {args.out}")


if __name__ == "__main__":
    main()
