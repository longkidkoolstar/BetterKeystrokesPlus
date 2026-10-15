# BetterKeystrokes+

Personal feature extension and patch builder maintained by **longkidkoolstar**
for the supplied Minecraft 1.8.9 BetterKeystrokes/Raven b+ client.

**Current release: 0.8.0 (Rod Aim).** The owner reports that the earlier v5 overlay works in
their Minecraft/OBS setup. This is a user report, not a guarantee across recorders,
OBS versions, drivers, or machines.

## Features

- **Tab health:** choose Entity or Tab independently in Nametags,
  `(ExplicitB9)NameTags`, and PlayerESP's health bar.
- **Bow Aim:** opt-in aim while drawing, with charge-dependent gravity/air-drag
  compensation, motion history and optional repeated-strafe prediction,
  range/FOV, and smooth frame-rate turning.
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
writes `dist/BetterKeystrokesPlus-1.8.9-v0.8.0.jar` plus build metadata.
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

Distance and bow charge determine arrow drop and flight time. Version 0.7.0 keeps
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
The module's description shows the last target distance, estimated flight time,
and prediction mode (Still, Strafe, Recent motion, or Off).

These are estimates from observed movement. Sudden future dodges, latency, water,
custom server physics, unknown terrain and collisions remain unmodeled. A clear
line of sight does not guarantee a clear complete arrow arc. Live server aim
behavior still needs user verification.

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
