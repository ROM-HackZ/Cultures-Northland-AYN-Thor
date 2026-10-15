# Northland on AYN Thor

I wanted to play Cultures: Northland on my Thor with proper controls and something
useful on the bottom screen. This adds a cursor, a command wheel and big, labeled
action buttons to the official Android game.

The game stays on the top screen. The left stick moves your cursor, the right
stick pans the map, and the bottom screen holds villager actions and shortcuts
for settlement and game menus. Touch still works as usual.

You need your own copy of [Cultures: Northland for Android](https://play.google.com/store/apps/details?id=com.runesoft.cultures).
This is a companion app, not a new Northland engine. It leaves the purchased app
and its saves alone. No root, modified game APK or PC game files needed.

This is an early prototype running on a real Thor. Setup still needs a computer
and a local build. There's no ready-to-install release yet.

## Controls

| Button or stick | What it does |
| --- | --- |
| Left stick | Move the cursor. Push gently for finer movement |
| Right stick | Pan the map |
| LT or A | Tap to select or confirm. Hold and move the left stick to draw a selection box |
| LT or A held still | Use the game's original hold action. Over terrain, hold for about half a second and release to issue an order |
| X | Issue an order at the cursor immediately |
| B | Deselect, cancel, go back or close the current window |
| Y | Open the selected character's original action menu |
| LB | Hold to add to a selection with LT or A |
| RB | Select the next civilian |
| RT | Hold for the command wheel. Choose with the right stick, press A to confirm or B to cancel |
| R3 | Cycle zoom levels |
| D-pad Up | Headquarters |
| D-pad Down | Character list |
| Start | Pause |
| Select | Toggle the game's map |

To move someone, tap LT over them, move the cursor to the destination, then hold
LT still and release. They stay selected. X skips the hold and sends the order
straight away. Holding LT over a character opens their action menu instead.

The RT wheel has Actions, Build, Profession, Assign home, Workplace, Characters,
Save and Back. Save opens the game's save window so you can choose a slot.

## The bottom screen

| Page | What's there |
| --- | --- |
| Villager | Actions, build, profession, home, workplace, roads, next civilian and next scout |
| Settlement | Headquarters, character list, statistics, technology, diplomacy, map, zoom and help |
| Session | Save, load, options, help, pause, speed, map and zoom |

Select a character, then tap **Villager > Actions**. Their circular menu appears
in the game and the bottom screen shows the commands we can read as text buttons.
Profession and equipment buttons open the original game windows. Commands that
need a destination leave you to choose it on the top screen.

The list stays put while you pan. Tap **Refresh actions**, or reopen Actions,
after selecting someone else or changing their profession. **Next page** shows
any commands beyond the first 15.

The companion reads the menu when you open or refresh Actions, then checks the
chosen command again when you tap a button. It can miss buttons. Y always opens
the original menu if something is missing. We know 38 character commands so far,
but only civilian and builder menus have had live checks. Other professions,
soldiers, women and groups still need testing.

## Getting it running

You'll need an AYN Thor with USB debugging enabled, your purchased Android game
installed, and a computer with Python 3.11 or newer and ADB. Plug the Thor in and
accept its USB debugging prompt.

### Build

Clone this repo or download and unzip it. The build needs Java 17, Android SDK
platform 36, build-tools 36.0.0 and `zip`. Set `ANDROID_HOME` to your SDK folder.
Run this from the project folder in Linux, macOS or WSL on Windows:

```sh
bash scripts/build-android.sh
```

The build creates `artifacts/android/northland-panel.apk` and
`artifacts/android/northland-bridge.jar`. It doesn't need any game files.

### Install and start

These commands run on Windows, macOS or Linux. On Windows, use Python and ADB
from Windows after building in WSL. Run `adb devices` to find the Thor's serial,
then replace `DEVICE_SERIAL` below with it:

```sh
python scripts/android-controller.py --serial DEVICE_SERIAL install
python scripts/android-controller.py --serial DEVICE_SERIAL panel
python scripts/android-controller.py --serial DEVICE_SERIAL start --background
```

Add `--adb "PATH_TO_ADB"` before `--serial` if ADB isn't on your PATH. Use
`python3` instead of `python` if that's what your computer calls it.

Open Northland on the top screen and release the sticks and buttons to enable
the controls. The installer adds our companion, copies the input helper and
allows the companion to draw the cursor and bottom panel over the game.

After rebooting the Thor, connect it to your computer and run the `panel` and
`start --background` commands again. You don't need to reinstall or rebuild.
The helper is set up to keep running after unplugging USB, but that still needs
a hands-on check.

To stop the controls:

```sh
python scripts/android-controller.py --serial DEVICE_SERIAL stop
```

Use **Hide panel** in the Northland Thor app to hide the cursor and bottom
screen buttons. Switching away from Northland hides them too. Controls release
when the game loses focus or the companion disconnects. Release the sticks and
buttons before returning to play.

## What works, what needs work

| Part | Current state |
| --- | --- |
| Game and displays | Original Android tutorial running on the top screen, companion controls on the bottom |
| Controller input | Cursor, camera, selection boxes, orders, zoom and wheel confirmation checked through the Thor's controller input path |
| Character actions | Civilian and builder menus checked, including profession, equipment and building-site targeting. Some screen conditions can hide a button from recognition |
| Stable action list | Camera movement and closing the original menu leave the bottom buttons unchanged. Refresh with no selection clears the list |
| Returning from other apps | Held input releases. One return test restarted Northland, so session recovery still needs work |
| Longer play | Full campaigns, every character type, groups, text entry, sound and long sessions still need proper testing |
| Setup | Computer required after each reboot. A simple installer and phone-only activation are unfinished |

The current build assumes the tested Thor setup: Android 13, a 1920x1080 top
screen, a 1240x1080 bottom screen on display 4, and the built-in Odin Controller.
Other layouts and handhelds haven't been checked. Physical stick feel still needs
more playtesting.

Found something wrong? [Open an issue](https://github.com/ROM-HackZ/Cultures-Northland-AYN-Thor/issues).
Say what you selected, what you pressed and what happened. Include the game
version and pictures of both screens if they help.

## Source and testing

| Path | What's there |
| --- | --- |
| `android/companion/` | Setup app, cursor, command wheel and bottom screen |
| `android/bridge/` | Controller input, game shortcuts and action-menu recognition |
| `android/shared/` | Command names and the messages between the app and helper |
| `scripts/build-android.sh` | Local APK and helper build |
| `scripts/android-controller.py` | Install, show, start and stop the companion and helper |
| `scripts/verify-android-controller.py` | Device checks that record input state and both screens |
| `tests/android/` | Local checks for action lists, menu recognition and message handling |
| `docs/` | Design notes, device results and the earlier Windows experiment |

Run the action-list checks locally with Java installed:

```sh
bash scripts/check-action-snapshots.sh
```

The recognition checks need your own private reference captures. The
[device notes](docs/android-controller.md) have the commands and results.
[Design notes](docs/port-design.md) cover how the companion works and why it
can't read the game's selection directly. The old GameHub tools and profiles
remain as a [record of the Windows tests](docs/compatibility.md). They aren't
needed for this Android setup.

## Credits

Northland and its artwork belong to their owners. This is an independent fan
project, with no involvement from the game's developers, publisher or AYN.
The Thor companion was developed with Codex assistance and testing on the device.
No game files, game APKs or saves are included in this repository.

[martianboy's Cultures BMD research](https://github.com/martianboy/cultures2-wasm/blob/master/src/bmd.rs)
helped check the sprite format. The metadata tool reads your own files locally;
we haven't bundled that project's decoder or the game's artwork.

My [Generals Zero Hour project for the Thor](https://github.com/ROM-HackZ/generals-zero-hour-ayn-thor)
is here too.
