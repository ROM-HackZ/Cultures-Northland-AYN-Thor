# Control groups

The RB group wheel, four D-pad shortcuts and bottom-screen Groups page are
implemented in the companion. This document keeps the original design choices
and records the checks still needed for larger groups and save/load.

## What Northland already has

The owned PC manual, `Manuals/Game.pdf`, describes ten numbered groups, 0 to 9:

| Input | Game action |
| --- | --- |
| Ctrl + number | Replace that group with the current selection |
| Shift + number | Add the current selection to that group |
| Number | Select that group |

Groups can hold characters, vehicles or buildings. Soldiers and workers use
the same system. The game owns the membership; our companion sends the keys.
The live single-character check showed that assigning Knut to another slot
removed him from the previous one. A unit belongs to one numbered group.
Recalling an empty group cleared the current selection.

Static inspection of the owned Android 1.1 library found all three operations
in the primary keyboard handler. This is stronger evidence than assuming the
PC shortcuts carried over, but it is not a live Android gameplay check.

| Android function | Relative address |
| --- | --- |
| `CObjectGroupManager::Group_CreateWithSelected` | `0x24c0ec` |
| `CObjectGroupManager::Group_AddSelectedTo` | `0x24c110` |
| `CObjectGroupManager::Group_Select` | `0x24c85c` |

These addresses apply only to the reviewed library with SHA-256
`96e49d3bb5a3fcd137a5224135900e72d3a19472612ed53c2e4167f5fbceb116`.
They document the investigation. The companion will not call these addresses
or attach to the game.

The group-selection function also has a camera-centering path when the group's
members are already selected. Repeat recall should therefore center the group,
but this needs a live check. Save and load functions include the group manager;
membership persistence also needs a live check before we promise it.

## Layouts considered

| Layout sketch | Good part | Drawback |
| --- | --- | --- |
| D-pad recalls groups 1 to 4. Hold RB for a ten-slot wheel | Four groups take one press; all ten remain available. RB follows the existing RT wheel pattern | Replaces three physical shortcuts that remain on the bottom screen |
| Keep D-pad shortcuts. Hold RB, then choose a direction for a group | Keeps the old shortcuts | Every recall needs a chord, and ten slots need another page or control |
| Tap RB to open a bottom-screen group manager | Clear touch buttons for every operation | Recalling a group interrupts the controller flow and needs another selection |

Use the first layout, with a bottom-screen Groups page as an alternative.

## Controller controls

| Input | Action |
| --- | --- |
| D-pad Up without LT | Recall group 1 |
| D-pad Right without LT | Recall group 2 |
| D-pad Down without LT | Recall group 3 |
| D-pad Left without LT | Recall group 4 |
| Same direction again | Send recall again. Let Northland handle camera centering |
| Hold RB | Open the group wheel and show the group controls on the bottom screen |
| Left stick while RB is held | Highlight one of ten slots, ordered 1 to 9, then 0 |
| A in the group wheel | Recall the highlighted group |
| Hold Y in the group wheel for about 0.7 seconds | Replace the highlighted group with the current selection |
| X in the group wheel | Add the current selection to the highlighted group |
| B, or release RB without confirming | Close without changing any group |

Holding Y shows a progress indicator and an explicit replacement label. Releasing
it early, changing slots or cancelling resets the progress. Ordinary recall
never changes membership. After one operation, dismiss the wheel and require
RB to be released before another operation.

RB opens Groups immediately. It no longer selects a civilian on press or release.
Next civilian stays on Villager, headquarters and the character list stay on
Settlement. LT, A, X, B, LB, the sticks and the RT command wheel keep their normal
behavior outside the group wheel.

Holding LT gives the D-pad to [UI navigation](ui-navigation.md). It cannot recall
a group during navigation. Release the D-pad before recalling a group afterward.

Example: box-select your archers with A and the left stick. Hold RB, point the
left stick at group 1 and hold Y until the progress completes. Release RB.
Later, D-pad Up recalls those archers. Select another archer, open the same
slot and press X to add them without replacing the existing group.

## Bottom-screen Groups page

The Groups tab has ten numbered slots and separate Select, Set from selection
and Add selection buttons. Set opens a separate Replace group confirmation.
The existing Villager, Settlement and Session tabs stay available. Holding RB
temporarily shows the group controls without losing the previously open page.

Use plain slot numbers initially. We cannot read membership or selection from
the engine, so do not show invented portraits, counts, empty-slot states or
live group names. Sending a key is not confirmation that the game accepted it.
The page must explain that the current selection is what Set and Add use.

The Actions page must require Refresh after a group recall, just as it does
after selecting another character. Do not silently claim its old buttons
describe the newly recalled group.

## Implementation and verification

`GroupCommand` restricts messages to SELECT, SET or ADD and a single digit.
`WheelState` owns the mutually exclusive RT and RB modes and the 700 ms save
hold. `GroupDirections` coalesces D-pad hat and button reports into one recall
until all directions are released. Input packets apply on the helper thread at
SYN_REPORT so a diagonal in one packet cannot become two separate recalls.

The helper releases existing keyboard modifiers before a group operation and
uses Ctrl for Set or Shift for Add. It includes modifier flags in injected key
events and releases the temporary modifier in a finally block. Group recall
invalidates any displayed Actions snapshot through a selection revision. No
selection IDs, group counts or membership are inferred from those requests.

Host checks cover all 30 bounded command messages, wheel ownership, cancelled
or interrupted replacement holds, drag protection, every D-pad direction,
diagonals and repeated reports. Run them with:

```sh
bash scripts/check-control-groups.sh
```

Device checks use `scripts/verify-control-groups.py`. Choose a spare slot for
`replace`, `add`, `touch-set` or `touch-add`, because those cases intentionally
change the game's group. Request counters establish delivery, while captured
native selection panels establish the result. The test always releases its
synthetic inputs and never launches, loads or resets Northland.

The current tutorial has only Knut. Saving him from the RB wheel and recalling
him with the D-pad worked, as did camera centering on repeat recall. All four
D-pad directions sent the correct group number once despite a held direction
and duplicate button reports. Cancelling a Y hold, changing slots while Y stayed
held, simultaneous wheels, diagonals and opening RB during a drag passed.
Bottom-screen replacement confirmation can also be cancelled without sending
a group command. See the device-check document for captures and remaining work.

Multi-unit addition, soldiers, mixed selections and membership after save/load
still need a separate gameplay session. Do not claim those passed from a
single-character input check.

## Implementation order and checks

1. Prove ordinary number recall, Ctrl-number replacement and Shift-number addition
   on the official Android game. Check modifier ordering and release. Use an
   agreed test session and spare slots; do not overwrite the player's groups.
2. Check two different selections, empty recall, repeated recall and camera
   centering. Check mixed or incompatible selections before describing support
   for every object type. Test save and load separately in a test slot.
3. Add bounded named operations for slots 0 to 9 to the authenticated helper
   protocol. Group operations use original keys, with no menu recognition.
4. Add a group-wheel mode that cannot overlap the RT command wheel or an active
   selection drag. Open it only with LT and A released. Opening RB during a drag
   must not finish the drag or issue an order; require a fresh RB press later.
   While a wheel owns input, consume its buttons and stick motion. Reject a
   second wheel until both wheel controls return to neutral.
5. Add one-shot D-pad recall, handling both the vendor hat axes and button events
   without duplicates. Diagonals must not recall two groups. Holding a direction
   must not repeatedly recall or unexpectedly center the camera.
6. Add replacement progress and bottom-screen controls. Require all selection
   and confirmation controls to return to neutral after a wheel operation.
7. Verify on the Thor that recall selects the intended units, Set replaces,
   Add preserves existing members, cancellation changes nothing and no wheel
   press leaks a click or order. Focus loss and companion loss must release
   every modifier and cancel unfinished operations.
8. Update the README controls only after the new mapping is installed and checked.

Do not infer that a group is empty from companion restarts or erase game groups
when our helper reconnects. Northland remains responsible for its own groups.
