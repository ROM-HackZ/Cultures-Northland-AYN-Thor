# Northland compatibility results

## Current result

Tests on October 14–15, 2026 used the owner's Steam installation and an AYN Thor running Android 13. Northland's first tutorial runs in GameHub Lite 5.1.8. Villager selection opens Knut's original needs and equipment panels. A new save appeared in the load list, and reloading returned to the tutorial with the game's “Game loaded” message.

ARM64X Proton with FEX shows about 43–57 FPS in the first tutorial; x64 Proton with Box64 showed 5–6 FPS. These are short HUD observations, not a campaign performance benchmark. Turning off the legacy audio systems allowed the tutorial to load; the latest test restores effects while leaving DirectMusic and CD audio disabled. Audible output is awaiting confirmation.

The earlier manual run from the external copy displayed **Steam Error — Application load error T:0000065615**. A normal child-process launch exited with `0x00000035`. This number alone does not establish a missing DLL. After the owner signed in, GameHub downloaded Northland through its Steam integration. The successful menu test uses that internal installation.

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
| Signed-in ARM64X/FEX with full Steam | Steam login succeeds; game exits before menu |
| x64 Proton + Box64 with full Steam | Stalls at Steam login |
| x64 Proton + Box64 with Lightweight Steam | Opens a black window before the startup configuration is applied |
| Windowed 800×600 and skipped intro, applied to the internal Steam copy | Title screen, main menu and tutorial list work |
| First tutorial with normal audio | Loading art appears; game process exits before gameplay |
| x64/Box64 tutorial with all audio disabled | Runs at about 5–6 FPS; selection, save and reload pass |
| ARM64X/FEX tutorial with all audio disabled | Runs; about 44 FPS with the mission panel open |
| ARM64X/FEX tutorial with only music disabled | Runs; roughly 43–57 FPS in short observations |
| Full Steam client, online | Login succeeds, but a later run stalls during library sync |
| Full Steam client, offline | Login and library sync complete; tutorial runs |
| Windows XInput probe | Runs; sees controller 0 and records virtual A plus both stick axes |
| Physical Thor controls, actual game commands and audible output | Still pending |

Loading a DLL proves that its loader works in the probe. It does not prove that the game can use its graphics, input or audio features.

## Installation details

| Item | Value |
| --- | --- |
| Steam app | Cultures - Northland, app 351860 |
| Local Steam build | 529200 |
| Game executable | `Game.exe`, 32-bit x86 |
| External diagnostic copy | `/sdcard/Download/Northland-THOR` |
| External Windows path | `D:\Northland-THOR` |
| Internal Steam copy used for menu test | `/data/data/gamehub.lite/files/Steam/steamapps/common/Cultures Northland` |
| Current compatibility layer | `proton10.0-arm64x-2` |
| Current translation component | `Fex-20251025` |
| Current Steam client | `steam_9866233`, Offline Mode on |
| Current startup profile | `profiles/music-isolation.ini` |
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

For the internal Steam copy, push a chosen profile as `Download/Northland-THOR/game.ini`, then run `scripts/prepare-steam-test.cmd` from that directory in the game's own container desktop. The script checks for the internal game's executable, backs up an existing configuration once, applies the test file and copies game-specific logs to the external diagnostic directory. The internal Steam installation had no `game.ini` before the first application. Its logs directory was empty after the first tutorial exit.

Changing from ARM64X to x64 left GameHub's per-game CPU translator empty. Installing the x64 compatibility component alone was insufficient: install Box64 in PC Emulator settings and select it in the game's CPU Translator setting too. Native dropdown taps may only focus an option; confirm with the controller A button and verify the displayed value.

Lightweight Mode reached gameplay once with ARM64X but later launches displayed `Application load error 6:0000065432`. Full Steam in Offline Mode then reached gameplay. After a later container-desktop visit, full Steam stayed at initialization; stopping and reopening GameHub let the next launch reach the menu. This is an observed recovery, not an established root cause. Startup must be tested across repeated clean launches before calling this profile reliable. Stopping the app does not clear its data.

`fx_off`, `dm_off` and `cda_off` are presence flags in the documented profile. To enable a subsystem, omit its `_off` line; do not rely on adding `0` after the flag. Early isolation runs used `1` after the flags. The later music-only profile omits `fx_off` entirely.

The input probe builds with the same compiler as the dependency probe:

```sh
i686-w64-mingw32-gcc -Wall -Wextra -Werror -O2 -static -mwindows scripts/controller-probe.c -o artifacts/controller-probe.exe -luser32 -lgdi32
```

It logs only controller connection, buttons, triggers and axes beside its executable. It neither injects input nor modifies Northland. On the Thor, it loads `xinput1_4.dll`, reports controller 0 connected, and records the virtual A button as `0x1000` plus nonzero values on both virtual sticks. These events establish the virtual input path; the physical buttons and sticks still need their own test. Leave the probe open while that test is pending.

## Next compatibility checks

1. Confirm effects and speech with the music-only isolation profile, then isolate and repair DirectMusic startup.
2. Verify right-click orders and pan behavior in the tutorial. Repeat save/load on the faster profile.
3. Prove controller input delivery and focus behavior, then build the controller prototype.
4. Compare DirectDraw and translator settings if needed, preserving the menu-working profile and saves.

Only then choose the runtime for the custom Thor app. A working desktop alone is insufficient to select the final runtime.
