"""Print only Steam startup event names and times from private GameHub logs."""

import argparse
import re
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=Path)
    args = parser.parse_args()
    # Never echo entire log lines: adjacent fields can contain account details.
    event = re.compile(r"^(\d\d:\d\d:\d\d\.\d{3}).*SteamStatus event=\s*([a-zA-Z0-9_]+)\s*$")
    for line in args.log.read_text(encoding="utf-8", errors="replace").splitlines():
        match = event.match(line)
        if match:
            print(f"{match[1]} {match[2]}")


if __name__ == "__main__":
    main()
