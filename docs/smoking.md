# Smoking + suppression

## Cigarette

`wflib:cigarette` (`com.wf.wflib.smoking`): tap use => `Smoking.Session` (synced attachment `wflib:smoking`: hand + start game time), `SmokingTimeline.TICKS` 186 (GAMMA `cigar1` `tm` 10.5 s minus cut `START` 1.2 s), consumed on the last tick => `CigaretteSmokedEvent` (game bus, server, before shrink). Cigarette leaves the hand => session dropped, nothing consumed. Not vanilla item use: release always ends it (`LivingEntity.releaseUsingItem` -> `stopUsingItem` unconditional). Stress relief = WFMedical.

| s | |
|---|---|
| 2.8 | lit (`SmokingTimeline.LIT`) |
| 5.8-6.9, 8.8-9.3 | exhale: server `SMOKE` at the mouth |

- First person (`smoking.client.SmokingClient`): `client.view.HandClip` + `HandClipView` (reused by WFMedical treatment hands): `RenderHandEvent` cancelled; wflib `PlayerArms` solved to baked GAMMA fist tracks; cigarette + lighter placeholder glb props follow baked item tracks; camera yaw/pitch/roll from the GAMMA camera effector.
- Fingerless fist: GAMMA fingertip grip floats 4-6 cm off the knuckles => bake clamps `cig` into the fist box while right-held (`CIG_GRIPS`, `handclip.grip`). Butt (+Y) aimed at `FACE` (mouth, view space) from start to flick (`AIM`). Source 0-1.2 s (out of the pack) cut, pack not baked; fists rise in over `LEAD`. Lighter: no GAMMA mesh; right fist cups the tip 3.4-4.9 s source => `lighter` track aimed at the mean tip, shown via scale column (`LIGHTER_SHOW`).
- Third person: client `IClientItemExtensions.getArmPose` = vanilla `SPYGLASS` (hand at the face); server rig: `Smoking` = `AhfHooks.addHandTargets`, wrist hanging -> mouth -> hanging (`SmokingTimeline.wrist`). AHF draws hand targets only through MineAssault's adapter => no drawn effect from `Smoking` alone.
- Data `assets/wflib/smoking/cigarette.json`, 30 Hz, view space (x right, y up, -z forward): `right`/`left` fist x y z + quat, `cig` centre + quat (long axis +Y), `lighter` centre + quat + scale, `cam` deg.
- Source: GAMMA `Asnen's and Grok's Better Cigarettes Animations` `asnen_cigarette.omf` (hands, `speed = 2`), `cigar1.omf` on FDDA `cigarettes.ogf` (pack `wpn_body`, cigarette `cig_body`), `asnen_cigarette.anm`; `hands_position` 0,0,0.0405 applied.
- Regenerate: MineAssault `tools/blender/stalker_hud_dump.py` (hands + item; flatpak Blender, see MineAssault `malib/stalker_sprint.py` `BLENDER`) + `.anm` import (`io_scene_xray` `xray_import.anm`, sample `matrix_world`) -> `tools/smoking/bake_cigarette.py` (shared baker `tools/handclip/handclip.py`); models `tools/smoking/placeholder_models.py`.
- Held item: `CigaretteItemClient` = GemRender item renderer on `cigarette.glb` (scaled to the cell), placement per context in `models/item/cigarette.json` `display`.
- Placeholder: baseColorFactor-only glb (tip -Y, filter +Y, open-top pack (unused since the cut), lighter flame +Y); replace by overwriting `models/smoking/{cigarette,lighter}.glb`, same centred long-axis-Y convention and sizes `bake_cigarette.py` prints / `lighterSize`.


## Near miss

`RoundNearMissEvent(level, player, preset, distance, velocity)` (game bus, server): a round segment (`Rounds.step`) passes within `rounds.nearMissRadius` (3.0; 0 disables) of a player's eyes.

- Closest approach inside the segment; skips the shooter's vehicle, struck players, spectators.
- A long flight can repeat it per segment; blocks stopping the segment still count.
- Cost: `level.players()` x AABB reject per entity-contact segment.
- Consumer: WFMedical stress.
- Sound `wflib:smoking.cigarette` = GAMMA `Asnen's ... Better Cigarettes Animations` `ciga1.ogg` (13.4 s mono; loaded last in GAMMA, matches clip) minus first 1.2 s (`START`), vorbis q5; `SmokingEffects` starts one tracking instance per session, stops when the session ends or changes. Sound outlives the clip; naturally ended instance never `isStopped` => keyed by session, not by stopped flag. Not verified audibly (headless).
- Ember light: GemRender unshadowed point at the wrist (`SmokingTimeline.wrist` frame, origin feet+1.5), lit from `LIT`, boosted `DRAG` ticks before each exhale. `Lights.shading()==false` => no glow. Harness `wf_smoking.mjs ... midnight`.
