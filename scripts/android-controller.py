"""Deploy and run our controller helper beside the owner's unchanged Android game."""

import argparse
import subprocess
import time
from pathlib import Path
from northland_session import open_game_args
from setup_backend import helper_active

ROOT = Path(__file__).resolve().parent.parent
REMOTE = "/data/local/tmp/northland-thor"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial")
    parser.add_argument("--artifacts",type=Path,default=ROOT / "artifacts/android",
                        help="Folder containing northland-panel.apk and northland-bridge.jar")
    commands = parser.add_subparsers(dest="action", required=True)
    commands.add_parser("install", help="Copy only our helper; never replace the game")
    start = commands.add_parser("start", help="Run in this terminal; Ctrl+C releases input and stops")
    start.add_argument("--background", action="store_true", help="Keep running after USB disconnect until reboot or stop")
    commands.add_parser("stop", help="Request clean shutdown")
    commands.add_parser("panel", help="Show the companion's cursor and bottom-screen commands")
    commands.add_parser("game", help="Open Northland or return to its existing game screen")
    commands.add_parser("setup",help="Install/update our app and activate controls in one step")
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
        return helper_active(adb)

    def stop():
        run("shell", "mkdir", "-p", REMOTE)
        run("shell", "touch", REMOTE + "/stop")
        deadline = time.monotonic() + 6
        while active():
            if time.monotonic() > deadline:
                parser.error("Bridge did not stop cleanly; it has not been killed or replaced")
            time.sleep(.15)

    def install():
        artifact=args.artifacts / "northland-bridge.jar"
        panel=args.artifacts / "northland-panel.apk"
        if not artifact.is_file() or not panel.is_file():
            parser.error("Choose a build folder containing northland-panel.apk and northland-bridge.jar")
        stop()
        run("shell", "mkdir", "-p", REMOTE)
        run("push", str(artifact), REMOTE + "/bridge.jar")
        run("install", "-r", str(panel))
        run("shell", "appops", "set", "org.northland.thor", "SYSTEM_ALERT_WINDOW", "allow")

    def panel():
        run("shell", "am", "start", "-n", "org.northland.thor/.SetupActivity", "--ez", "show-panel", "true")

    def background():
        if active():
            print("Controls are already active.")
            return
        run("shell", "CLASSPATH=/data/local/tmp/northland-thor/bridge.jar "
            "nohup /system/bin/app_process64 /system/bin org.northland.thor.InputBridge controller "
            "> /data/local/tmp/northland-thor/bridge.log 2>&1 < /dev/null &")
        time.sleep(1)
        if not active():
            run("shell", "cat", REMOTE + "/bridge.log")
            parser.error("Bridge failed to start")
        print("Northland controller bridge is running in the background.")

    if args.action == "install":
        install()
    elif args.action == "setup":
        install();panel();background()
        print("Setup complete. Open Northland and release the controls.")
    elif args.action == "stop":
        stop()
    elif args.action == "panel":
        panel()
    elif args.action == "game":
        run("shell", *open_game_args())
    else:
        if args.action == "start" and not args.background and active():
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
                background()
            else:
                run("shell", "CLASSPATH=" + REMOTE + "/bridge.jar", "/system/bin/app_process64",
                    "/system/bin", "org.northland.thor.InputBridge", *inputs)
        except KeyboardInterrupt:
            stop()


if __name__ == "__main__":
    main()
