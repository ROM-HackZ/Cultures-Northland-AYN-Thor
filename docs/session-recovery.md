# Returning to Northland

Use recent apps or **Open / return to Northland** in the Northland Thor app to
get back to an existing game. From a computer:

```sh
python scripts/android-controller.py --serial DEVICE_SERIAL game
```

Release the sticks and buttons when you return. The helper releases its input
while Northland is out of focus and waits for neutral before enabling controls.

## What caused the restart

The old focus test returned with a plain component-only `am start`. Northland's
main activity uses Android's default standard launch mode. Starting it again
can create a second SDL activity inside the existing game process.

The retained October 15 log shows this happening during a later launcher tap:

1. The existing process was PID 7401 with its original game window still present.
2. A launcher intent created another MainActivity and ran SDL_main again inside
   that process.
3. The renderer reported `pthread_mutex_lock called on a destroyed mutex`.
4. The process died, and Android created PID 28045, returning to the main menu.

The task's base intent was component-only, left by our earlier ADB launches. It
did not match the normal MAIN/LAUNCHER entry. This was a duplicate launch, not
evidence that the input helper can never resume a background game.

Private logs and task dumps are in `artifacts/session-recovery/`. The old restart
test's full log is unavailable, so its exact crash cannot be independently
reconstructed from that test's screenshots alone.

## Changes

The companion now has an **Open / return to Northland** button. It uses the
normal MAIN/LAUNCHER intent with NEW_TASK, REORDER_TO_FRONT and SINGLE_TOP on
display 0. The `game` command uses the same action and flags. REORDER_TO_FRONT
also handles a still-running activity whose task originated in an older probe.
These are ordinary [Android intent flags](https://developer.android.com/reference/android/content/Intent#FLAG_ACTIVITY_REORDER_TO_FRONT).

The focus test closes our setup activity with Back. The Home-return test uses
the corrected open command. Both check process ID, ActivityRecord identity and
task ID before leaving, while away, after returning and after controls return
to neutral. A task number alone cannot detect a new activity in the same task.
The test rejects missing or duplicate game activities and never hides a failure
by launching a replacement game during cleanup.

No game APK, license component or save was changed. The controller's existing
release-and-rearm logic passed without changes.

## Checks on the Thor

| Path | Observed result |
| --- | --- |
| Setup activity, then Back | Same PID, activity and task; held pan released and waited for neutral |
| Home, then corrected open command | Same PID, activity and task; no held input on return |
| Installed companion's return button | Returned to the existing activity, with no second game screen |
| Controls tutorial | Setup/Back and Home/corrected-open preserved the running mission and Knut's selection |
| Android recent apps | Tapping Northland's card returned to that same mission, process and activity |
| Input after return | Fresh right-stick camera input moved the map again after neutral, then released |

The installed checks retained PID 28045, ActivityRecord 1891ead and task 138.
Menu evidence is in `artifacts/session-recovery/installed/`; mission evidence
is in `mission/`. The setup-button captures, recent-apps captures and task dumps
are in their parent folder. `rearmed/` records the working camera after return.

The checked mission was the Controls tutorial. Knut's native Civilian panel
remained open, and the game stayed in the same 4:3 layout it had before these
round trips. The camera tests intentionally panned the map. No save was written
or overwritten by these return checks.

Save your progress before repeating lifecycle tests. Leave physical controls
untouched during the short checks:

```sh
python scripts/verify-android-controller.py --serial DEVICE_SERIAL --case focus
python scripts/verify-android-controller.py --serial DEVICE_SERIAL --case home-return
python -m unittest discover -s tests -p test_northland_session.py
```

The local tests reject duplicate and missing activities, ignore other displays,
and detect recreation even when PID and task stay the same. The device checks
record both displays and input state. Review the actual game capture as well as
the identity report.

This fix preserves a running activity. It cannot restore unsaved progress after
Android kills the game, a native crash, a device reboot or an activity recreation
caused by another system change. Long background stays, memory pressure and
sleep/wake still need separate checks. We do not claim automatic save recovery.
