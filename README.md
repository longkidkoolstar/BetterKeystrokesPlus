# BetterKeystrokes+

Personal feature extension and patch builder maintained by **longkidkoolstar**
for the supplied Minecraft 1.8.9 BetterKeystrokes/Raven b+ client.

**Current prerelease: 0.10.0 (Mechvibes game-input sound bridge).** The owner reports that the earlier v5 overlay works in
their Minecraft/OBS setup. This is a user report, not a guarantee across recorders,
OBS versions, drivers, or machines.

## Features

- **Mechvibes Bridge:** local sound events for actual auto-click/placement actions
  and automated keys, using a separate Mechvibes copy with your selected sounds.
- **Auto Tool:** reliable first-block switching and return-slot ownership, manual
  hotbar priority, and a configurable combat hold for deliberate mining under attack.
- **Tab health:** choose Entity or Tab independently in Nametags,
  `(ExplicitB9)NameTags`, and PlayerESP's health bar.
- **Bow Aim:** stable full-draw aiming while charging, separate actual-charge flight
  checks, moving-hitbox prediction, readiness feedback and optional local diagnostics.
- **Rod Aim:** smooth pre-cast aiming while holding an uncast fishing rod, with
  its own hook trajectory and optional movement/strafe prediction.
- **OBS native mode:** GPU-only overlay composition with a clean read framebuffer
  targeting OBS 30.1 OpenGL Game Capture. Permits testing ordinary F11 fullscreen.
- **Desktop overlay mode:** separate, click-through window with Windows capture
  exclusion. Requires windowed Minecraft; includes a borderless fullscreen option.
- **Overlay FPS:** configurable from 10 to 240. Higher values improve overlay
  smoothness and increase work; desktop mode additionally pays CPU readback costs.
- **F9 toggle**, existing config paths, startup checks, and reversible window changes.

Native mode does **not** hide visuals from Display Capture or every other recorder.
The client still disables incompatible scene-changing modules while the mode is on.
See [usage and limitations](docs/OBS-native-mode.txt).

## Build your local client

This repository contains our extension and patcher, not the original client. See
[provenance and license scope](NOTICE.md). Supply your own supported base JAR.

Requirements: Windows, PowerShell, a Java 8 **JDK** with `java` and `javac` on PATH,
and internet access for pinned public build dependencies.

```powershell
git clone https://github.com/longkidkoolstar/BetterKeystrokesPlus.git
cd BetterKeystrokesPlus
./build.ps1 -BaseJar 'D:/Minecraft/mods/1.8.9.BetterKeystrokes.V-1.2.jar'
```

The builder checks the base SHA-256, downloads and checks dependency hashes, and
writes `dist/BetterKeystrokesPlus-1.8.9-v0.10.0.jar` plus build metadata.
It does not modify the input JAR, install a mod, edit configs, or operate OBS.
An original JAR renamed to `.jar.disabled` is also accepted when passed explicitly.
Use `-JavaHome 'D:/Java/jdk8'` if the required JDK is not on PATH.

Close Minecraft before replacing its enabled BetterKeystrokes JAR. Keep only one
version enabled, and retain a backup of the original and both config folders
(`keystrokes` and `keystrokesmod`). Closing the mod GUI can save config changes.

## Enable the OBS fullscreen experiment

1. Start Minecraft, initially windowed, and enter a world.
2. Open **Client**, then **right-click Stream Proof** to expand its settings.
3. Set **OBS native mode ON**, **Borderless fullscreen OFF**, and **Overlay FPS 60**.
4. Enable Stream Proof or press **F9**. Try ordinary **F11 fullscreen**.
5. In OBS use **Game Capture**, with **Capture third-party overlays OFF**.
6. Check a short saved local recording against the physical screen before streaming.

For the separate Windows overlay, turn OBS native mode OFF. Its borderless checkbox
is inside the Stream Proof module, not Minecraft's normal Fullscreen on/off setting.

## Health source and Bow Aim

In **Render**, expand **Nametags**, **(ExplicitB9)NameTags**, or **PlayerESP**.
Set **Health source (Entity/Tab)** to **2** for Tab; **1** retains the original
entity health. Keep **Tab scores are hearts OFF** when the server sends health
points (20 means full normal health, including vanilla Tab heart icons). Turn it
ON only for servers whose numeric scores count hearts (10 means full health).
PlayerESP's existing **Health** checkbox still controls whether its bar is drawn.

Tab mode reads the synchronized scoreboard objective in display slot 0, the same
data vanilla Tab renders. You do not need to open Tab. Missing objectives or player
scores fall back to entity health; a genuine zero stays zero. Tab values are used
as supplied, without adding potentially spoofed entity absorption again. This
cannot reveal data the server does not send or correct a spoofed Tab score.
Servers that draw health only in custom Tab name text are not supported yet.
Select Tab only when that scoreboard actually represents health: some servers
use the slot for kills, ping, or other values.

In **Combat**, enable **Bow Aim**. It is OFF by default and activates automatically
while using a bow. You still release shots yourself. Settings control range,
FOV (full cone angle), turn speed in degrees/second, movement prediction, and
ignoring teammates/friends. It skips invisible/dead players, blocked line of sight,
and entities rejected by the existing AntiBot module. Friend filtering uses the
existing AimAssist friend list. Disable other aim modules while testing it.

The camera uses the full-charge trajectory from the beginning of each draw. This
avoids steering upward for a weak early shot and then back down as charge grows.
Actual charge is used separately to check readiness; manual early releases can
still miss. Startup turns ramp over 150 ms and retain the existing turn-speed cap.
Details and a live-test checklist: [Bow Aim 0.9.0](docs/Bow-Aim-0.9.0.md).

Distance and bow charge determine arrow drop and flight time. Version 0.7.0 introduced
up to 96 ticks of motion history per nearby player while Bow Aim is enabled,
including between draws. Small stationary jitter is ignored; stops and reversals
replace stale movement estimates. Brief vertical motion uses a bounded jump
estimate, with a recent ground level when available.

**Strafe patterns** (default ON) recognizes repeated horizontal cycles only after
at least three matching cycles with several direction changes. It blends the
cycle forecast according to its fit and falls back to recent movement when a new
stop/turn breaks the match. Irregular movement is not treated as a learned pattern.
Patterns are kept locally in memory and reset on world changes, missed samples,
teleports, or disabling the module. Prediction runs at game tick rate; no learning
work runs in the render loop.

**Prediction horizon (ticks)** (default 40, range 5–60) caps motion extrapolation,
not arrow flight or range. **Lead strength (%)** (default 100, range 0–120) tunes
the predicted movement offset. Set **Strafe patterns OFF** for recent-movement
prediction only, or **Movement prediction OFF** for stationary-target aiming.
The module's description shows readiness, its reason, last target distance,
estimated full-charge flight time and prediction mode.

Full paths now check known block shapes and intervening entities, with up to three
candidate traces plus the actual-angle shot and a 480-segment/tick cap. Water,
lava, exhausted checks and unsupported data cannot produce Ready. These estimates
cannot predict future dodges, random arrow spread or custom server physics.
Live server aim behavior still needs user verification.

## Auto Tool

In **Player > Auto Tool**, the existing **Hotkey back** and **Max delay** settings
are preserved. First-block delays now start reliably; the original slot is saved
once and restored when mining stops or you return to attacking an entity. Manual
scroll/hotbar input takes priority until Attack is released.

**Combat mining guard** defaults ON. For one second after attacking/aiming attack
at a player, hold the same block for **Combat hold (ms)** (180 ms initially) before
an automatic tool switch. Sustained mining can continue across blocks while you
take damage. This is a configurable intent heuristic, not perfect intent detection.
Details and a gameplay checklist: [Auto Tool 0.9.1](docs/Auto-Tool-0.9.1.md).

## Mechvibes Bridge

Enable **Client > Mechvibes Bridge** with the separate portable Mechvibes companion
running. Quit the original sound app first to avoid duplicate physical playback.
The module defaults OFF. Actual left/right game clicks and automated key changes
drive the selected sound packs; ordinary physical sounds resume outside gameplay.
Settings, setup and scope: [Mechvibes Bridge 0.10.0](docs/Mechvibes-Bridge-0.10.0.md).

The optional companion builder needs a reviewed local MechvibesPlusPlus v2.4.0
installation and Python on Windows. Only patch/build source is published. Node.js
is required for the public sound-router checks; it is not a runtime requirement
for using the portable companion.

## Rod Aim

In **Combat**, enable **Rod Aim** (OFF by default). Hold a fishing rod with no
hook out: the camera turns smoothly toward a predicted torso intercept. You cast
and reel manually. It stops on your cast input, on an active hook, on switching
items, on death or while a GUI is open. Once the hook is reeled in or disappears,
it may line up the next cast if you are still holding the rod.

The cast-input pause covers the delay before a server-spawned hook appears. If a
cast was rejected or canceled and no hook arrives, it can resume after 1.5 seconds.
Mouse/keyboard detection respects the configured Use Item binding, with an
interaction-event fallback. No input event is canceled or generated by Rod Aim.
Only local-player interactions affect its cast state.

Settings: **Range** (12 blocks default, adjustable 3–20), **FOV** (70 degrees),
**Turn speed** (240 degrees/second), **Movement prediction**, **Strafe patterns**,
**Prediction horizon** (20 ticks), **Lead strength** (100%), **Ignore teammates**,
and **Ignore friends**. It uses the same in-memory motion tracker as Bow Aim and
respects the existing AntiBot filter. The description shows the last distance,
flight-time estimate and prediction mode. Disable other aim modules if they fight
over your camera. Bow Aim naturally stays inactive while holding a rod.

Rod Aim uses vanilla 1.8.9 hook speed, gravity, drag and spawn offset rather than
bow physics. Range is an acquisition limit; unreachable casts are skipped. A
clear line of sight is required, but the full future hook path is not checked for
collisions. Water, server physics changes, latency, random vanilla cast spread
and sudden dodges can cause misses. Switching and casting immediately may not
give smooth turning enough time; it never delays your click to finish lining up.
Live mid-fight behavior remains unverified.

## Verification

After building, run the GPU/window probes on a Windows desktop with a working
OpenGL driver:

```powershell
./verify.ps1 -BaseJar 'D:/Minecraft/mods/original.jar' `
  -MinecraftJar 'D:/Minecraft/libraries/minecraft-1.8.9-client.jar'
```

`-MinecraftJar` is optional; it enables checks against actual Minecraft presentation
bytecode. `-CompatibilityJava` optionally selects another Java 8 executable, such as
the launcher runtime, for the legacy JNA compatibility probes.

Checks cover modified bytecode and health/module hooks, scoreboard fixtures,
independently simulated arrow interceptions, distance/charge regressions, stationary
jitter, stops, reversals, noisy repeating strafes, irregular-motion fallback and
history resets, overlay separation, color/row conversion, borderless
restoration, rod input/active-hook transitions and independent hook-flight simulations,
a stable bow charge sweep, 30–240 FPS control tests, full-path collision cases,
diagnostic ambiguity/log bounds and readiness-label capture separation,
Auto Tool delay/return-slot/combat-hold timelines and restore-before-attack hooks,
a real display swap, an OBS 30.1 copy-sequence simulation, and ASM 5
transformer compatibility. Test windows remain hidden. They do not start Minecraft,
OBS, a recording, or a stream. The GitHub workflow checks repository hygiene,
compiles the standalone patcher, and runs the health/physics checks; it cannot
build the full client without a base JAR.

## Future changes

This is the source of truth for this personal version. New work should start from
this repository, preserve the verified v5 behavior, update the changelog, and ship
versioned source releases. [AGENTS.md](AGENTS.md) records maintenance instructions
for future coding chats.

Source release archives intentionally omit the original and modified full-client
JARs, local configs, credentials, and private backups.
