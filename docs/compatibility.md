# Northland compatibility results

## Current result

Tests on October 14, 2026 used the owner's Steam installation and an AYN Thor running Android 13. GameHub Lite 5.1.8 can run its Windows desktop and our 32-bit diagnostic executable. Northland has not reached its main menu or a mission.

The manual diagnostic run displayed **Steam Error — Application load error T:0000065615**. A normal child-process launch exited with `0x00000035`. This number alone does not establish a missing DLL. Launch through the owner's signed-in Steam library before treating the failure as a graphics or audio problem.

| Check | Result |
| --- | --- |
| Original game files copied to Thor | 150 files, 342,436,873 bytes |
| Device executable and main archive hashes | Match the PC inventory |
| GameHub Windows desktop | Renders and accepts input |
| Own 32-bit Windows diagnostic program | Runs; pointer size is 4 bytes |
| Load the executable's ten imported DLLs | All load in the diagnostic process |
| Load DirectDraw, DirectMusic and the game's music DLL | All load in the diagnostic process |
| Direct GameHub game launch | Returns to launcher before menu |
| Manual launch from copied game directory | Fails; Steam error observed |
| Windowed 800×600 and skipped intro | Does not resolve startup |
| Menu, mission, actual controller commands, audio, save/load | Pending a successful game launch |

Loading a DLL proves that its loader works in the probe. It does not prove that the game can use its graphics, input or audio features.

## Installation details

| Item | Value |
| --- | --- |
| Steam app | Cultures - Northland, app 351860 |
| Local Steam build | 529200 |
| Game executable | `Game.exe`, 32-bit x86 |
| Device copy | `/sdcard/Download/Northland-THOR` |
| Windows path in this GameHub container | `D:\Northland-THOR` |
| GameHub compatibility layer | `proton10.0-arm64x-2` |
| Translation component | `Fex-20251025` |
| Graphics driver component | `turnip_v25.0.0_R1` |
| Container resolution | 1280×720 |

DirectDraw is loaded dynamically by this executable. Its absence from the static import table does not mean the game uses a different renderer. The owned manual and binary strings also identify the game's older DirectMusic path.

## Reproduce the diagnostics

Read the original install without changing it:

```powershell
python scripts/inspect-install.py 'D:\Steam\steamapps\common\Cultures Northland'
```

Build the probe on a machine with MinGW:

```sh
i686-w64-mingw32-gcc -Wall -Wextra -Werror -O2 -static scripts/probe-runtime.c -o artifacts/probe-runtime.exe
```

Copy `probe-runtime.exe` and `scripts/diagnose-launch.cmd` beside the device's copied `Game.exe`. Run the CMD file inside the Windows container. It produces `northland-runtime.log`, `northland-launch.log`, `northland-syswow64.log` and `northland-system32.log` in that directory. For a separate startup trace, run `probe-runtime.exe --trace`: it debugs a new game process, logs module and exception events, and attempts to detach after 15 seconds. Compare a normal launch too: debugging can change startup behavior.

GameHub's own wrapper logs are in `Android/data/gamehub.lite/files/log/`, as documented in its [troubleshooting guide](https://github.com/Producdevity/gamehub-lite/blob/master/docs/troubleshooting.md). Keep raw logs private: they may contain account or service identifiers.

For Wine desktop input, GameHub's **Touch Controls** toggle gates the RTS touch layer. With Touch Controls and RTS Touch Controls on and Screen Trackpad off, absolute ADB taps work. With Screen Trackpad on, gestures move the existing cursor relatively. Check a screenshot after movement before clicking; Android keyboard transitions can consume the next gesture. The input helper sends logical Android coordinates without applying the display rotation a second time.

The [Cultures map development kit](https://github.com/Nithanim/cultures-map-development-kit) documents the `game.ini` display and intro settings. Their names are also present in this installed executable. The test profile changes only the copied game's new `game.ini`.

## Next compatibility checks

1. Launch Northland from the owner's signed-in Steam library in GameHub.
2. Reach the menu, start a tutorial and verify mouse selection and right-click orders.
3. Verify speech, effects and music, then save and reload the same mission.
4. If a signed-in launch still fails, compare an x64 Wine/Proton profile and DirectDraw settings while preserving the existing profile and saves.

Only then choose the runtime for the custom Thor app. A working desktop alone is insufficient to select the final runtime.
