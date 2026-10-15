# Changelog

## 0.6.0 — Tab health and experimental Bow Aim

- Added independent Entity/Tab health selectors to Nametags, ExplicitB9NameTags,
  and PlayerESP's health bar. Existing configs retain Entity by default.
- Read vanilla's synchronized Tab objective without creating missing zero scores;
  missing data falls back to entity health. Added an optional numeric-heart unit
  conversion and avoided adding entity absorption to Tab values.
- Added Bow Aim in Combat, disabled by default. While enabled it activates on bow
  draw, accounts for charge, gravity and air drag, and optionally predicts motion.
- Added range, FOV, turn speed, teammate and friend settings. Turning runs at frame
  rate; selection runs at game tick rate. No automatic firing.
- Preserved the v5 overlay implementation and added offline health/physics checks.
- Changed the local output name to BetterKeystrokesPlus-1.8.9-v0.6.0.jar.
- Gameplay on the user's server remains unverified for these new features.

## 0.5.0 — v5 OBS native experiment

- Added an opt-in GPU clean-read presentation path for OBS 30.1 OpenGL Game Capture.
- Allowed native-mode testing with ordinary Minecraft F11 fullscreen.
- Added a Minecraft display-boundary transformer and state restoration after swaps.
- Extended Overlay FPS settings to 10–240.
- Retained the desktop overlay, performance optimizations, and borderless mode.
- User reported that the native OBS/fullscreen build works in their setup.
- Published the new source and portable patch builder as an independent repository.

Earlier local prototypes introduced capture-excluded desktop rendering, Java/JNA
compatibility fixes, pooled pixel copies, and reversible borderless window sizing.
