# Smoking + suppression

## Cigarette

`wflib:cigarette` (`com.wf.wflib.smoking`): hold use, `SmokingTimeline.TICKS` 210 (GAMMA `cigar1` `tm` 10.5 s), consumed on the last tick => `CigaretteSmokedEvent` (game bus, server, before shrink). Stress relief = WFMedical.

| s | |
|---|---|
| 4.0 | lit (`SmokingTimeline.LIT`) |
| 7.0-8.1, 10.0-11.1 | exhale: server `CAMPFIRE_COSY_SMOKE` at the mouth |

- First person (`smoking.client.SmokingClient`): `RenderHandEvent` cancelled; wflib `PlayerArms` solved to baked GAMMA fist tracks; pack + cigarette placeholder glb props follow baked item tracks; camera yaw/pitch/roll from the GAMMA camera effector.
- Third person: client `IClientItemExtensions.getArmPose` = vanilla `SPYGLASS` (hand at the face); server rig: `Smoking` = `AhfHooks.addHandTargets`, wrist hanging -> mouth -> hanging (`SmokingTimeline.wrist`). AHF draws hand targets only through MineAssault's adapter => no drawn effect from `Smoking` alone.
- Data `assets/wflib/smoking/cigarette.json`, 30 Hz, view space (x right, y up, -z forward): `right`/`left` fist x y z + quat, `pack`/`cig` centre + quat (long axis +Y), `cam` deg.
- Source: GAMMA `Asnen's and Grok's Better Cigarettes Animations` `asnen_cigarette.omf` (hands, `speed = 2`), `cigar1.omf` on FDDA `cigarettes.ogf` (pack `wpn_body`, cigarette `cig_body`), `asnen_cigarette.anm`; `hands_position` 0,0,0.0405 applied.
- Regenerate: MineAssault `tools/blender/stalker_hud_dump.py` (hands + item; flatpak Blender, see MineAssault `malib/stalker_sprint.py` `BLENDER`) + `.anm` import (`io_scene_xray` `xray_import.anm`, sample `matrix_world`) -> `tools/smoking/bake_cigarette.py`; models `tools/smoking/placeholder_models.py`.
- Placeholder: baseColorFactor-only glb (tip -Y, filter +Y, open-top pack); replace by overwriting `models/smoking/{cigarette,pack}.glb`, same centred long-axis-Y convention and sizes `bake_cigarette.py` prints.


## Near miss

`RoundNearMissEvent(level, player, preset, distance, velocity)` (game bus, server): a round segment (`Rounds.step`) passes within `rounds.nearMissRadius` (3.0; 0 disables) of a player's eyes.

- Closest approach inside the segment; skips the shooter's vehicle, struck players, spectators.
- A long flight can repeat it per segment; blocks stopping the segment still count.
- Cost: `level.players()` x AABB reject per entity-contact segment.
- Consumer: WFMedical stress.
- Sound `wflib:smoking.cigarette` = GAMMA `Asnen's ... Better Cigarettes Animations` `ciga1.ogg` (13.4 s mono; loaded last in GAMMA, matches clip); `SmokingEffects` starts one tracking instance per smoking player, stops when use ends. Not verified audibly (headless).
- Ember light: GemRender unshadowed point at the wrist (`SmokingTimeline.wrist` frame, origin feet+1.5), lit from `LIT`, boosted `DRAG` ticks before each exhale. `Lights.shading()==false` => no glow. Harness `wf_smoking.mjs ... midnight`.
