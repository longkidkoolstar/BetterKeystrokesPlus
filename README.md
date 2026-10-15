# BetterKeystrokes Stream Proof

Personal Stream Proof extension and patch builder maintained by **longkidkoolstar**
for the supplied Minecraft 1.8.9 BetterKeystrokes/Raven b+ client.

**Current release: 0.5.0 (v5 OBS experiment).** The owner reports that v5 works in
their Minecraft/OBS setup. This is a user report, not a guarantee across recorders,
OBS versions, drivers, or machines.

## Features

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
git clone https://github.com/longkidkoolstar/betterkeystrokes-streamproof.git
cd betterkeystrokes-streamproof
./build.ps1 -BaseJar 'D:/Minecraft/mods/1.8.9.BetterKeystrokes.V-1.2.jar'
```

The builder checks the base SHA-256, downloads and checks dependency hashes, and
writes `dist/BetterKeystrokes-1.8.9-StreamProof-v5-OBS.jar` plus build metadata.
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

Checks cover modified bytecode, overlay separation, color/row conversion, borderless
restoration, a real display swap, an OBS 30.1 copy-sequence simulation, and ASM 5
transformer compatibility. Test windows remain hidden. They do not start Minecraft,
OBS, a recording, or a stream. The GitHub workflow checks repository hygiene and
compiles the standalone patcher; it cannot build the full client without a base JAR.

## Future changes

This is the source of truth for this personal version. New work should start from
this repository, preserve the verified v5 behavior, update the changelog, and ship
versioned source releases. [AGENTS.md](AGENTS.md) records maintenance instructions
for future coding chats.

Source release archives intentionally omit the original and modified full-client
JARs, local configs, credentials, and private backups.
