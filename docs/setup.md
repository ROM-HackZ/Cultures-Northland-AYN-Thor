# Setting up Northland Thor

Install your purchased Android copy of Cultures: Northland first. This project
adds the controller helper and bottom-screen panel. It does not include the game.

You have two ways to activate the controls. USB setup works without Shizuku.
On-device activation uses Shizuku and Wireless debugging. Neither requires root.

## Computer setup window

You need Python 3.11 or newer with Tk, Android Platform Tools, USB debugging on
the Thor and a local build or extracted setup bundle. The bundle already contains
our APK and helper, so players do not need Java or the Android SDK build tools.

| Computer | Open |
| --- | --- |
| Windows | `Setup.cmd`, or run `python scripts/setup-ui.py` |
| macOS | `Setup.command`, or run `python3 scripts/setup-ui.py` |
| Linux | `sh Setup.sh`, or run `python3 scripts/setup-ui.py` |

If macOS does not allow double-clicking the command file, use its Terminal
command. Linux distributions may package Tk separately from Python.

1. Plug in the Thor and accept its USB debugging prompt.
2. Choose `adb` from Android Platform Tools if the window did not find it.
3. Choose the folder containing `northland-panel.apk` and `northland-bridge.jar`.
   The default is `artifacts/android` in this project or setup bundle.
4. Click **Find devices** and choose your Thor.
5. Click **Install / update + activate**.
6. Open Northland and release the sticks and buttons.

Setup updates our companion, copies our helper, enables its overlay and starts
the controls. It leaves the purchased game and saves in place. Unauthorized and
offline devices show a recovery step before any installation starts.

After a reboot, **Activate installed app** restarts the controls without
reinstalling anything. Pressing it again reuses an active helper. **Open game**
uses the return path that avoids launching a second game screen.

The command-line equivalent is:

```sh
python scripts/android-controller.py --serial DEVICE_SERIAL setup
```

Use `--adb PATH_TO_ADB` and `--artifacts BUILD_FOLDER` before `setup` if needed.
The window has a link to Google's official Platform Tools download. It does not
silently download or replace ADB.

## Activate entirely on the Thor

Install `northland-panel.apk` on the Thor. This APK includes our controller
helper; the separate JAR is only needed for USB activation.

1. Install [Shizuku from its official download page](https://shizuku.rikka.app/download/).
2. In Shizuku, follow **Start via Wireless debugging > Pairing**. Use the pairing
   code shown in Android's Wireless debugging settings. This pairing stays on
   your device; our companion does not collect or store the code.
   Allow Shizuku notifications so you can enter the code in its notification.
3. Start Shizuku using Wireless debugging.
4. Open **Northland Thor**, allow its overlay when prompted, then tap
   **Activate controls**.
5. Allow Northland Thor's Shizuku access when prompted.
6. Wait for **Controls are active**, then tap **Open / return to Northland**.

After rebooting, start Shizuku on the Thor again, then tap **Activate controls**
in our app. You do not need a computer for that route. Wireless activation needs
Wi-Fi. The controller helper does not use Wi-Fi to send game commands.

Shizuku has its own [step-by-step setup guide](https://shizuku.rikka.app/guide/setup/)
for Android's pairing screens. This is manual activation after reboot, not an
unattended boot service. USB setup remains available if you do not want Shizuku.
If Shizuku keeps searching for Wireless debugging, turn Wireless debugging off
and back on in Android's Developer options, allow the current Wi-Fi network,
then return to Shizuku. That was needed once during testing on this Thor.

## What activation runs

Shizuku starts a small service containing our own helper as Android's shell UID
2000. It accepts only status, start and stop operations from our companion or
shell. It accepts no shell commands, paths or input coordinates over that
interface. The helper keeps its authenticated socket, Northland/display-0 focus
checks, input release and neutral-before-rearm rules.

USB and Shizuku activation use the same file lock. They cannot run two controller
helpers together. An installed build gets a content-derived service version, so
Shizuku can replace its service when the app code changes.

The build pins the Shizuku API and AndroidX annotation downloads by SHA-256.
See [third-party notices](../third_party/NOTICE.md). The separate Shizuku manager
is not bundled or modified.

## Local builds and bundles

Build on Linux, macOS or WSL with Java 17, Android SDK platform 36, build-tools
36.0.0, Python 3 and `zip`:

```sh
bash scripts/build-android.sh
python scripts/package-android.py
```

The first build downloads the pinned API dependencies. Later builds reuse their
verified cache. The packager creates `artifacts/releases/Northland-Thor-setup.zip`
from an explicit list of our code, artifacts and notices. It never packages game
files, saves, captures, ADB keys or the signing keystore.

## Verification

Host checks cover device authorization, missing builds, argument boundaries and
recognizing a live helper started through either route:

```sh
python -m unittest discover -s tests -p test_setup_backend.py
python scripts/verify-desktop-setup.py --adb PATH_TO_ADB --serial DEVICE_SERIAL --action refresh
```

The UI verifier can also drive `--action activate` or `--action setup` on a
connected Thor. Setup intentionally updates our app and restarts its helper.
It never reinstalls or clears the game.

On October 15, the Windows window installed and activated the build on the Thor.
Shizuku pairing succeeded through its notification; stopping its server and
restarting it through its Wireless debugging button also succeeded. Our app then
started its embedded helper as shell UID 2000, and cursor/camera checks passed
in the running tutorial. A complete device reboot and native macOS/Linux window
runs have not yet been checked. See the [device results](android-controller.md).
