# Northland Thor design

## Use the official Android game

The owner installed the [Runesoft Android version](https://play.google.com/store/apps/details?id=com.runesoft.cultures). It runs the original Northland tutorial natively on ARM64, using SDL for input and display. The inspected build is version 1.1, version code 16. The project now targets that app. The [Windows tests](compatibility.md) remain as a record of the earlier route.

| Approach | Result of investigation | Decision |
| --- | --- | --- |
| Ordinary Android companion or accessibility service | Useful for a panel, but Android 13 does not give an ordinary background app unrestricted joystick capture and mouse/keyboard injection into another app | Insufficient by itself |
| Replace the game's wrapper or load its library in another app | Would need to preserve the publisher's licensing, asset delivery and updates; no engine source is available | Keep the purchased app intact |
| Companion UI plus an ADB shell input helper | Thor shell can read its controller, inject input, and leave Northland focused while a bottom panel receives touch | Implemented prototype |

The app is not debuggable. Exported functions in its library do not give a companion access to the running engine. No selected-unit, resource or camera-state interface has been established.

## Direct action commands: investigation

The requested replacement for the visual command lookup needs a bridge **inside the running game process**. Static inspection of the installed ARM64 library found the selection queries, allowed-command queries, localized action labels and menu dispatcher. These interfaces have not been called in the live game. The existing installed prototype still uses visual lookup for actions without shortcuts.

The inspected library has SHA-256 `96e49d3bb5a3fcd137a5224135900e72d3a19472612ed53c2e4167f5fbceb116`. Offsets below describe that exact binary, not a portable ABI or absolute process addresses.

| Interface | Relative address | Role |
| --- | --- | --- |
| `CInputManager::sTheObjectPtr` | `0x4aa100` | Pointer to the current input/selection manager |
| `Selection_GetSelectedSingleHumanId` | `0x23b02c` | Selected character ID |
| `Selection_GetSelectedMultipleHumanNumber` / `...Id` | `0x23b03c` / `0x23b050` | Selected character group |
| `Logic_UserControl_GetHumanCommands` | `0x2cda94` | Actual allowed commands for a character's current state |
| `Logic_UserControl_GetVehicleCommands` | `0x2cdb20` | Vehicle commands |
| `Logic_UserControl_GetHouseCommands` | `0x2cdae0` | Building commands |
| `StringTool_GetLogicUserCommandsForHumanTypeNamePtr` | `0x1d2fe0` | Localized labels for human command IDs |
| `CGuiManager::sTheObjectPtr` | `0x4aa120` | Current in-game UI manager |
| `CGuiManagerDefaultMessageHandlerElement::XGui_BE_Message_Handle` | `0x2442b0` | The original action-menu dispatch path |

The native human-command handler contains a 32-bit count followed by at most 25 command IDs, occupying 104 bytes. The original menu queries a single character with flags `(false, false)`. For groups it queries each member with `(true, false)` and intersects the results, then sorts by the engine's priority function. A bridge should reproduce those rules, including availability changes when a profession, assignment or other state changes. A fixed profession-to-buttons table would miss those changes.

The original menu constructs human button messages as `3000 + commandId`, vehicle messages as `3600 + commandId`, and house messages as `3300 + commandId`. It sends message type `0x800` to the UI manager's default-handler member at offset `0x288`. The keyboard path uses this same handler: C dispatches human command 24 (Change profession), A dispatches 16 or 20 according to availability (workplace/work area), and W dispatches 10 (Assign home). The general equipment action is command 26 and opens the original equipment window. These mappings are established by disassembly, not by simulated keyboard guesses or screen coordinates.

Use this UI handler rather than invoking `CHuman::DoExecuteUserCommand_*` directly. The UI handler preserves targeting modes, original selection handling, native windows and normal command queuing. Some actions choose a mode and await a world target; issuing every action as an immediate unit order would change their behavior.

The proposed bridge boundary is:

1. On the game's update thread, read the actual selection and query its allowed commands and localized labels.
2. Send a snapshot containing selection kind, selected IDs, a revision, and command IDs/labels to the companion. Display those commands as ordinary bottom-screen buttons; clear the page when selection is empty.
3. A button sends its command and snapshot revision. On the game thread, re-query selection and availability; reject stale requests, then dispatch through the original UI handler.
4. Keep targeting in the original world input path. Profession/equipment submenus need their own live choice queries if their contents are also to move to the bottom display.
5. Authenticate the companion connection by kernel UID, expose only named game operations, and release input on focus/connection loss. No screenshot capture belongs in this path.

### Access checks on the Thor

Read-only checks on October 15, 2026 found a production Android build (`ro.debuggable=0`, build type `user`), an installed game without the debuggable flag, no `su` executable in PATH or the checked standard locations, and denied shell access to the game's `/proc/PID/maps` and `/proc/PID/mem`. `run-as com.runesoft.cultures id` also fails because the game is not debuggable. The bootloader reports `ro.boot.flash.locked=0`; that does not grant process access or establish a safe rooting procedure.

The inspected manifest and main activity expose no game-command service, provider or broadcast interface. The other exported receiver is AndroidX's profile installer, not gameplay IPC. Loading another copy of the library in our helper would create separate globals and would not control the running game.

Android documents that native code remains inside the [application sandbox](https://source.android.com/docs/security/app-sandbox), that [ART agents require a debuggable app](https://source.android.com/docs/core/runtime/art-ti), and that [wrap.sh also requires a debuggable build](https://developer.android.com/ndk/guides/wrap-script). Android 13's [instrumentation implementation](https://github.com/aosp-mirror/platform_frameworks_base/blob/android13-release/services/core/java/com/android/server/am/ActivityManagerService.java) checks the test package's signature against its target. Our companion is not signed by the publisher.

The owner requires an unrooted Thor and a simple setup, and does not want invasive integration work. A publisher-provided build/integration that loads the bridge would meet the direct-command requirement. Root instrumentation and private repackaging were considered but are not the selected direction. [Frida's Android documentation](https://frida.re/docs/android/) describes why those alternatives require root, repackaging or debugger access. Replacing or re-signing the purchased app also conflicts with this project's current preservation rule. No root, reboot, attachment, package replacement or save changes were performed in this investigation.

Within the current companion approach, use the original keyboard shortcuts wherever available. Native-menu recognition remains the practical fallback for other actions, with acknowledged recognition failures. The implemented Actions page refreshes from a recognized native menu when opened and holds that snapshot stable until an explicit refresh. It reports observed menu content, not authoritative live selection data. The current recognition catalog contains 38 distinct human-menu commands; civilian and builder gameplay checks are recorded in the device-check document. The companion cannot promise complete live action availability without an engine connection.

The no-root alternatives were checked against Android 13's source: `ProcessList` enables JDWP from the app's debuggable flag; `AppProfiler` checks that flag before attaching startup agents; and `GraphicsEnvironment` requires debuggability or the app's explicit `com.android.graphics.injectLayers.enable` metadata. The inspected game manifest has neither opt-in. Setting a debug-app name alone does not change its manifest flag or add those interfaces. No debug settings were changed.

Repeat the binary inventory and read-only access checks with:

```sh
python scripts/inspect-direct-actions.py --library PATH_TO_OWNED_LIB/libNorthland.so \
  --adb PATH_TO_ADB --serial DEVICE_SERIAL --out artifacts/direct-actions/report.json
```

This script requires only Python's standard library. It checks exported symbols and the reviewed binary's shortcut table; it does not attach, inject code or claim that native calls work. Private library copies, disassembly and generated reports stay outside Git.

## Input and display

`InputBridge` runs as the ADB shell user and reads only the device named `Odin Controller`. It does not exclusively grab it. Home, volume and Android navigation retain their usual paths. The vendor key layout establishes the face-button codes and R3 at Linux code `0x13e`.

Right stick produces held arrow keys. This first version uses the game's fixed keyboard pan speed; analog camera speed remains an improvement. Left stick uses a dead zone, a curved response and elapsed time to move a logical-screen cursor. A owns left clicks and dragging with the left stick. X produces right-click orders. Mouse release sends the updated button mask, which SDL compares with its previous value to find the released button.

LT is navigation-only. While held, D-pad directions capture the native game UI on demand and move the cursor between visible button frames. A clicks the chosen target. The analog LT threshold has hysteresis. Navigation, wheels and existing A drags have exclusive ownership; LT cannot interrupt a drag or recall a numbered group. Right-stick camera input pauses during navigation. See [UI navigation](ui-navigation.md) for detection limits and verification.

The cursor and wheel are non-focusable, non-touchable overlays. The bottom panel is non-focusable and touchable. Tapping it sends a named command to the helper, which injects the original shortcut on display 0. The panel does not take keyboard focus from SDL. Top overlay opacity stays below Android's untrusted-touch threshold.

The prototype fixes geometry to the tested 1920×1080 top and 1240×1080 bottom layout. The game initially rendered a 4:3 area, but the owner's later session uses the full display width. Cursor bounds therefore follow the entire logical top display rather than the earlier side margins. Dynamic display sizes remain future work.

## Session ownership

One helper owns injected keys and mouse buttons. A file lock rejects duplicate helpers. The launcher stops an old helper cleanly before replacing its artifact. A stop request, controller-reader failure, loss of Northland focus or loss of the companion connection releases held input. Focus is polled, so app-switch handling has a short delay. Returning requires neutral controls before injection resumes.

UI and helper communicate over an Android local socket. Both check the peer's kernel UID: the helper accepts our installed companion's UID, and the companion accepts shell UID 2000. The protocol exposes a fixed command list and controller/UI state. It has no arbitrary shell command or general-purpose input endpoint.

Background launch uses `nohup`. A reboot ends the helper and currently requires activation from a computer again. Cable-disconnect behavior still needs a physical check. Phone-only activation needs a separate investigation.

## Command coverage

The [control group controls](control-groups.md) use the D-pad for groups 1 to 4
and an RB wheel for all ten native groups. That document records the Android
binary evidence, implemented input rules and remaining gameplay checks.

The [README controls](../README.md) are implemented. RT opens an eight-command wheel. It consumes camera/cursor movement and selection input while held. Left stick chooses, A issues one command, and B cancels. Releasing RT dismisses the wheel. A cancelled or confirmed wheel stays dismissed until RT is released. A must return to neutral before starting another selection, preventing a held confirmation from clicking through to the game. Holding LT blocks wheel opening; navigation started during a wheel waits for LT to be released and pressed again.

The bottom panel has Villager, Settlement and Session pages. It opens original management windows rather than guessing available actions. The first tutorial confirms action-menu, save-window and basic villager-order paths. Building, professions, home/workplace assignment, roads, diplomacy and technology require later-tutorial and campaign checks.

Saved group creation and recall use the original number-key shortcuts. Remaining
checks include multi-unit group addition, group save/load, same-profession
selection, work-area targeting, building/profession navigation, menu/text entry
and a complete pause/speed/session audit. Test each against the Android game;
Windows manual shortcuts are candidates, not proof of Android behavior.

## Touch and live game data

The top screen uses the publisher's original touch handling. The experimental two-finger Escape shortcut was removed at the owner's request after “Go to” remained during physical testing. The helper no longer observes the touchscreen or adds commands to physical touch gestures.

A follows the original game's tap/hold distinction: a short press selects or confirms; a stationary hold followed by release acts as a right click and issues a context order. Earlier device tests established this behavior with the former LT mapping. A now owns that mouse input, and A plus left-stick dragging has been checked on the Thor. X gives an immediate context order. The game supplies the tap/hold behavior; the helper does not infer selection state. Touch-to-controller cursor synchronization still needs work: native touch moves the game's pointer independently of our overlay.

Live needs, professions, home/workplace details, settlement resources and a bottom-screen minimap require a real game-state interface. The companion currently supplies shortcuts only. Keep the original character windows and minimap accessible while that interface is unresolved. Do not infer map coordinates from a scaled screenshot.

## Next verification milestones

| Milestone | What establishes it |
| --- | --- |
| Physical controller feel | Owner tests sticks, buttons, R3, triggers, selection drags and wheel navigation |
| Audio | Audible music, effects and speech on the Thor; an active audio track alone is insufficient |
| Basic play | Select with A, order with X, navigate actions and build without external input |
| Command coverage | Relevant tutorials and a campaign mission, including trade and professions |
| Session safety | App switching, companion loss, disconnects, sleep/wake and no held input after stopping |
| Save behavior | Create and load a new test save, including text entry, without overwriting existing saves |
| Simple installation | Fresh setup on Windows, macOS and Linux, then updates and reboot recovery |

The [device checks](android-controller.md) use synthetic events through the actual Thor controller node. Physical handling and long-play stability still need hands-on testing.
