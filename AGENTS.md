# Maintenance instructions

This repository is longkidkoolstar's personal BetterKeystrokes Stream Proof extension.
Use it as the source of truth for future feature requests.

## Preserve the working behavior

- Baseline is v5 / 0.5.0. The owner reported that the native OBS mode works.
- Keep desktop capture-excluded mode and OBS native mode distinct. Native mode is
  recorder-specific and does not hide Display Capture. Never claim universal hiding.
- Runtime compatibility: Minecraft 1.8.9, Forge 11.15.1.2318, Java 8, LWJGL 2.9.4,
  Forge ASM 5, and Minecraft's JNA 3.4. Read JNA calling-convention constants at runtime.
- Keep F9, existing config paths, startup recovery, and reversible borderless sizing.
- Do not silently install builds, change user configs, replace backups, start a
  recording/stream, or change OBS settings unless the user requests those actions.

## Repository boundaries

- Publish only the new extension/patcher source and its tools. See NOTICE.md.
- Do not commit or publish original/decompiled upstream code, original or patched
  full-client JARs, personal config files, credentials, recordings, or private backups.
- Keep input JARs and dependencies in ignored locations. The supported base is pinned
  by SHA-256; investigate a mismatch rather than silently accepting a different client.
- Do not overwrite an already-installed working build just to run a test.

## Build and check

- `./build.ps1 -BaseJar <authorized-local-base.jar> [-JavaHome <jdk8>]`
- `./verify.ps1 -BaseJar <base.jar> [-MinecraftJar <1.8.9-client.jar>]`
- `python scripts/check_repo.py` runs the public CI hygiene/patcher compilation check.
- GPU/window tests require a Windows desktop; clearly distinguish isolated tests,
  simulated OBS copy behavior, and real user-recorded Minecraft/OBS verification.
- Keep test classes out of runtime JARs. Do not package stale compiled classes.

## Changes and releases

Source is under `src/`, regression probes under `test/`. Build scripts share
`scripts/BuildContext.ps1` and pinned dependencies in `dependencies.json`.
For a release, update VERSION, CHANGELOG.md, README and any affected usage docs.
Preserve user-facing distinctions between overlay refresh and Minecraft game FPS.
Use versioned releases of this repository for future updates. Source-only releases
must not contain the original or modified base client.
