# Jumping between game buttons

Hold LT and press the D-pad to jump the cursor between visible game buttons.
Press A to click the outlined button. Hold a direction to keep moving through
the list. The first repeat waits 380 ms, then repeats every 180 ms.

A handles selection and dragging now. LT never presses or releases the mouse.
Without LT, the D-pad still recalls groups 1 to 4. Release the D-pad before
switching from navigation back to group shortcuts.

## What it reads

Northland draws its own interface. Android's normal focus navigation cannot
see those buttons. A fixed list of coordinates would cover the toolbar but
break when a window moved or a different list opened.

The helper captures Northland on each directional request, finds its native
button frames, and chooses a target in that direction. It also recognizes the
left toolbar's layout and uses the existing circular-menu geometry. There is
no OCR, embedded game artwork or continuous capture loop. Holding LT without
pressing a direction does not capture anything.

The existing window detector recognizes native windows with an X title button.
When one is found, targets stay inside it. The cursor enters the window at its
nearest target if it starts outside. Directional navigation stops at the edge
rather than wrapping around. Buttons are scored by forward distance and alignment.
Adjacent list rows remain separate even when their borders line up.

The blue outline shows the chosen rectangle. It clears on manual cursor
movement, an A release, B, LT release or loss of focus. A new directional request
reads a fresh frame. Nothing is clicked automatically.

## Input ownership

- Starting LT during an A drag cannot interrupt it. Release and press LT again
  after the drag to navigate.
- D-pad navigation pauses while A is held. The left stick remains available for
  manual cursor movement and A dragging.
- Camera panning pauses during LT navigation. RT and RB wheels cannot open while
  LT is held. Both wheels use the left stick when opened normally.
- LT started during a wheel waits for another LT press after the wheel closes.
- Group recalls cannot leak out of an LT direction or a direction still held
  when LT is released.

Capture and hover injection retain the game's display and focus checks and the
authenticated companion connection. Input still releases on focus or companion
loss. This does not attach to the game or change its APK.

## Checked on the Thor

The installed prototype passed these checks through the actual Odin Controller
event node, with captures reviewed separately from helper counters:

| Check | Result |
| --- | --- |
| Hold LT alone | No mouse hold, cursor movement, captures or group recall |
| LT + D-pad on the toolbar | Cursor reached the wrench button |
| A on the wrench | Original Options window opened |
| Navigate to the window's X, then A | Correct close button outlined; window closed |
| A + left stick, then LT + D-pad | A mouse hold continued; LT neither snapped nor ended the drag |
| Release test inputs | No held mouse, key, wheel or navigation state remained |

Private evidence is in `artifacts/ui-nav-quiet/`. The checked Options request
took 97 ms for capture and detection. This is one measurement, not a latency
guarantee. A later frame-detector adjustment also covers the large Exit and
Restart buttons; host checks use the actual Options capture.

Reference captures cover ten toolbar icons, seven profession rows, Options tabs
and controls, large General buttons and six separate save rows. These establish
target geometry, not live activation of every button. During inspection, tapping
an unused save slot immediately created a new save. No existing save was
overwritten, and no save was loaded.

## Limits

This uses visible UI recognition, not an authoritative list from the engine.
Selected or highlighted buttons can have a different border and be missed.
An unfamiliar popup without the recognized title chrome may not confine
navigation. Text fields, scroll arrows and every character window have not been
checked. The toolbar profile assumes the tested Thor layout. Touch and manual
cursor control remain available for anything it misses.

## Repeat checks

Pure checks require Java. The synthetic cases contain no game assets. Optional
reference profiles add coordinate and directional assertions to private captures:

```sh
bash scripts/check-ui-navigation.sh
bash scripts/check-ui-navigation.sh toolbar=artifacts/TOOLBAR.png \
  profession=artifacts/PROFESSION.png general=artifacts/GENERAL_OPTIONS.png \
  save=artifacts/SAVE_WINDOW.png
```

Those profiles expect the captured layouts used during development, including
window positions. An unprefixed image path checks general bounds and targets.

For live checks, start in idle gameplay and leave physical controls released:

```sh
python scripts/verify-ui-navigation.py --serial DEVICE_SERIAL --case hold
python scripts/verify-ui-navigation.py --serial DEVICE_SERIAL --case toolbar
python scripts/verify-ui-navigation.py --serial DEVICE_SERIAL --case drag
```

The toolbar case opens Options and closes it using A. It does not change any
setting. The drag case draws a selection box and therefore can change selection.
The `popup` and `list` cases move between targets in an already open window
without clicking. Always review the captures as well as the input-state report.
