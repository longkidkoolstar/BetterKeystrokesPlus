# Changelog

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
