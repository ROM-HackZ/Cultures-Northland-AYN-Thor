# Northland Thor

This project adds controller and two-screen support beside the owner's official
Cultures: Northland Android app. The Windows compatibility experiment is history.

- Keep the original PC installation read only. Copy owned files for testing.
- Keep retail game data, saves, screenshots, APKs and private tools out of Git.
- Work here, not in the completed Generals project.
- The target is the original game with controller and two-screen support.
- Keep the installed official Android app, license checks and saves intact.
  Our companion and ADB shell helper must not replace or re-sign that app.
- Restrict privileged input to Northland on display 0. Authenticate companion IPC
  by kernel peer UID and expose named commands only.
- Release input on focus loss, companion loss and clean shutdown. Require neutral
  controls before rearming. Never exclusively grab the controller or touchscreens.
- Input delivery is not access to game state. Do not invent live unit or map data.
- Prove menu, mission, input, sound and save/load behavior on the actual Thor.
  Clearly distinguish prototypes, confirmed behavior and planned features.
- Preserve upstream terms and credit when bringing in an engine or other code.
- Build locally. Do not create or run CI without a request.
- Do not replace, clear or uninstall existing apps or their data during testing.
