# Testing a separate Northland APK

The private test copy installed on the Thor, but it did not get past the game's
license check. It is not a playable replacement for the purchased game.

## What we tested

On October 15, 2026, the local builder rebuilt the owner's Northland 1.1 APKs as
`org.northland.thor.experimental`, labeled **Northland Thor Test**. The six splits
were signed with one private test key and installed beside the official game.
No game code was added at this stage.

The builder compared complete byte hashes for the original three DEX files,
both native libraries and every asset entry. All matched after rebuilding.
Resources and installation identity were rebuilt; this was not a test of the
original publisher signature. License-check classes, constants, response
validation and the license provider remained intact.

| Check | Result |
| --- | --- |
| Six APKs rebuilt, aligned and signed together | Passed |
| Separate installation beside the purchased game | Passed |
| DEX, native-library and asset preservation | Passed, including all 3,477 asset-pack entries |
| First startup | Failed to bind to Google Play's licensing service |
| Explicit Google Play package visibility | Fixed service connection; request reached the licensing service |
| License response after that fix | Game logged `Unexpected response code 4 received.` and showed its error dialog |
| Playable menu and Controls tutorial in the test copy | Not reached |
| Built-in controller adapter, wheels and bottom-screen UI | Not added because the startup gate failed |
| Purchased installation | All six installed APK paths and SHA-256 hashes stayed unchanged |
| Return to the purchased game | Original PID 9598, activity 6ee284 and task 164 remained running with Knut selected |

The logs establish the service response and the game's failure. They do not
establish the precise meaning of response code 4, or prove which package,
certificate, account or distribution check caused it. No license check was
removed, installer identity spoofed or ownership response forged.

Google documents the normal [package visibility declaration](https://developer.android.com/training/package-visibility/declaring)
needed to bind to another app's service. The test adds only a query for
`com.android.vending`. Its first binding failure was distinct from the subsequent
license-response failure. Google's [installer-check documentation](https://support.google.com/googleplay/android-developer/answer/15621622?hl=en)
describes why a Play-protected app can reject unofficial installations.

The purchased game and companion remain the working route. The companion now
includes its own wireless activation, without Shizuku. This experiment did not
produce a playable game copy with controller support inside it.

## Repeat the experiment locally

Use a source checkout and your own extracted six-APK installation. The tool accepts only the reviewed
version by SHA-256. It never downloads game files. APKs, decoded files, captures,
keys and detailed logs stay out of Git.

Inspect from Windows, macOS or Linux:

```sh
python scripts/apk-experiment.py inspect --source PATH_TO_OWNED_APKS
```

Build with Java 17, Python 3.10 or newer and Android SDK build-tools 36.0.0 on
Linux, macOS or WSL:

```sh
python3 scripts/apk-experiment.py build --source PATH_TO_OWNED_APKS --sdk PATH_TO_ANDROID_SDK
```

The builder downloads official Apktool 2.12.1 and verifies SHA-256
`66cf4524a4a45a7f56567d08b2c9b6ec237bcdd78cee69fd4a59c8a0243aeafa`.
It uses Apktool's bundled aapt2 because the stock SDK compiler rejects some
decoded generated resource names. Resource-free splits explicitly include
Android's framework definitions. Builds use native temporary storage so WSL
does not compile thousands of files across the Windows filesystem boundary.
Successful builds remove that temporary directory; a failed build leaves its
location in the private build log for diagnosis.

The build prints its output folder. Install and observe a first test copy with:

```sh
python scripts/apk-experiment.py install-test --build-folder BUILD_FOLDER --adb PATH_TO_ADB --serial DEVICE_SERIAL
```

The command verifies build hashes, records the official installation, installs
only the experimental package, launches it and collects startup evidence for
25 seconds. Process liveness alone is not a passing game test. Review the
license log, actual screen and tutorial behavior before adding controller code.

An existing experimental installation is refused unless `--previous-build
PREVIOUS_BUILD_FOLDER` identifies its exact installed APK hashes and matching
signing certificate. That permits an update of only our test copy, without
clearing its data. The purchased package is never an installation target.

Local evidence is in `artifacts/apk-experiment/`. The initial test is
`baseline-20261015-171032`; the visibility fix and final result are in
`baseline-20261015-171527`. Each contains a build inventory and device log. The
second also has the startup error screenshot and official-game return capture.

Run the boundary checks with:

```sh
python -m unittest discover -s tests -p test_apk_experiment.py
```
