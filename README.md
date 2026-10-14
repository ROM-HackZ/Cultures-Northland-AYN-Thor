# Cultures Northland on AYN Thor

The goal is the original PC game on the Thor, with proper controller controls and a useful bottom screen. This project is at the compatibility testing stage. There is no playable custom APK yet.

GameHub Lite 5.1.8 is installed on the test Thor. Its Windows desktop works, and our 32-bit diagnostic program loads Northland's tested dependencies. The copied Steam executable stops with **Steam Error: Application load error T:0000065615**. Launching through the owner's Steam library is the next compatibility test.

The PC installation is untouched. Device tests use a separate copy in `Download/Northland-THOR`. Retail files, saves, APKs and diagnostic captures stay out of Git.

See [the port design](docs/port-design.md) for proposed controls and the implementation stages, and [compatibility results](docs/compatibility.md) for device findings and diagnostic commands.

## Project tools

| Tool | Purpose |
| --- | --- |
| `scripts/inspect-install.py` | Read a PC installation, hash its files and inspect executable dependencies |
| `scripts/device.py` | Check the focused Android app and send guarded taps or relative trackpad movement |
| `scripts/probe-runtime.c` | Run a 32-bit dependency and startup diagnostic inside the Windows container |
| `scripts/diagnose-launch.cmd` | Launch the copied game and capture diagnostic output |
| `profiles/startup-test.ini` | Test an 800×600 window with the intro skipped |

Python tools need Python 3.11 or later. ADB must be installed for device commands. Build the runtime probe with a 32-bit MinGW compiler; the command is in the compatibility notes.
