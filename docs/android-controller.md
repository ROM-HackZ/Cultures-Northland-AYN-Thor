# Android controller device checks

Tests on October 15, 2026 use official Android Northland 1.1 (16), package `com.runesoft.cultures`, on an Android 13 AYN Thor. Its APK, license components and private saves are unchanged. Our development companion is `org.northland.thor`; the helper is in `/data/local/tmp/northland-thor`.

## Observed results

| Check | Evidence |
| --- | --- |
| LT navigation-only | LT alone caused no clicks, captures or group recalls. LT + D-pad reached the toolbar wrench; A opened Options, then navigation reached X and A closed it. Captures reviewed in `artifacts/ui-nav-quiet/`. A + left-stick dragging continued while LT was pressed and released. [Navigation details and limits](ui-navigation.md) |
| Current A selection | A now owns left-mouse selection and dragging. The A-held drag guard passed on the Thor. Earlier LT selection results below describe the previous mapping |
| Left stick for both wheels | Installed build uses LS to highlight commands and group slots. Cursor coordinates stayed fixed while choosing; moving RS changed neither the wheel choice nor injected camera keys. RT + LS left + A opened the original Save window, then B closed it. Evidence: `artifacts/wheels-left-stick-groups/`, `wheels-left-stick-commands/`, `wheels-left-stick-confirm/`, `wheels-left-stick-close/` |
| Native groups | Ctrl-number saved Knut and number recalled him in the official Android game. The companion's RB + hold Y saved him into the previously empty group 1; D-pad Up recalled him and repeated recall centered the camera. Native selection panels are visible in `artifacts/groups-quick/dpad-after-top.png` |
| Four D-pad shortcuts | Up, Right, Down and Left requested groups 1, 2, 3 and 4 respectively. Each held direction, including a duplicate hat/button report, increased the request counter only once. Evidence is in `artifacts/groups-quick/` and `groups-quick-2/` through `groups-quick-4/` |
| Group cancellation and input ownership | Cancelled replacement, a slot change during a held Y, diagonals, competing wheel openers and RB during an LT selection drag passed in `artifacts/groups-installed/`. The final simultaneous RT + RB + A check also injected no click, command or group operation (`artifacts/groups-final-both/`) |
| Group touch controls and Add | Set displayed a separate confirmation, Cancel sent no request and Set with an empty selection cleared the spare test group. Adding Knut to that empty group then recalling it selected him again. Evidence: `artifacts/groups-touch/`, `groups-touch-empty/`, `groups-touch-add9/`, `groups-added9-recall/` |
| Group hold interrupted by companion loss | Stopping only our companion during a Y replacement hold cancelled the wheel, released input and prevented a group operation. Reconnecting while the controls remained held waited for neutral. Northland's activity was not restarted (`artifacts/groups-disconnect/`) |
| Earlier RT wheel regression | Before switching wheel navigation to LS, RT + right-stick left + A opened the original Save window and B closed it (`artifacts/groups-rt-regression/`, `groups-rt-close/`) |
| Group test limits | Tutorial has one subject, Knut. Multi-unit addition preserving existing members, soldiers, mixed selections and group membership after save/load remain unverified. No mission was loaded or advanced for these checks |
| Native membership rules | Moving Knut from group 9 to group 1 removed him from 9. Recalling 9 then cleared selection, while D-pad Up selected Knut again. Captures: `artifacts/groups-old-slot9/select-after-top.png`, `groups-final-recall1/dpad-after-top.png`. Group 1 is left available for the owner to try |
| Actions after group recall | Recall cleared the earlier Actions page and required Refresh (`artifacts/groups-actions-invalidation/dpad-after-bottom.png`). An empty group's native selection stays empty rather than showing invented actions |
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
| Earlier synthetic Y / B / RB / R3 | Actions open and close, civilian selection worked with the earlier RB mapping, and zoom changes. RB now opens Groups; next civilian remains on the bottom screen |
| Synthetic A | Left mouse press/release reaches injected state; A + left-stick dragging and LT-during-drag input ownership checked in `artifacts/ui-nav-quiet/` |
| A held with left-stick motion and X | Mouse movement continues with left held; X does not interrupt the drag |
| Earlier LT selection and left-stick drag | Before navigation replaced LT selection, the game's selection rectangle was visible in the captured held state; trigger release ended the left-mouse hold |
| Earlier LT and A overlapping | Before the mapping change, releasing either control kept selection held until both were released; analog threshold checks produced no extra clicks |
| Earlier LT digital button and focus loss | Digital L2 selected under the old mapping; leaving the game released the mouse and returning with LT held waited for neutral |
| Physical drag before the stick swap | Owner confirmed LT + right-stick rectangle dragging worked. Cursor and drag now use the left stick; physical feel of this layout needs owner testing |
| Earlier LT held still, then released | Under the old mapping, synthetic L2 hold sent the selected Knut from his previous position to the terrain under the cursor; his blue selection heart and character window remained visible on arrival (`artifacts/controller-lt-arrived.png`) |
| Earlier short LT press and B deselect | B cleared Knut's selection and character windows; an 80 ms synthetic analog LT press over him selected him again under the old mapping (`artifacts/controller-lt-short-select.png`) |
| Original touch controls | Experimental two-finger shortcut removed at the owner's request; no touchscreen observer runs in the helper |
| Earlier RT wheel | Appeared, highlighted Profession with right stick pointing right, and dismissed on RT release. Both wheels now use LS |
| Earlier wheel confirmation | RT + left on right stick + A opened Save; no left click leaked into the game. Both wheels now use LS |
| Focus loss during pan | Earlier neutral/rearm check passed. Latest check after the stick swap released keys on focus loss, but returning failed and Northland restarted; resumed gameplay recovery needs investigation |
| Companion stopped during pan | Keys release, controller injection disables, and the bottom launcher becomes visible again; reconnecting requires neutral |
| Audible output, physical feel, long sessions | Awaiting owner testing |

The helper's state report establishes what it injected. Screenshots establish what the game did. Neither establishes sound heard at the speakers or physical control feel.

After the stick swap, synthetic checks confirmed left-stick cursor motion, right-stick panning, and a visible LT + left-stick rectangle. The latest app-switch check restarted Northland; a fresh Controls tutorial was opened afterward. Do not run app-switch checks in an unsaved play session.

The game distinguishes a short left click from a stationary hold. A now supplies that input. To issue an order, select a villager, position the cursor over terrain, hold A still and release, or use X for an immediate right-click order. LT only navigates visible buttons. The obsolete LT selection cases have been removed from the controller test script.

## Repeat a check

Use an idle tutorial with all physical controls released. Start the companion and bridge as described in the [README](../README.md). The check writes synthetic events to the exact `Odin Controller` node and restores those controls to neutral in a `finally` block. Use a test session, since checks move the camera and issue commands.

```sh
python scripts/verify-android-controller.py --serial DEVICE_SERIAL --case camera
python scripts/verify-android-controller.py --serial DEVICE_SERIAL --case cursor
python scripts/verify-android-controller.py --serial DEVICE_SERIAL --case order
python scripts/verify-android-controller.py --serial DEVICE_SERIAL --case drag
python scripts/verify-ui-navigation.py --serial DEVICE_SERIAL --case hold
python scripts/verify-ui-navigation.py --serial DEVICE_SERIAL --case toolbar
python scripts/verify-ui-navigation.py --serial DEVICE_SERIAL --case drag
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

## Repeat group checks

The [group controls guide](control-groups.md) describes the new mapping and local
checks. On-device checks use, for example:

```sh
python scripts/verify-control-groups.py --adb PATH_TO_ADB --serial DEVICE_SERIAL --case wheel --slot 9
python scripts/verify-control-groups.py --adb PATH_TO_ADB --serial DEVICE_SERIAL --case dpad --slot 1
```

The replace and add cases intentionally change the chosen slot; use a spare
group. Captures remain private. These checks do not establish sound, physical
stick feel or multi-unit membership from helper counters alone.

## API references

Android documents [mouse MotionEvents](https://developer.android.com/reference/android/view/MotionEvent), [overlay touch-obscuring rules](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_NOT_TOUCHABLE), and [local-socket peer credentials](https://developer.android.com/reference/android/net/LocalSocket#getPeerCredentials()). SDL's [Android mouse translation](https://github.com/libsdl-org/SDL/blob/SDL2/src/video/android/SDL_androidmouse.c) explains release masks. Injection uses a hidden API under the shell UID; this is tested on this device, not a portable permission an ordinary app possesses.
