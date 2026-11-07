# Corpses

System `corpse`, requires `ragdoll`. API `corpse.api.Corpses`. Replaces Corpse (henkelmax 1.1.13); both installed => both cancel drops, drop `corpse` from the pack. Config `wflib-corpse.toml`.

## Divergences from Corpse
- Body = death ragdoll: player's running sim handed off, else started from the rig at death (killing hit still throws it). Persisted `Body` = 11 x (pos, quat).
- Stacks remember their slot: `CorpseSlot` kinds `inventory` (index), `backpack`, `carry`, `curios` (`id/index`). Transfer: slot free => back in it, else inventory, else stays. No swap: viewer's own items never enter the corpse.
- One menu: worn row, 36 main, scrolling "Other" (curios + loose drops); no additional-items page.
- Explosion throws, AHF rounds shove (`Ragdolls.hit`); never damaged. `/kill` drops the contents.
- Carriable ([ragdoll.md](ragdoll.md#carry)); carried => not pickable, menu closed.
- No skeleton stage, death history, `lie_on_face`, `lava_damage`, Jade tooltip.

## Capture
- `LivingDeathEvent` LOWEST uncancelled => snapshot. `LivingDropsEvent` LOWEST => every drop -> entry, `ItemStack.matches` first unclaimed snapshot stack; drops cancelled. Mods drop copies => identity match FORBIDDEN.
- keepInventory => corpse holds only what mods still drop.
- Open Persistence body killed => `GraveHelper.claim` -> `Corpses.claim(save, profile, body)`: every kind `take`n from the loaded save, OP writes it back.

## Traps
- `CorpseSlot.take` MUST NOT sync: OP passes a connectionless `ServerPlayer`.
- Dead players not drawn (`RenderPlayerEvent.Pre`): corpse is the body from the death tick.
- Below min build height => body frozen, no void fall.
- `BackpackLayer` draws inside AHF `ArmorRenderer.begin/end`: outside, GemRender copies part rotations only => pack at standing height on a ragdoll.
- Claim: Curios load moves stacks failing `isItemValid` to its invalid list; save never ticks => `handleInvalidStacks` before reading (-> inventory entries), else lost on write-back.

## Verified
- Harness `wf_corpse` / `wf_carry` / `ma_corpse_backpack` / `wf_op_corpse` (dedicated, OP claim): death, transfer, despawn, curios / kit / MA backpack capture, reload, carry.

## Gaps
- Box fixed 1.8 x 0.5 at the torso, not the body.
