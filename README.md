# Cultures Northland on AYN Thor

The goal is the original PC game on the Thor, with proper controller controls and a useful bottom screen. This project is at the compatibility testing stage. There is no playable custom APK yet.

Northland's first tutorial now runs on the test Thor through GameHub Lite 5.1.8. ARM64X Proton with FEX shows roughly 43–57 FPS in the tutorial, compared with 5–6 FPS in the x64/Box64 test. Villager selection and a save/reload cycle work. Legacy music is disabled while audio compatibility is investigated. Proper controller support and the bottom-screen interface are still to be built.

The PC installation is untouched. Device tests use a separate copy in `Download/Northland-THOR` and the owner's Steam download inside GameHub. Retail files, saves, APKs and diagnostic captures stay out of Git.

See [the port design](docs/port-design.md) for proposed controls and the implementation stages, and [compatibility results](docs/compatibility.md) for device findings and diagnostic commands.

## Project tools

| Tool | Purpose |
| --- | --- |
| `scripts/inspect-install.py` | Read a PC installation, hash its files and inspect executable dependencies |
| `scripts/device.py` | Check the focused Android app and send guarded taps or relative trackpad movement |
| `scripts/probe-runtime.c` | Run a 32-bit dependency and startup diagnostic inside the Windows container |
| `scripts/diagnose-launch.cmd` | Launch the copied game and capture diagnostic output |
| `scripts/prepare-steam-test.cmd` | Apply a test configuration to GameHub's Steam-installed copy and collect game logs |
| `scripts/steam-events.py` | Extract startup event names and times without printing private account fields |
| `scripts/controller-probe.c` | Inspect XInput buttons and axes inside the Windows runtime; no game controls yet |
| `profiles/startup-test.ini` | Test an 800×600 window with the intro skipped |
| `profiles/audio-isolation.ini` | Isolate startup with effects, music and CD audio disabled |
| `profiles/music-isolation.ini` | Test effects with legacy music and CD audio disabled |

Python tools need Python 3.11 or later. ADB must be installed for device commands. Build the runtime probe with a 32-bit MinGW compiler; the command is in the compatibility notes.
