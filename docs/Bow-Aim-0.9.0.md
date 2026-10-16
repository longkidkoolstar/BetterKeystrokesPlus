# Bow Aim 0.9.0 prerelease

Enable **Combat > Bow Aim** and draw normally. You release every shot yourself.
The camera lines up a full-charge shot throughout charging, with a 150 ms startup
ramp. It no longer aims for the weak early-charge arc before flattening out.
At long range, releasing early can still miss because the arrow has less power.

## Readiness

**Show shot readiness** defaults ON. A small label appears below the crosshair
while drawing with a selected target. It follows the existing Stream Proof overlay.

- **Ready (green):** two consecutive ticks predict that the actual camera angle
  and actual bow power reach the moving target's interior, with a 0.05-block margin.
- **Adjusting (amber):** still charging/turning, uncertain movement, insufficient
  prediction horizon, stale data, water/lava, or an incomplete/unsupported check.
- **Blocked (red):** the assessed path meets a block or another entity first, or
  the full-power target is unreachable.

Camera, power and age changes remove green immediately. The settings description
shows the reason, distance and estimated full-charge flight time. Green indicates
model readiness, not a promise: spread, new dodges and server changes can cause misses.
Liquid checks use a conservative swept volume, so nearby liquid may withhold Ready.

Target selection considers interior heights at 35%, 50% and 65% of the hitbox. A
valid retained target is only replaced by a challenger scoring at least 20% better
for two consecutive ticks. Lost/invalid targets can be replaced immediately.
Turning follows the selected target each frame; movement analysis, ground probes
and collision searches stay on game ticks. At most 480 flight segments are checked
per tick. An unfinished check never becomes clear by default.

## Timing and prediction

Existing movement, strafe, horizon, lead-strength, range and turn-speed settings
remain. Bow-specific vertical prediction checks projected ground collision without
changing Rod Aim's predictor. A moving target outside the prediction horizon cannot
produce Ready; increase the horizon only when the longer movement assumption fits.

**Timing compensation** defaults OFF. Local sample age is always accounted for.
When enabled, the module also adds a smoothed estimate of half the local player's
reported round-trip latency, capped at 150 ms. Missing latency adds nothing. This
is an estimate of the local connection, not the age of every remote player's state.
It does not use the opponent's Tab ping or claim precise lag compensation.

## Optional shot diagnostics

**Shot diagnostics** defaults OFF. Enabling it writes under the Minecraft folder:
`keystrokesmod/diagnostics/bow/`. Two rotating JSONL files total at most 10,000,000
bytes. A bounded queue feeds a daemon writer; it never blocks the render loop.
The settings show writer failures and dropped records. Disabling it closes the
session and marks unresolved shots unknown.

Records include numeric launch/goal angles, charge, positions, lead time, prediction
mode, expected flight, observed arrow positions and likely miss causes. Names,
chat, server addresses and account identifiers are omitted. Nothing is uploaded.

- **Player hits:** a vanilla server arrow-hit marker attributable to one eligible
  owner-known tracked shot. The marker confirms a player hit but does not identify
  the victim, so this is not an independently verified intended-target hit rate.
- **Block misses:** a uniquely matched owner-known arrow observed embedded in a
  block after a short grace period.
- **Unknown:** ambiguous matches/ownership, disappearance, missing markers, timeout,
  or a session ending before enough evidence exists.

Player health changes are never used to claim arrow hits. Trajectory/movement
diagnoses are hypotheses, not proof of the server's internal state. Network arrival
delay is considered when aligning observed arrow motion with the predicted path.

## User test checklist

1. Keep the previous working JAR. Close Minecraft, replace the enabled version
   with the new JAR, and keep only one version enabled. Existing configs are reused.
2. Start with other aim modules disabled. Leave Timing compensation OFF.
3. At 10, 20, 30 and 50 blocks, hold a stationary target in view and start drawing.
   Check that the view moves smoothly toward one stable angle, without the old
   charge-driven rise and correction. Test releasing and drawing again.
4. Compare an early release with a full draw and note the readiness label. Test
   strafing, stopping, reversing, jumping and landing, plus a wall/nearby water.
5. Check the label both on your screen and in a short local recording using your
   existing verified capture setup. Native mode still does not exclude Display Capture.
6. If investigating misses, enable diagnostics and compare matching distances,
   charge levels and movement patterns. Keep unknown outcomes separate. Compare
   FPS under the same conditions against 0.8.0; real gameplay performance is unverified.

Automated fixtures, bytecode and hidden GPU/window probes are not live Minecraft
or OBS gameplay verification. The flick fix and hit-rate improvement need user feedback.
