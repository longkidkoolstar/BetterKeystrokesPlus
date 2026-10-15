# Changelog

## 0.8.0 — Rod Aim

- Added Rod Aim under Combat, disabled by default. It turns smoothly while holding
  an uncast rod and stops on cast input, an active hook, item switching or a GUI.
- Kept casting/reeling manual. A bounded pending-cast pause covers server spawn
  delay without remaining stuck after rejected casts.
- Added the hook's own vanilla 1.8.9 air trajectory, including gravity-before-drag
  order, speed and launch offset. Unreachable targets are skipped.
- Reused motion history/strafe forecasts with independent range, FOV, turn speed,
  prediction horizon, lead strength, teammate and friend settings.
- Added rod activation timelines and independent stationary/moving hook tests.
- Preserved Bow Aim, Tab health and the v5 overlay. Live rod fights need testing.

## 0.7.0 — Adaptive bow prediction

- Retained distance, charge, gravity and air-drag compensation; added independent
  forward simulations at 5, 20, 50 and 80 blocks plus partial-charge regression.
- Replaced one-tick linear movement with a bounded local history, stationary jitter
  suppression, faster stop/reversal response and a short vertical-motion estimate.
- Added opt-out repeated-strafe prediction after three matching cycles, with
  confidence blending and fallback when recent movement breaks the match.
- Added Prediction horizon and Lead strength controls. The module description shows
  target distance, flight time and prediction mode. Activation remains bow draw.
- Added deterministic tests for noisy forward strafes, irregular motion, stop/turn
  fallback, teleports, missing/duplicate samples and history/horizon bounds.
- Kept Tab health and the v5 overlay behavior. New aim behavior needs live testing.

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
