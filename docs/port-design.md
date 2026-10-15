# Northland Thor port design

## Preserve the original game

Run the owned PC executable through a Windows compatibility runtime. Keep the original campaigns, villagers, economy, audio and save format. Controller input should cover the entire game, including its management windows and text entry.

GameHub is useful for proving whether the original game runs on this device. Its [public Lite repository](https://github.com/Producdevity/gamehub-lite) contains an APK patching system, rather than a complete app source tree we can freely adapt into a dedicated Northland app. A final runtime choice follows the compatibility test.

## Runtime choices

| Route | What it gives us | Main constraint |
| --- | --- | --- |
| GameHub profile | Fastest baseline using an app already installed | Generic mappings and no proven game-state interface for our bottom screen |
| Companion panel beside GameHub | A prototype of bottom-screen shortcuts | Must prove input delivery and controller focus across apps; selected-unit data remains unavailable |
| Dedicated app based on an open compatibility runtime | Control over input, touch, both displays and launch/setup | More runtime integration work; must pass the same real-game compatibility checks |

The intended final route is a dedicated app, provided it runs Northland reliably. [Winlator app source](https://github.com/brunodev85/winlator-app) exposes its X server's pointer and keyboard injection, making it a concrete candidate for controller integration. The inspected reference revision is `3981d86`; it is currently only a local reference checkout.

The first real-game comparison favors ARM64X/FEX: roughly 43–57 FPS in the tutorial, against 5–6 FPS in the tested x64/Box64 profile. A runtime fork must reproduce the faster result before it replaces GameHub. The Windows XInput probe now runs inside GameHub and receives its virtual controller's buttons and both sticks. Physical controls, input injection into Northland and a separate Android panel remain unproven. Test those boundaries with small prototypes before committing to the companion route or the dedicated runtime.

[Open Northland](https://github.com/s-nems/open-northland) is an independent reimplementation. Its current [development documentation](https://github.com/s-nems/open-northland/blob/main/docs/DEVELOPMENT.md) describes a converted content pipeline rather than support for this original packed Northland install and its complete campaigns. It does not satisfy the current requirement to keep the original game.

## Proposed controller controls

These bindings are a starting design based on the installed game's manual. They have not been tested in a Northland mission. The camera is fixed isometric: there is no planned camera rotation.

| Thor control | Action | Original input |
| --- | --- | --- |
| Left stick | Pan camera | Arrow keys, with an analog speed prototype to compare against middle-mouse pan |
| Right stick | Move cursor, with acceleration and a precise inner range | Mouse movement |
| A | Select or confirm; hold and move cursor to select a rectangle | Left mouse button |
| X | Give the order at the cursor | Right mouse button |
| B | Cancel placement, close the active window or deselect | Escape |
| Y | Selected character's action menu | Space |
| LT + A | Add to selection | Shift + left click |
| RT held | Command wheel; stick chooses, A confirms, B cancels | Named commands below |
| LB held | Group wheel: recall, create and add to groups | Number keys and Ctrl/Shift combinations |
| RB | Cycle civilians; LT + RB cycles scouts | Period / comma |
| R3 | Toggle zoom | Z |
| D-pad Up | Headquarters | H |
| D-pad Down | Character list | F7 |
| D-pad Left / Right | Navigate the focused wheel or panel | UI navigation |
| Start | Pause during play; explicit menu action when in a menu | P |
| Select | Open the bottom-screen management panel | App action |

The same-profession selection shortcut needs its own test. The original game uses a double left click; test LT + L3 issuing that gesture at the selected character, avoiding a timed double press of A. Reliable targeting requires either a known cursor target or a proven selected-character interface. Do not advertise selecting all on-screen villagers until the game's actual selection scope is checked.

## Wheels and management commands

Use short labels with controller prompts and a readable central description. Limit each wheel to eight choices; give a category its own page instead of squeezing more labels into the ring. Menus consume stick input while open so the camera and cursor do not move underneath them. Releasing RT dismisses the wheel without issuing a highlighted command.

| Command category | Commands from the original game |
| --- | --- |
| Build | Building B, road S, stockade D, gate Shift+D |
| Selected villager | Actions Space, profession C, assign home W, assign workplace A, observation O |
| Settlement | Headquarters H, characters F7, statistics F6, technology F8, diplomacy F5 |
| Session | Pause P, speed L, save F4, load F3, quicksave F9, options F2, help F1 |
| Map | Small map F10, large map Ctrl+F3, zoom Z, tooltips Tab |
| Selection | Add Shift+click, remove Ctrl+click, same profession double click, groups 0–9 |

Right-click assignments and the outdoor worker's Ctrl+right-click work-area command also need explicit controller access. Add a contextual work-area action that enters targeting mode. B cancels before sending the order. Do not import Generals commands such as attack-move or stop without confirming Northland's equivalent behavior.

## Touch and both screens

The top screen shows the complete game image with an accurate pointer and no clipped bottom edge. Preserve the game's aspect ratio unless a wider game resolution actually renders correctly. Recompute all touch and cursor coordinates from the displayed game rectangle when the resolution changes.

On the top screen, tap selects and one-finger drag makes a selection rectangle. Two fingers pan using the original middle-mouse behavior. Compare pinch translated to the game's zoom toggle with an explicit zoom button; continuous zoom is not established. No rotation gesture is planned for a fixed isometric camera.

The first bottom-screen panel can show clear, touchable commands, groups and help prompts. Opening a management window should make the corresponding controller navigation mode explicit and keep B as a predictable way back. Text fields need an Android keyboard that returns focus cleanly to the game.

A later panel can show the selected villager's profession, needs, home, workplace and available actions, plus settlement stocks and a minimap. These features depend on reading live game state. Screen scraping or guessed coordinates are not a reliable substitute for that interface. Prove a small Windows-side bridge against a supported executable hash before expanding it: export selected-object identity and camera state first, then show only fields that the bridge actually supplies. If that cannot be made dependable, keep the original management windows accessible and report the reduced second-screen scope.

For a separate minimap, use the game's own map-to-camera relationship. Test center, corners and edges against the original map. A tap must jump to the tapped location, and a drag must keep following the finger. Scaling the terrain bitmap alone does not establish the correct coordinates.

## Implementation stages

| Stage | Concrete result | Verification before moving on |
| --- | --- | --- |
| 1. Compatibility | Original Northland menu and tutorial on Thor | Selection, orders, audio, save and reload |
| 2. Controller prototype | Camera, cursor, face buttons, groups and a small command wheel | Actual hardware buttons; no stuck keys after pause, focus loss or disconnection |
| 3. Touch and bottom panel | Selection gestures, camera pan and management shortcuts | Both screens, correct transforms, no cropped UI, reliable focus and keyboard return |
| 4. Context integration | Bridge-backed selection and camera state | Version checks, unavailable-state behavior, comparison with the original UI |
| 5. Full command coverage | Villager assignments, building, trade, groups and all management windows | Play the tutorial and a campaign mission without a mouse or keyboard |
| 6. Install and release | App and a simple setup flow for owned game files | Fresh setup, interrupted-copy recovery, updates and save preservation |

Controller processing and bottom-screen taps should feed the same named command model. A single session owns pressed keys and mouse buttons; it releases them on focus loss, panel changes and device disconnect. Keep the screen transform in one place. This avoids the input-routing and minimap-coordinate problems already encountered while building the Generals port.
