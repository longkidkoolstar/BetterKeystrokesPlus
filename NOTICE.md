# Scope and upstream provenance

This repository contains the newly authored Stream Proof extension, an offline
bytecode patcher, tests, and build tools. Its MIT license applies to this repository's
new code only. It grants no rights to the original client or third-party libraries.

The supplied base client identifies its source as `Kopamed/Raven-bPLUS`. That GitHub
repository returned HTTP 451 when this project was prepared. GitHub's public record
documents a copyright takedown affecting that repository and its fork network:

https://github.com/github/dmca/blob/master/2025/07/2025-07-15-raven-b4.md

This project is therefore an independent patch-source repository, not a GitHub fork
or a mirror of the upstream client. It includes neither the original JAR, a modified
full-client JAR, nor decompiled upstream source. Users supply a local base JAR they
are authorized to use; the scripts do not download the original client.

Supported base SHA-256:

`c4a15b140901b8b13a467e0a502ea7a7198fc54d280f0f39968b7a9ea9bfa408`

Build dependencies are downloaded from their original public repositories into an
ignored cache and retain their respective licenses: Minecraft Forge, LWJGL, ASM,
and JNA. None is vendored in the source release.

The experimental native capture path was designed after inspecting OBS 30.1.0's
OpenGL capture behavior. It does not modify or redistribute OBS:

https://github.com/obsproject/obs-studio/blob/30.1.0/plugins/win-capture/graphics-hook/gl-capture.c

This project is not affiliated with or endorsed by the original client developers,
OBS, Mojang, Microsoft, or Minecraft Forge.
