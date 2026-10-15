"""Deploy and run our controller helper beside the owner's unchanged Android game."""

import argparse
import subprocess
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REMOTE = "/data/local/tmp/northland-thor"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial")
    commands = parser.add_subparsers(dest="action", required=True)
    commands.add_parser("install", help="Copy only our helper; never replace the game")
    start = commands.add_parser("start", help="Run in this terminal; Ctrl+C releases input and stops")
    start.add_argument("--background", action="store_true", help="Keep running after USB disconnect until reboot or stop")
    commands.add_parser("stop", help="Request clean shutdown")
    commands.add_parser("panel", help="Show the companion's cursor and bottom-screen commands")
    key = commands.add_parser("key")
    key.add_argument("name")
    for command in ("click", "move"):
        p = commands.add_parser(command)
        p.add_argument("x", type=int, choices=range(1920), metavar="X[0..1919]")
        p.add_argument("y", type=int, choices=range(1080), metavar="Y[0..1079]")
        if command == "click":
            p.add_argument("button", choices=("left", "right"))
    args = parser.parse_args()
    adb = [args.adb] + (["-s", args.serial] if args.serial else [])

    def run(*parts):
        subprocess.run(adb + list(parts), check=True)

    def active():
        processes = subprocess.run(adb + ["shell", "ps", "-A", "-o", "ARGS"],
                                   capture_output=True, text=True, check=True).stdout
        return any(line.startswith("app_process64 /system/bin org.northland.thor.InputBridge controller")
                   for line in processes.splitlines())

    def stop():
        run("shell", "mkdir", "-p", REMOTE)
        run("shell", "touch", REMOTE + "/stop")
        deadline = time.monotonic() + 6
        while active():
            if time.monotonic() > deadline:
                parser.error("Bridge did not stop cleanly; it has not been killed or replaced")
            time.sleep(.15)

    if args.action == "install":
        artifact = ROOT / "artifacts/android/northland-bridge.jar"
        if not artifact.is_file():
            parser.error("Build first with bash scripts/build-android.sh (WSL works on Windows)")
        stop()
        run("shell", "mkdir", "-p", REMOTE)
        run("push", str(artifact), REMOTE + "/bridge.jar")
        panel = ROOT / "artifacts/android/northland-panel.apk"
        if panel.is_file():
            run("install", "-r", str(panel))
            run("shell", "appops", "set", "org.northland.thor", "SYSTEM_ALERT_WINDOW", "allow")
    elif args.action == "stop":
        stop()
    elif args.action == "panel":
        run("shell", "am", "start", "-n", "org.northland.thor/.SetupActivity", "--ez", "show-panel", "true")
    else:
        if args.action == "start" and active():
            parser.error("Bridge is already running; use stop before restarting")
        inputs = ["controller"] if args.action == "start" else [args.action]
        if args.action == "key":
            if not args.name.replace("-", "").isalnum():
                parser.error("Invalid command name")
            inputs.append(args.name)
        elif args.action in ("click", "move"):
            inputs += [str(args.x), str(args.y)]
            if args.action == "click":
                inputs.append(args.button)
        try:
            if args.action == "start" and args.background:
                # Constant shell code: no user text or paths are interpolated.
                run("shell", "CLASSPATH=/data/local/tmp/northland-thor/bridge.jar "
                    "nohup /system/bin/app_process64 /system/bin org.northland.thor.InputBridge controller "
                    "> /data/local/tmp/northland-thor/bridge.log 2>&1 < /dev/null &")
                time.sleep(1)
                if not active():
                    run("shell", "cat", REMOTE + "/bridge.log")
                    parser.error("Bridge failed to start")
                print("Northland controller bridge is running in the background.")
            else:
                run("shell", "CLASSPATH=" + REMOTE + "/bridge.jar", "/system/bin/app_process64",
                    "/system/bin", "org.northland.thor.InputBridge", *inputs)
        except KeyboardInterrupt:
            stop()


if __name__ == "__main__":
    main()
