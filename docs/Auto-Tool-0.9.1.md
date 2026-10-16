# Auto Tool 0.9.1 prerelease

Expand **Player > Auto Tool**. Your existing module toggle, keybind, **Hotkey back**
and **Max delay** values are kept. Nothing is installed and no config is edited by
the build. Close Minecraft to install the test JAR, with only one version enabled.
Keep the previous working build so you can switch back.

## Switching and returning

- The delay starts on the first block, including a block of the same material you
  mined previously. The old delay could stay idle until a different block type appeared.
- Only an actual automatic slot change saves your original slot. Changing tools
  while continuing to mine does not overwrite it.
- With **Hotkey back ON**, releasing attack, looking at air/an entity, opening a GUI,
  losing game focus, using an item, or disabling the module returns to that slot.
  A real entity attack restores it before vanilla synchronizes the held item and
  sends the attack packet. With Hotkey back OFF, the chosen tool stays selected.
- Your own scroll/hotbar selection takes priority, including pressing the number
  for the currently selected tool. Auto Tool waits for attack to be released before
  taking over again. It never forces a saved slot over a later manual selection.
- World, player and inventory changes discard stale state. They cannot restore a
  slot from the previous world/player.
- Attack and hotbar bindings are read from Minecraft's controls, including a
  keyboard-bound Attack action. LeftClicker's existing Break blocks option is respected.
- Selection considers the actual block state, dig speed, Efficiency and whether the
  item can harvest the block. Equal scores keep the held item. No faster item means
  no automatic switch and no saved return slot.

## Combat mining guard

**Combat mining guard** defaults ON. During the one second after attacking/aiming
attack at a player, a new mining attempt must stay on the same block for
**Combat hold (ms)**, initially **180 ms**, before equipping a tool. The wait is the
greater of this hold and the existing sampled Max delay, rather than their sum.

Briefly catching a wall or floor during a fight should therefore leave your weapon
selected. Deliberately hold attack on the wool/wood/stone to start mining. After it
starts, continuous mining can move across blocks and change tools without repeating
the combat hold. Returning to an entity/air ends that mining session and restores
the original slot when Hotkey back is enabled.

Incoming damage does not disable or interrupt Auto Tool. You can keep breaking
bed defenses while being hit. The guard cannot read intent perfectly: a long
accidental block hold can still qualify, and a fight before any player targeting
or attack will not have armed the recent-combat window. Raise the hold for stronger
protection, lower it for quicker deliberate mining, or turn the guard OFF.

## Live test checklist

1. Test a first block after enabling, repeat the same material after releasing,
   and change between wool/wood/stone. Verify the original slot is saved once.
2. Stop by releasing Attack, aiming at air, returning to a player, opening the
   inventory and disabling Auto Tool. Check Hotkey back both ON and OFF.
3. During mining, scroll or press a hotbar key, including the current tool's key.
   Your selection should stay until you release Attack.
4. Attack a player, briefly catch a block, then deliberately hold on a bed defense.
   Check both accidental-switch suppression and continued mining while taking hits.
5. Adjust Combat hold if your click timing needs a shorter or longer wait.

Automated timelines and actual Minecraft action-hook bytecode are checked on
Java 8/ASM 5. Live Bedwars behavior still needs gameplay feedback; tests do not
launch Minecraft or OBS or modify the installed build.
