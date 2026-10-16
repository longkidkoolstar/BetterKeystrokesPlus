# Mechvibes Bridge 0.10.0 prerelease

Use the matching BetterKeystrokes+ JAR and the separate MechvibesPlusPlus bridge
copy. The normal Mechvibes app cannot see Minecraft's internal automation events.

## Start

1. Quit your usual MechvibesPlusPlus from its tray menu so two sound apps do not
   play the same physical input. The installed app is kept unchanged.
2. Run **Launch-Mechvibes-Bridge.cmd** from the delivery folder. Keep the portable
   MechvibesPlusPlus-BKBridge folder together. Closing its window minimizes to tray;
   use its tray Quit menu to exit.
3. Close Minecraft, install the supplied 0.10.0 JAR with only one client version
   enabled, then restart. Your existing BetterKeystrokes configuration is reused.
4. Enable **Client > Mechvibes Bridge**. This new module defaults OFF. The companion
   should show Connected once you return to focused gameplay.

The companion starts with a copy of the current keyboard/mouse pack choices,
volumes and mute/key-up/random settings. Its own `bridge-data` folder stores later
changes independently. It has no installer, uninstaller, startup registration or
automatic upstream update. Its launcher uses normal-user privileges.

## Sounds

- Actual left/right click attempts inside Minecraft drive mouse sounds, including
  auto-clicking and held FastPlace repetition. AutoPlace's direct placement path
  is also observed. The rate follows executed game actions, not a guessed CPS value.
- Automated KeyBinding changes drive key press/release sounds: SafeWalk Shift,
  ShiftTap, WTap/STap, AutoJump and other modules using the same input API.
  Holding a key continuously plays one down sound, then its release; it does not
  repeat every rendered frame.
- While focused gameplay is connected, the companion uses the game feed for
  keyboard and left/right mouse sounds. This avoids playing both a physical click
  and its corresponding game action. Middle mouse remains handled normally.
- On a GUI, focus loss, death, disabling the module or a missing heartbeat for
  750 ms, it returns to physical-input sounds. A short delay at focus/disconnect
  boundaries is possible; no game input is changed by sound delivery.
- Existing Mechvibes mute, Mouse Sounds, keyboard/mouse volume, key-up sounds and
  sound-pack selection still control playback. GUI inventory automation and
  third-party modules bypassing these APIs are not covered by this version.

Events contain only numeric sound codes, states, a random session and sequence
number. They travel over UDP on 127.0.0.1:39189, without internet delivery or input
logs. A bounded background sender keeps socket work off game ticks/rendering.
No OS key/mouse input is synthesized, and the bridge does not add gameplay clicks.

## Verify locally

Test left/right clickers, held FastPlace, AutoPlace, SafeWalk's Shift press/release,
ordinary movement keys, the mute toggle, focus changes and returning to the GUI.
If it stays Waiting, check that the companion is running and the new module is ON.
Leave the original sound app closed while using the companion.

Automated checks cover event/state transitions, actual click-hook bytecode,
loopback transport, invalid/stale packets, duplicate suppression and disconnect
fallback. An isolated hidden test decoded the selected packs and exercised keyboard
and mouse playback with volume zero and the native listener disabled. This confirms
the playback path, not audible live gameplay quality or performance.

The public repository contains only extension/patch-tool source. The full client,
installed Mechvibes runtime, sound packs and personal settings remain local.
