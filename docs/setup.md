# Setting up Northland Thor

Install your purchased Android copy of Cultures: Northland first. This project
adds the controller helper and bottom-screen panel. It does not include the game.

You can activate over USB, or pair Northland Thor directly with Android's
Wireless debugging. Both work without Shizuku or root.

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

1. Connect to Wi-Fi. Enable Developer options if needed by tapping Android's
   Build number seven times in About device.
2. Open **Northland Thor**, allow notifications, and tap **Pair wireless
   activation**, then **Open Developer options**.
3. Find **Wireless debugging**, enable it and allow the current network. Tap
   the row to open its settings, then **Pair device with pairing code**.
4. Keep Android's code window open. Pull down notifications, expand the
   Northland Thor group and **Northland Thor setup** notification if needed.
5. Tap **Enter pairing code**, enter Android's six digits, and tap the send arrow.
6. Wait for **Paired**, return to Northland Thor, allow its overlay when prompted,
   then tap **Activate controls**.
7. Wait for **Controls are active**, then tap **Open / return to Northland**.
   Release the sticks and buttons before playing.

After rebooting, connect to Wi-Fi, enable Wireless debugging if Android turned
it off, then tap **Activate controls**. Pair again only if Android has forgotten
the authorization. The running helper sends game commands locally.

If activation keeps searching, turn Wireless debugging off and back on, allow
the current Wi-Fi network, then return to our app and tap **Activate controls**
again. That restarts the attempt and reads the current port immediately. Discovery
waits up to two minutes and shows a recovery hint after ten seconds.
If pairing fails, close the code window and retry with a fresh code. Keep
Android's window open until pairing finishes.

An earlier Shizuku pairing does not authorize our app's key. Users upgrading
from that build need to pair Northland Thor once. Shizuku can stay installed
for other apps; Northland Thor no longer uses it. USB setup remains available.

## What activation runs

The companion reads Android's current TLS debugging port and connects to loopback
using Android's paired TLS connection. Its fixed startup command runs our
embedded helper as Android shell UID 2000, then closes the ADB connection.
There is no shell console or remote-device address field.

Startup uses ADB's non-interactive shell-v2 protocol, checks its exit status,
and detaches the helper with `nohup` and `setsid`. The previous terminal session
could hang up its background child or stall while reading for EOF. A shell exit
marker alone is insufficient: the authenticated controller socket must connect.
Once Android has authenticated, a helper startup failure is shown as that error;
it no longer sends the app back into port discovery.

The RSA pairing key belongs to this app installation. It is encrypted with an
Android Keystore key in private, non-backed-up storage. Pairing codes are not
saved. Clearing companion data or forgetting it in Android requires pairing again.

Activation reads `service.adb.tls.port` through a bounded `/system/bin/getprop`
call inside our ordinary app. This worked on the Android 13 Thor without root.
If a ROM restricts that property or the connection fails, local NSD discovery
remains available. Pairing-code ports still use discovery. Activation no longer
reuses a persisted port from a previous boot. Only an authenticated helper socket
counts as active.

After pairing, Activate does not need another permission popup. Progress and
errors appear in our app and its setup notification; a sound is not success.

USB and wireless activation use the same file lock, so only one helper runs.
Its socket authenticates the companion's kernel UID and accepts named commands.
Focus checks, input release and neutral-before-rearm stay in place. The build
pins LibADB, Bouncy Castle and AndroidX annotation by SHA-256.
See [third-party notices](../third_party/NOTICE.md).

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
bash scripts/check-wireless-activation.sh
python scripts/verify-desktop-setup.py --adb PATH_TO_ADB --serial DEVICE_SERIAL --action refresh
```

The UI verifier can also drive `--action activate` or `--action setup` on a
connected Thor. Setup intentionally updates our app and restarts its helper.
It never reinstalls or clears the game.

On October 15, the Windows window installed and activated the build on the Thor.
Northland Thor paired through its own notification and started its helper
directly as shell UID 2000 with Shizuku's server stopped. Cursor/camera checks
passed in a loaded tutorial save. Reboot testing exposed port-discovery and
helper-startup failures. After installing the shell-v2 startup fix and rebooting,
the owner confirmed that activation worked. The pairing carried over; Wireless
debugging needed enabling again. Native macOS/Linux setup windows remain unchecked.
See the [device results](android-controller.md).
