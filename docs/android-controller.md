# Android controller device checks

Tests on October 15, 2026 use official Android Northland 1.1 (16), package `com.runesoft.cultures`, on an Android 13 AYN Thor. Its APK, license components and private saves are unchanged. Our development companion is `org.northland.thor`; the helper is in `/data/local/tmp/northland-thor`.

## Observed results

| Check | Evidence |
| --- | --- |
| Actions snapshots and paging | Installed snapshot build read the builder's 16 commands; page two showed Assign building site, which opened the original “Select a building site” mode. Cancelled without choosing a target. Evidence: `artifacts/snapshot-final-builder-bottom.png`, `snapshot-final-builder-page2.png`, `snapshot-final-builder-site.png` |
| Explicit refresh and unavailable selection | Refresh after deselecting cleared the command list and showed the select-character message (`artifacts/snapshot-final-empty.png`). Selecting Knut and refreshing restored the commands. Profession and equipment opened their matching native windows. Knut was restored to Civilian after the builder check |
| Snapshot during camera movement | Bottom captures stayed pixel-identical while right-stick pan changed the top-screen terrain, and after the original menu closed. RGB comparisons, including all color channels, passed for `artifacts/snapshot-final-ready-pan/` and `snapshot-final-left-pan/`. A minimap jump also changed the top without changing the bottom list |
| Recognition coverage | Four civilian reference frames returned all 15 commands and two builder frames returned all 16. Another live civilian snapshot omitted Attack inhabitants without reporting an unknown frame; frame detection can miss buttons. Other professions and groups remain unverified |
| Native launch and first tutorial | Menu and mission render on the top display; initial 4:3 layout and later full-width session both observed |
| Original shortcuts | Escape closes a panel; F4 opens Save; period selects Knut; Space opens actions; Z cycles zoom |
| Mouse input | Hover tooltips track logical coordinates; right click sends Knut to targeted terrain |
| Bottom-screen commands | Session → Save opens the top-screen save window while Northland retains focus |
| Fixed Actions page | Installed palette remained pixel-identical while a synthetic right-stick pan moved the top-screen terrain and while the original profession dialog replaced the circular menu (`artifacts/fixed-palette-pan-before.png`, `fixed-palette-pan-after.png`). Direct profession and on-demand equipment activation both opened the correct windows. Dark trees no longer count as an open dialog in the checked capture; actual profession/equipment dialogs still do |
| Labeled villager actions | Villager → Actions displays 15 text buttons for Knut. Bottom-screen Change profession and Change equipment each opened the matching original window; captures are `artifacts/actions-text-bottom.png`, `actions-text-profession.png` and `actions-text-equipment.png` |
| Synthetic right stick | Odin Controller events produce held Right, visible terrain movement, then release |
| Synthetic left stick | Axis events move the overlay cursor and game hover location |
| Synthetic X | Knut visibly walks to the cursor position |
| Synthetic Y / B / RB / R3 | Actions open and close, civilian selection works, and zoom changes |
| Synthetic A | Left mouse press/release reaches injected state; full selection and drag need a dedicated gameplay pass |
| A held with left-stick motion and X | Mouse movement continues with left held; X does not interrupt the drag |
| LT selection and left-stick drag | The game's selection rectangle is visible in the captured held state; trigger release ends the left-mouse hold |
| LT and A overlapping | Releasing either control keeps selection held until both are released; analog threshold checks do not produce extra clicks |
| LT digital button and focus loss | Digital L2 also selects; leaving the game releases the mouse and returning with LT held waits for neutral |
| Physical drag before the stick swap | Owner confirmed LT + right-stick rectangle dragging worked. Cursor and drag now use the left stick; physical feel of this layout needs owner testing |
| LT held still, then released | Synthetic L2 hold sent the selected Knut from his previous position to the terrain under the cursor; his blue selection heart and character window remained visible on arrival (`artifacts/controller-lt-arrived.png`) |
| Short LT press and B deselect | B cleared Knut's selection and character windows; an 80 ms synthetic analog LT press over him selected him again (`artifacts/controller-lt-short-select.png`) |
| Original touch controls | Experimental two-finger shortcut removed at the owner's request; no touchscreen observer runs in the helper |
| RT wheel | Appears, highlights Profession with right stick pointing right, and dismisses on RT release |
| Wheel confirmation | RT + left on right stick + A opens Save; no left click leaks into the game |
| Focus loss during pan | Earlier neutral/rearm check passed. Latest check after the stick swap released keys on focus loss, but returning failed and Northland restarted; resumed gameplay recovery needs investigation |
| Companion stopped during pan | Keys release, controller injection disables, and the bottom launcher becomes visible again; reconnecting requires neutral |
| Audible output, physical feel, long sessions | Awaiting owner testing |

The helper's state report establishes what it injected. Screenshots establish what the game did. Neither establishes sound heard at the speakers or physical control feel.

After the stick swap, synthetic checks confirmed left-stick cursor motion, right-stick panning, and a visible LT + left-stick rectangle. The latest app-switch check restarted Northland; a fresh Controls tutorial was opened afterward. Do not run app-switch checks in an unsaved play session.

The game distinguishes a short left click from a stationary hold. To check LT orders, first select a villager and position the cursor over open terrain away from them, then run `lt-button`. This holds L2 long enough for the game's context click on release. Check movement and retained selection after arrival; the `lt-button` state assertion alone does not establish either. Use a short physical LT press to test selection instead.

## Repeat a check

Use an idle tutorial with all physical controls released. Start the companion and bridge as described in the [README](../README.md). The check writes synthetic events to the exact `Odin Controller` node and restores those controls to neutral in a `finally` block. Use a test session, since checks move the camera and issue commands.

```sh
python scripts/verify-android-controller.py --serial DEVICE_SERIAL --case camera
python scripts/verify-android-controller.py --serial DEVICE_SERIAL --case cursor
python scripts/verify-android-controller.py --serial DEVICE_SERIAL --case civilian
python scripts/verify-android-controller.py --serial DEVICE_SERIAL --case order
python scripts/verify-android-controller.py --serial DEVICE_SERIAL --case drag
python scripts/verify-android-controller.py --serial DEVICE_SERIAL --case lt-select
python scripts/verify-android-controller.py --serial DEVICE_SERIAL --case lt-drag
python scripts/verify-android-controller.py --serial DEVICE_SERIAL --case lt-button
python scripts/verify-android-controller.py --serial DEVICE_SERIAL --case lt-focus
python scripts/verify-android-controller.py --serial DEVICE_SERIAL --case actions
python scripts/verify-android-controller.py --serial DEVICE_SERIAL --case back
python scripts/verify-android-controller.py --serial DEVICE_SERIAL --case zoom
python scripts/verify-android-controller.py --serial DEVICE_SERIAL --case wheel
python scripts/verify-android-controller.py --serial DEVICE_SERIAL --case wheel-confirm
python scripts/verify-android-controller.py --serial DEVICE_SERIAL --case focus
python scripts/verify-android-controller.py --serial DEVICE_SERIAL --case panel-loss
```

`--adb PATH_TO_ADB` is available. Results go to ignored `artifacts/controller-check`: JSON input-state reports and before/held/after screenshots. Inspect the pictures; a state assertion alone is insufficient. Some checks depend on selection and UI state. Wheel confirmation opens Save without choosing a slot. Focus testing opens our setup activity and returns to the existing game activity.

For direct guarded probes, stop controller mode first:

```sh
python scripts/android-controller.py --serial DEVICE_SERIAL key save
python scripts/android-controller.py --serial DEVICE_SERIAL key cancel
python scripts/android-controller.py --serial DEVICE_SERIAL move 960 540
python scripts/android-controller.py --serial DEVICE_SERIAL click 960 540 right
```

The helper log and current input state contain narrow diagnostics:

```sh
adb -s DEVICE_SERIAL shell cat /data/local/tmp/northland-thor/bridge.log
adb -s DEVICE_SERIAL shell cat /data/local/tmp/northland-thor/state.json
```

## Labeled villager actions

Opening Villager → Actions sends a named refresh request over the existing UID-authenticated socket. The helper checks that controls are neutral and Northland retains focus, rebuilds the original menu at the controller cursor inside the display, and returns an immutable list of recognized command IDs. The panel shows their compiled text labels. No screenshot or icon pixels are sent to the panel, and no capture loop runs between requests.

Opening Actions or tapping Refresh replaces the snapshot; panning and original-menu closure do not. Replies from previous refresh requests are ignored. Activation includes the snapshot request ID and command ID. The helper rejects a command outside that snapshot, reads the current native menu, and requires a unique matching button before dispatch. Profession, home and workplace use original shortcuts after that availability check; other commands click the freshly recognized native button. Failures report a message instead of clicking a guessed position. Outbound messages belong to their authenticated connection, so a reconnect cannot receive an old connection's snapshot.

The catalog contains 38 distinct human-menu commands. The initial civilian fingerprints were checked against native hover captions; the expanded catalog uses the owned engine's command-to-sprite and label tables. Fingerprints contain horizontal and vertical gradient bits, not stored game images. A reproducible generator reads private owned files and writes metadata only. No upstream decoder code or game artwork is bundled. The BMD format was cross-checked with [martianboy's format research](https://github.com/martianboy/cultures2-wasm/blob/master/src/bmd.rs).

Live checks covered Knut as a civilian and a builder. Other professions, women, soldiers and group menus still require live checks. Only actual recognized menu contents are shown; this is not an engine selection interface. Recognition can omit or fail to identify buttons. The page reports detected unknowns, but cannot guarantee that every native button was found. Keep the original menu available with Y.

Repeat protocol checks and checks against private reference captures:

```sh
bash scripts/check-action-snapshots.sh
bash scripts/check-action-names.sh artifacts/CIVILIAN.png
bash scripts/check-action-names.sh --count 16 artifacts/BUILDER.png
bash scripts/check-menu-geometry.sh 0:clear artifacts/TERRAIN.png 0:dialog artifacts/PROFESSION_WINDOW.png
python scripts/derive-action-fingerprints.py --library PATH_TO_OWNED_LIB/libNorthland.so \
  --asset-pack PATH_TO_OWNED_APK/split_NorthlandAssetPack.apk --out artifacts/action-fingerprints.json
```

The generator requires Pillow. Its binary profile is hash-checked against the reviewed library. Existing on-device calibration samples are retained alongside generated signatures; the generator does not overwrite the app's source. Retail copies and reference screenshots remain ignored.

## API references

Android documents [mouse MotionEvents](https://developer.android.com/reference/android/view/MotionEvent), [overlay touch-obscuring rules](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_NOT_TOUCHABLE), and [local-socket peer credentials](https://developer.android.com/reference/android/net/LocalSocket#getPeerCredentials()). SDL's [Android mouse translation](https://github.com/libsdl-org/SDL/blob/SDL2/src/video/android/SDL_androidmouse.c) explains release masks. Injection uses a hidden API under the shell UID; this is tested on this device, not a portable permission an ordinary app possesses.
