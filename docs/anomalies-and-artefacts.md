# Anomalies, anomaly sites, artefacts, detectors (STALKER Zone project)

Handoff doc: what the project is, what exists, where it lives, how to build and test it, and what is left. State
as of 2026-10-29. Read this top to bottom before touching anything; the "Gotchas" section has cost real hours.

## The goal (as the user put it)

- STALKER (GAMMA) style anomalies in Warfactory. **Optional, off by default** (`wflib-anomalies-server.toml`
  `anomalies.enabled=false`) until the user says otherwise.
- **Anomaly sites**: STALKER-scale areas, not cluttered blobs. Few anomalies, lots of terrain and obstacles
  (craters, ravines, fissures, boulder mazes...), some with **ruins based on vanilla structures** (villages,
  jungle/desert temples), **biome-fitting variants** (badlands get terracotta, not dirt). **Tiered 1-4**: higher
  tiers have harder anomalies and better artefacts. Areas are **picked or auto-generated in wfworldgen**.
- **Detection and collection**: artefacts lie hidden in sites; a **detector** finds them. Three tiers after
  STALKER's Echo / Bear / Veles. The user plans **their own model for the basic detector**. **Bolts** are thrown to
  probe anomalies. Detector and bolt are drawn **through MineAssault's arm system** (first-person arms).
- **Artefacts** from GAMMA's list, **no junk ones**, worn in Curios `artifact` slots, with GAMMA's numbers.
  **Psy = WFMedical stress** ("stress is our own version of psy").
- The user wants **prepared tests for all these features**.

## Repos and how the pieces connect

Checkouts side by side in one workspace (`~/projects/wf2-21/`; older machine `~/Codebases/`).

| repo | dir | branch -> remote | role |
|---|---|---|---|
| WFLib (mod id `wflib`) | `WF-Ballistics` | `main` -> `origin/main` (Warfactory-Official/WfLib) | anomalies, sites, artefacts, detectors, bolts, screen effects |
| WFMedical | `WFMedical` | `neoforge-1.21.1` -> `origin/neoforge-1.21.1` | stress (= psy), healing / drain / clot / thin, GAMMA hit + psy screen effects |
| WFWeight | `Ultimate-Weight` | `master` -> `origin/master` | `api/StaminaModifiers` (artefact power_restore) |
| wfcore | `Warfactory-Modern-Core` | `master` -> `origin/1.21.1` | `AnomalySiteDensity`: site density from worldgen resource rules |
| wfworldgen (Go) | `worldgen-workspace` | `master` -> `origin/master` | "Anomalies" section: pick fields / autogen cells per tier |
| MineAssault | `MineAssault` | `master` (+ `origin/instance/ma-0f896c4-wflib-main`) | `ZoneHandsViewmodel`: detector on DAR hands + bolts in first person; `libs/wfweight-*.jar` |
| GemRender | `GemRender` | `main` | glTF rendering, lens pass, lights; WFLib / MineAssault need 0.1.19 (`GltfSkeleton`, `flushOpaque`) |
| mc-harness | `mc-harness` | (local) | headless client checks: `scenarios/wf_zone.mjs` |

Data flow: wfworldgen writes resource rules (system `anomaly`, targets `wflib:anomaly_site_tier_1..4`) -> wfcore
`integration/wflib/AnomalySiteDensity` sets `AnomalySites.density` -> WFLib's `anomaly_site` structure picks a tier
per candidate chunk -> `SitePainter` builds terrain, ruins, anomalies and hidden `ZoneArtifact`s -> players find
them with a `DetectorItem`, probe with bolts (`Bolts`), wear them in Curios -> `Artifacts` applies immunities and
restores, calling WFMedical / WFWeight hooks when those mods are loaded.

### Push state (2026-10-29)

- Pushed: WFLib `646015c`, WFMedical `a1460d0`, GemRender `a5af843`, MineAssault `master` `fd3e369`, wfworldgen
  `42ba399`. mc-harness `scenarios/wf_zone.mjs` local only (no remote).
- **Push order for cross-repo API changes: GemRender, WFWeight -> WFLib -> WFMedical, MineAssault.** Remote CI
  resolves the published snapshots; dependents fail to compile until the upstream nightly is published.
- Not in git on purpose: `art/detectors/handoff/` (an 8 MB copy of the user's detector zip).

## Build, install, run

- WFLib: JDK 21, `./gradlew build publishToMavenLocal` (publishes `com.wf.wflib:wflib:<mod_version>-SNAPSHOT`).
  Jar: `build/libs/wflib-<mod_version>.jar`.
- WFMedical: JDK 21, `./gradlew build`. It asks for `wflib ${wflib_version}-SNAPSHOT` with mavenLocal first: **always
  republish WFLib first**, else it silently compiles against a stale snapshot ("cannot find symbol clotter").
- WFWeight: JDK 25, `./gradlew :1.21.1:neoforge:publishToMavenLocal`. Its shared JUnit tests are not wired.
- MineAssault: `./gradlew build -x test` (mavenLocal-first init script in `~/.local/share/gradle/init.d/` puts local
  WFLib / GemRender first). Never `pkill -f <dir>` from a shell whose command line holds it.
- GemRender: `./gradlew :1.21.1:publishToMavenLocal`.
- Prism instance: `~/.local/share/PrismLauncher/instances/wf2-internal-client-0.1.0-internal.20261017-2139/minecraft/mods`.
  Move replaced jars to `mods-backup-YYYYMMDD/`, never delete. Before swapping WFLib, **link-check dependents**
  (MineAssault, CombinedAssault, wfcore, WFMedical...) against the new jar: list members that disappeared
  (`javap -p -s` old vs new) and grep the dependents' constant pools (`javap -v`, Methodref/Fieldref) for them. A
  WFLib-only swap once crashed MineAssault with NoSuchMethodError. Check `pgrep -a java | grep wf2-internal` first:
  the user may be playing.

**Our scripts live in `devtools/` (tracked)**: `anomaly-bench.sh`, `park-window.py`, `xray-ogf.py`, `xray-db.py`,
`xray-assets.py`, `gamma/*`, `detectors/bake_hands.py`. Upstream ignores any `tools/` folder (other people's local generators referenced in
older docs, e.g. `tools/rpg_rockets.py`, live there and are not in git).

## WFLib code map

### Anomalies (`com.wf.wflib.anomaly`)

- `AnomalyType` (26): VORTEX (`wflib:gravity_anomaly`), WHIRLIGIG, SPRINGBOARD, ELECTRO, BURNER, ACID, STEAM, FLASH,
  GHOST, NET, BALL_LIGHTNING, LIQUID_GOLD, GREEN_DRAGON, REBOUNDER, MEPHISTOPHELES, BURNT_FUZZ, GRABBER, COMET,
  RADIATION_FIELD, THERMAL_FIELD, ACID_FIELD, NO_GRAVITY, PSY_FIELD, RADAR, DARKNESS, STEAM_VENT. Each = behaviour (`MincerTuning` | `BlastTuning` |
  `FieldTuning`) + `AnomalyLook` + damage type. One block per type (`ModBlocks`), one shared block entity.
- Ported from GAMMA zone sections + xray-monolith code. Look: GemRender lens refraction (`Lenses.submit`) for
  gravitational ones, X-Ray particles (`client/xray`, `devtools/xray-assets.py`) for the rest.
- Client: effects only where the chunk section is compiled and in render distance; idle loops start within 24
  blocks, stop past 32; no anomaly sound may be `"stream": true` (only 8 streaming channels exist).
- Commands: `/wflib anomalies [on|off]`, `/wflib anomalies site <tier> [motif|any [ruin|any|none]]` (builds a site
  near you, searches 128 blocks for ground that takes one), `/wfdebug anomaly status|measure|trace|walkaway`.
- Psy (damage type `wflib:psy`): `Psy.expose` -> `PsyExposureEvent` (STALKER psy units, 1 = all psy health);
  WFMedical handles it (stress x `stressPsyGain` x (1 - `psyProtection`)); unhandled or non-player => hurt
  `units x FULL_HIT`. PSY_FIELD (`FieldTuning.Kind.PSY`), RADAR (`Quirk.PSY_AURA` 8 m + psy blast), DARKNESS (psy
  mincer; DAO poltergeist spawns left out). Darkness/Radar particles + sounds: DAO via `xray-assets.py`.
- Skipped on purpose: Witches' Galantine (= Electro), zone_unknown (= weak Grabber).

### Anomaly sites (`anomaly/site`)

- Structure `wflib:anomaly_site`, one structure set (spacing 12, separation 7), step `top_layer_modification`.
- Tier per candidate: `AnomalySiteStructure.BASE = {0.20, 0.08, 0.03, 0.012}` x `AnomalySites.density.multiplier(
  tier, x, z)` (default 1; wfcore overrides from worldgen rules).
- `SitePlan` (motif, overlay, radius 16-48) -> `SiteLayout` (pure, deterministic; `GeneratorTerrain` = noise heights
  on a 4-block grid) -> `SitePainter` (per chunk box; applies the layout's height delta to real ground; keeps native
  surface and trees unless bare or scorched; places anomalies and `ZoneArtifact`s).
- Motifs (`SitePlan.Motif`, element, min tier): CRATER, CRATER_FIELD, RAVINE, FISSURES, GLASS_FIELD, BOG (1);
  BOULDER_FIELD, SINKHOLE, DEAD_FOREST (2); UPHEAVAL, ARCHES (3); PIT (4).
- Ruins (`SitePlan.Overlay`, min tier): VILLAGE_RUIN, CAMP, WATCHTOWER (1); TRAIN_WRECK, RADIO_MAST (2); TEMPLE,
  BUNKER (3). Village ruins use vanilla village house templates with BlockRot/BlockAge processors.
- `SiteStyle`: biome restyle (badlands terracotta strata, desert sand...) and motif lean per biome.
- FISSURES vents = STEAM_VENT anomalies (Steam behaviour; idle `xray/particles/wflib/vent_steam`: ours, GAMMA's
  zone_steam_idle smoke blended at alpha 0.35, slower fade) over magma ringed with basalt (`SiteMotifs.vent`;
  `Kind.STEAM_VENT` cap 0: never rolled). GAMMA's own steam idle is near-invisible additive wisps.
  `anomalies.enabled=false` => no smoke (user, 2026-10-29). Camp ruin campfire (`SiteRuins`) unlit on purpose.
- `AnomalyMix`: which anomaly types per element/spot (FLOOR, CEILING, FIELD) and the loot table (`Loot`, built from
  `Artifact.ALL`; `pickLoot(r, tier, kinds)` filters by each artefact's `minTier` and element; `Artifact.UNIQUE` weight
  1/8: ~1.3% of tier-4 draws).

### Artefacts (`com.wf.wflib.artifact`)

- `Artifact` record: id, glb model (`models/artifact/<id>.glb`), texture slots, arcs, core sprite, per-hit
  `immunity` (`Hit`: SHOCK, BURN, STRIKE, WOUND, BULLET, EXPLOSION, CHEMICAL, PSY), health / power (stamina) /
  bleeding restore, `radShield` (radiation_immunity), `radiation` (radiation_restore_speed), bloom, light, detect
  rank, element, min tier. Values are GAMMA's resolved numbers.
- `Artifact.ALL` (29): BATTERY, SOUL; 2026-10-28: CRYSTAL, ITCHER, SPONGE, FIREBALL, SPARKLER, GLASS_BEADS, MOONLIGHT,
  FLASH (item `wflib:flash_artifact`: `wflib:flash` = Flash anomaly block item), ICE, RING, DUMMY, STONE_FLOWER;
  2026-10-29: STONE_BLOOD, LOBSTER_EYES, JELLYFISH (af_medusa), MEAT_CHUNK, PIN, BUBBLE (af_baloon), BLACK_SPRAY,
  BRACELET, EYE, FLAME (af_fire), KOLOBOK, FIREFLY (af_glass), WRENCHED (af_vyvert); `UNIQUE`: PELLICLE,
  OASIS_HEART (tier 4). Element / tier = GAMMA `af_class_<elemental|psio_gravi|phyto_chem>_<south|mid|north>` (lowest;
  elemental split by `af_<fire|elec>_t*`); rank = DAO Svarog `items_devices.ltx` af_class lists. Items registered from `ALL` in `ModItems.ARTIFACTS`; masses in `ItemMasses`; Curios
  tag `data/curios/tags/item/artifact.json`; lang `item.wflib.<id>`.
- `Artifacts` (server): `LivingIncomingDamageEvent` at HIGH (before WFMedical turns hits into wounds) scales by
  `1 - immunity`, stacked per worn artefact; `hit(DamageSource)` maps damage types to `Hit`. Once a second: health
  restore -> `Artifacts.healer`, bleeding restore -> `Artifacts.clotter` (negative too), radiation restore ->
  `LivingHazardProps.incrementRadiation`. `psyProtection(player)` for stress. `shieldDose`: radShield as a RAD/s budget
  per tick across every dose (`ContaminationUtil.contaminate`, after armour; negative adds once a tick).
  `RAD_PER_UNIT` 1000; X-Ray subtracts radiation_immunity per hit, not per second.
  Stamina: `compat/wfweight/ArtifactStamina` (WFWeight `StaminaModifiers`).
- Hooks claimed by WFMedical (`compat/wflib/ArmorBridge`, `StressBridge`): `healer` -> `MedicalHealing.heal`
  (negative = `drain`: a self-healing blunt_force_trauma on the torso), `clotter` -> `MedicalHealing.clot` /
  `thin` (negative: transient bleeding x (1 + thinning)), `PsyExposureEvent` + stress gain x `(1 - psyProtection)`.
- Rendering (`artifact/client/ArtifactRenderer`, GemRender direct path). **Slot naming is the contract**:
  - `body*`: lit. `glow` / `glow2`: unlit, blended, bloom. `glowsolid` / `glowsolid2`: unlit, opaque, **no bloom**
    (a bloom pass on an opaque part draws its blurred copy over it at equal depth; that bug made Crystal/Fireball
    smears until 07356ba). `Artifact.glowSlot` / `bloomSlot` encode this.
  - Blended parts write no depth: anything drawn later paints over them. Use MASK (cut-out) or OPAQUE for shells
    whenever that does not hide something meant to show through (Flash's shell stays blended, its blue comes from
    the rays behind it).
  - First person: arms via WFLib `PlayerArms`/`ArmViewPose`, artefact floats over the palm (`HeldArtifactPose`,
    `SCALE` 0.4 in view space). Inventory/ground/item frames/others' hands: GemRender item renderer at true size.
    Held artefacts add a GemRender point light.
- Missing GAMMA effects, not implemented: additional_inventory_weight (WFWeight carry capacity),
  satiety_restore_speed.

### Detection, collection, bolts (`artifact/detect`)

- `ZoneArtifact`: hidden entity holding the artefact stack, placed by `SitePainter`; shown to a client only inside its
  detector's reveal radius; taken by interacting. `detectRank` = lowest detector rank that finds it.
- `DetectorTier`: ECHO (rank 1, range 30, reveal 3, LED), BEAR (2, 30, 4, DIRECTION), VELES (3, 30, 5, SCREEN). Beep
  interval lerps 2 s -> 0.05 s with distance. `DetectorItem`, `Detection` (server search), client `DetectorClient`
  (models, sounds, faces), `DetectorHud`, `DetectorProbe`, `ZoneArtifactRenderer`.
- Bolts: `Bolts.THROWABLE` = `#wflib:throwable_bolts` (`wflib:bolt`, `#c:bolts`: GregTech's every material); right
  click throws one (`RightClickItem` for foreign items, `BoltItem.use` for ours), stack shrinks unless creative,
  12-tick cooldown per player. `BoltEntity` (plain `Entity`, carries the stack): gravity 0.04, drag 0.99, water sink,
  per-axis bounce (restitution 0.3, Coulomb friction -> spin), tumble on knocks > 0.06, rolling on ground, rest ->
  flat, wakes when moved; glances off bodies; no damage; anomalies take it as a body (`AnomalyBlockEntity.affected`).
  Picked up by walking over (landed) or use: same stack back. Despawn 6000 ticks. Drawn as its item's sprite
  (`BoltRenderer`, `FIXED`), synced quaternion slerped. Impact sounds by surface (Oleh's). `wflib:bolt`: 1 iron nugget
  -> 2, sprite from GAMMA's icon atlas.
- Detectors draw `models/detector/lowpoly/<id>.glb` (old static glbs still in place, unused; delete once approved).
  **Echo is a placeholder for the user's own model**: same bone + clip names, or re-bake.
- First person (MineAssault `ZoneHandsViewmodel` on WFLib `DetectorAnim`): DAR states (`models/detector/lowpoly/
  states.json`) SHOW / HIDE / IDLE / MOVING / CROUCH_MOVING / SPRINT / ZOOM_IN / ZOOM / ZOOM_OUT / THROW_START /
  THROW / THROW_END, 0.15 s crossfade; hide holds the next detector back. Zoom = use key with the detector out and no
  bolt in the main hand. Left arm on the baked fist track; device on the baked `detector` track with its own clip's
  `wpn_body` motion cancelled (`B0 * B(t)^-1`); faces (`DetectorAnim.Face`: Veles `cover_power`, Bear `screen_bone`)
  follow the posed bone. Zoomed: + `aim_hud_offset_pos` (approximation: X-Ray's `UpdateHudAdditional` not ported).
  Right hand: MA's bolt throw; the held bolt drawn as its stack's sprite.
- Hand clips: `assets/wflib/detector/hands/<tier>/<anm_state>.json` (`HandClip`, prop `detector`), baked by
  `devtools/detectors/bake_hands.py` (MineAssault `stalker_hud_dump.py` + local `tools/handclip/handclip.py`; DAR
  hands + device OGF/OMF). `hands_orientation` = X-Ray `setHPB(x, y, z)`: x HEADING, y PITCH, z bank. Device clip
  aligned (cut / held) to the hand clip's frames; DAR's state speed x the motion's own.
- Rigged low-poly sources: `art/detectors/` (`README.md`, `detectors.blend`, `build.py` / `validate.py`,
  `source-study.json`). Bones: Echo `lid` (`light_bone_3` rides it), `button`, `on_switch`; Bear `cover`,
  `tuner_bone`, `on_switch`, `screen_bone*`, `light_bone_1/2`; Veles `cover` -> `cover_power` (display), `joint1`,
  `switch`, `light_bone_1..9`.
- Gap: third person / inventory / ground detectors in bind pose (no clip).

## Screen and camera effects (GAMMA's, ported 2026-10-29)

- Runtime (WFLib `client/xray`): `XrEnvelope` (interp.cpp), `XrPostEffect` (`.ppe`), `XrCameraMotion` (`.anm`,
  chunk `0x1100`, v4/v5), `XrScreen` (effectors by id: `post(id, file, loop, factor)`, `factor`, `stop`, `camera`;
  summed as `CCameraManager::UpdatePPEffectors`; shader `wf_xray_pp` = r2 `postprocess.ps`; drawn after the level +
  hands, before the GUI: `MixinGameRendererXrScreen`, also under F1). Camera: yaw / pitch / roll only, translation
  dropped. Client config `screenEffects.strength` / `cameraShake`.
- Files: `assets/wflib/xray/anims/*.ppe`, `camera_effects/*.anm` (GAMMA winners via `gamma_find.py`).
- Triggers: WFLib `AnomalyScreen` (zone `postprocess` per type, `CZoneEffector` ramp, nearest per effect;
  `anomalies.enabled` gates), `RadiationScreen` (dose > 0.3 / 0.45 units: alcohol / radiation); WFMedical
  `compat/wflib/ScreenEffectsClient` (hits, bloody, low health, deimos, psy = stress, finished treatment).
- Base Anomaly's `psi.ppe` and `electra_mine.ppe` are empty: psy fields / electro mines colour nothing (as GAMMA).
- Not ported: monster attack effectors (no monsters), blowout / psi-storm, sleep / thirst / alcohol items, helmet
  mask + visor + gas mask fog, NVG (WFLib `nvg/` own), weapon switch / headlamp / inertia camera, colour map
  (`cm_influence`, `cm_tex1`), GAMMA death sequence; WFMedical: `pripyat_horror.anm` at low stamina.

### What X-Ray has, and where the files are

- **Post-process effectors (`.ppe`)**: keyframed envelopes for base colour, add colour, grey colour, grey amount,
  blur, **duality** (double vision, horizontal/vertical), noise (intensity, grain, fps) and a colour-map texture.
  Played once or looped, under an id, scaled at runtime by a factor (`level.add_pp_effector(file, id, loop)`,
  `set_pp_effector_factor`). Monsters define the same channels **inline** in their `*_attack_effector` config
  sections (`duality_h/v`, `blur`, `gray`, `noise_*`, `color_base/gray/add`, `time`, `time_attack` fade in,
  `time_release` fade out), plus camera-shake keys.
- **Camera effectors (`.anm`, `anims/camera_effects/`)**: object motion (position and rotation envelopes) applied to
  the camera: hit kicks, earthquakes, drunk sway, fatigue, item use, waking up.
- Base files: `anomaly/db/meshes/anims.db0` -> `devtools/xray-db.py <out> 'anims/*' ~/Games/GAMMA/anomaly/db/meshes/anims.db0`
  (128 `.ppe`, ~50 non-weapon camera `.anm`). Mod overrides: `gamma/mods/*/gamedata/anims/`. Triggers: base scripts
  (`devtools/xray-db.py <out> 'scripts/*' .../configs/scripts.db0`), mod scripts, and configs (`misc/postprocess.ltx`,
  zone and creature LTX). Formats: xray-monolith `src/xrEngine` (`PostProcessAnimator.cpp`, `Envelope.cpp`, object
  motion for `.anm`); `.anm` also imports in Blender's `io_scene_xray` (MineAssault's smoking bake already uses that).
- Third-party data like the rest: internal only, credit sources.

### Inventory by what triggers it

**Anomalies.** A zone's `postprocess = postprocess_<x>` names a section in `misc/postprocess.ltx` (`pp_eff_name`,
`radius_min` / `radius_max` as fractions of the zone radius): the effect ramps up as the player closes in and peaks
inside. Our anomaly types per effect:

| `.ppe` | GAMMA zones | WFLib `AnomalyType` |
|---|---|---|
| `gravi_mine` | mine_vortex, gravitational_weak/average/strong, springboard, blast, net, sphere | VORTEX, WHIRLIGIG, SPRINGBOARD, NET, REBOUNDER |
| `electra_mine` | mine_electra/electric/static/point/flash/darkness, fireball_electric | ELECTRO, FLASH, BALL_LIGHTNING |
| `thermal_mine` | mine_thermal, mine_zharka, mine_radar, fireball_zone | BURNER, COMET |
| `steam_mine` | mine_steam, mine_vapour | STEAM |
| `acidic_mine` | mine_acid/acidic, mine_ghost, mine_green_dragon, fireball_acidic | ACID, GHOST, GREEN_DRAGON |
| `mine_orange` (Dynamic Anomalies Overhaul) | mine_gold, mine_mefistotel | LIQUID_GOLD, MEPHISTOPHELES |
| `fuzz` | burning_fuzz, hvatalka, liana, student, unknown | BURNT_FUZZ, GRABBER |
| `radiation` | field_radioactive, no_gravity | RADIATION_FIELD, NO_GRAVITY |
| `thermal` / `acidic` | field_thermal / field_acidic | THERMAL_FIELD / ACID_FIELD |
| `psi` | field_psychic | (psy field, not built) |
| `flame`, `electra`, `gravi`, `mosquito_bald` | zharka_static/buzz and campfires; teleport, witches_galantine, emi; gravi_zone; mosquito_bald | (no counterpart yet) |

Also: psi-storm and blowout field hits (`bind_anomaly_field.script`) play `psi_fade.ppe` (psy hit) and
`electro_fade.ppe` (shock hit) scaled by the hit's power. Rule for the port: same ramp, client side, off when
`anomalies.enabled=false`.

**Damage and low health** (`actor_effects.script`, `plugins/actor_effects.ltx` `[settings_health]`):
- Hit size tiers: over 17 `amk_shoot.ppe`; over 44 also a random camera kick (`shell_shock`, `hit_front/back/left/
  right` and diagonals) and pain sound; over 68 `total_recall.ppe` plus `head_shot`/`fusker` and a chance to drop
  the weapon.
- Losing health: `bloody.ppe` plus blood splatter statics on the HUD.
- Health under 0.21 or radiation over 0.3: `alcohol.ppe` (dizzy sway) looped; radiation over 0.45: `radiation.ppe`.
- Bleeding over 0.5 with health under 0.2: `deimos1.ppe` pulses every 4-10 s; bleeding over 0.5 with stamina under
  0.25: `pripyat_horror.anm` and `snd_shock.ppe` looped.
- GAMMA mods: Wepl Hit Effects (`camera_effects/wepl/hit_effect/*.anm` per hit, plus `blur2.ppe`); Ballistics
  Overhaul `grok_bo_eft_hit_effects` (`blur2.ppe` on bullet hits); Body Health System / Medications Balance
  `zzz_player_injuries` (head trauma: `concussion_bhs.ppe` -> `concussion_post_bhs.ppe` + `wake_up.anm`;
  `blur_bhs.ppe`; shaking aim when arms are hurt; limp); Radiation Effects Overhaul
  `grok_progressive_rad_damages` (`blur2.ppe` as radiation builds).
- Death (decoded with a `.ppe` dump; xray-monolith `ActorCondition.cpp` / `Actor.cpp`):
  - Engine dying sequence (`CActorDeathEffector`): under 5% health it hides weapon and HUD, locks input, plays a
    dying sound, runs `actor_death.ppe` once with `drunk.anm` swaying, holds health until the effect ends, then kills.
    **Anomaly ships it switched off** (the section is `_actor_death_effector`, the engine looks for
    `actor_death_effector`). `actor_death.ppe`, 3 s: faint red tint 0.5-1.5 s, film grain building (0.33 to 0.8,
    coarser and faster), then darkening (-0.4).
  - Plain death: a death sound, camera to first-eye then a free-look orbit round the body, then the game-over menu.
    No post-process.
  - `rcom_actor_death_quick.ppe`, 3 s (Seraphim's cheat-death, with `surge_02.anm`): the red tint, washing to cold
    grey (0.75), double vision growing hard (duality to 0.66 / 0.88), fade to black.
  - `death.ppe`, 6 s loop: desaturated green-grey, grey pulsing 0.5-0.85, heavy grain, slight blur; nothing in base
    Anomaly or GAMMA's scripts plays it.
  - `black_infinite.ppe`: solid black (psy death, blackout scenes).
  - For us: the dying sequence fits WFMedical's downed state (it already has `UnconsciousOverlay` /
    `GiveUpOverlay`); the plain-death orbit and menu are vanilla Minecraft's to replace or keep; the user decides.

**Psy** (ours is WFMedical stress):
- `arszi_psy.script` psy health: 0.9-1 `blur.ppe`; below that `blur.ppe` + `snd_shock.ppe` scaled by intensity; 0
  `black_infinite.ppe` (psy death) or zombification (`radiation.ppe` + `alcohol.ppe`).
- Actor Damage Balancer / Psy Fields in the North: `adb_psy_feedback.ppe` on psy damage.
- Places and events: `psy_antenna.ppe` / `psy_antenna_indoor.ppe` (Radar, Brain Scorcher), `yantar_underground_psi.ppe`,
  `psychic.ppe` (blowout), `old_psi.ppe`.
- Monsters: controller (`controller_hit.ppe`, `controller_control_effector`, `extra_controller`), burer
  (`m_burer_scan_effector`, `poltergeist_scan.ppe`), psy dog (aura, phantom appear), bloodsucker (vampire effector),
  and every monster's `*_attack_effector`.

**Healing, drugs, needs, misc:**
- Medkit (`medic_effects.script`): `item_use_fast.anm` + `surge_fade.ppe`, then `inv_medkit.ppe`. Antirad: `item_use.anm`
  + `surge_fade.ppe`, then `blur.ppe`.
- Alcohol `alcohol.ppe` / `drunk.anm`; sleep `sleep.anm`, `sleep_fade.ppe`; exhaustion and thirst
  `yantar_underground_psi.ppe` (`actor_status_sleep/thirst`); `fatigue.anm`. (`eat_drug.ppe` exists but nothing in the
  base game triggers it.)
- Blowout (`surge_manager.script`): `earthquake_20/40/60/80.anm` building up, `surge_01/02.anm`, `surge_fade.ppe`,
  `psychic.ppe`; `vibros_p.ppe`.
- `teleport.ppe`, `blink.ppe`, `fade_*` transitions, `snd_shock.ppe` (stun).
- Helmets and masks: the mask shader (`r2_mask_control`: crack distortion by helmet condition, vignette, reflection;
  `[settings_helm_*]`), visor `helm_*.ppe` (RemadeAnimations), gas mask breathing and fog, gas mask wipe `.anm`.
- NVG `nightvision*.ppe`, `bas_nightvision_*` (compare with WFLib's `nvg/`, see `nvg.md`).
- Camera feel: weapon switch kicks (`switch/low|med|strong1-4.anm`), headlamp toggle, Camera Reanimation INERTIA
  movement anims (`*_crp.anm`). Lowest priority.

### Already in Warfactory (don't duplicate; decide per effect: replace, or layer on top)

- WFMedical client: `PainVignetteOverlay`, `StressVignetteOverlay`, `DamageOutlineOverlay`, `UnconsciousOverlay`,
  `GiveUpOverlay`; post shaders `opioid_haze`, `passout_blur`, `blood_desaturate`.
- WFLib: `anomaly/client/TimeSlipOverlay` (the Flash's white-out and blackout), the anomaly lens (GemRender), NVG.

## Tests and benches

- Unit: `anomaly/` AnomalyPathTest, AnomalyTypesTest (incl. psy field), BlastTuningTest, MincerTuningTest,
  SitePlanTest; `anomaly/site/AnomalyMixTest` (loot tier filter, element lean, uniques rare); `artifact/ArtifactsTest`
  (immunity / psy stacking, rad shield budget, restore rates); `artifact/detect/DetectorTierTest` (beep curve, ranks);
  `artifact/client/HeldArtifactPoseTest`. WFMedical `PhysiologyTest` thinning.
- GameTests (own batches: new `defaultBatch` rows reshuffle packing-sensitive lanes): `anomaly/gametest/
  RebounderGameTest`, `SiteLayoutGameTest` (+ FISSURES vents = STEAM_VENT, batch `anomaly_sites`); `artifact/gametest/
  ArtifactGameTest` (damage type -> Hit; reveal/take by rank and radius; batch `artifacts`), `BoltGameTest` (rest on
  flat / stairs / after a wall, sets off a Springboard, walk-over returns the same stack, throw takes one; batch
  `bolts`); `ItemMassGameTest`. WFMedical `WfLibArtifactGameTest` (drain, clot, thin), `WfLibStressGameTest` (psy ->
  stress). Known flakes in WFLib's suite: `apilotfliesthedronefromtheiranchor`,
  `animpactonunloadedgroundwaitsforitschunk`, `alockedsimcomesbackandstays`.
- mc-harness `scenarios/wf_zone.mjs` (MineAssault client): detectors through DAR states per tier, bolts mid-air /
  lying / walked over, the 2026-10-29 artefacts day + night, a FISSURES site, psy anomalies. Screenshots by eye.
- `devtools/anomaly-bench.sh`: dev client into `run/saves/anomaly-bench`, window parked on Hyprland **workspace 15**
  (never on the user's visible workspaces), writes `run/wflib-anomaly-test.txt`, screenshots in `run/screenshots`.
  Env: `BENCH_TEST`, `BENCH_BLOCK=wflib:<anomaly>`, `BENCH_ITEM=wflib:<artefact>`, `BENCH_TIME=day|night`,
  `BENCH_ARGS` (Gradle), `BENCH_JVM`, `GEMRENDER_JAR`.
  - `walkaway`, `trigger` (pig), `bolt`, `look`: anomaly behaviour and visuals.
  - `hold`: an artefact held (light position, lift/put-away), dropped, in the inventory, third person, then worn:
    shock immunity or health restore measured. **Needs `BENCH_ARGS=-PwithCurios`** (Curios is only on the dev
    runtime with that flag; without it `/curios` is unknown and every worn check fails).
  - `gallery`: every artefact on a large item display from three sides, the bench anomaly removed first (its lens
    and debris would spoil the shots). Run with `BENCH_TIME=day` and `night`; check glow, faces, layering by eye.
  - `clusters` (`-Dwflib.anomalyTest.motifs=`, `.ruin=`, `.tier=`): builds sites and screenshots them.
  - `detect`, `hands`: detector/bolt flow; `hands` is run from the MineAssault worktree's dev client
    (`-PquickPlay=anomaly-bench`, `JAVA_TOOL_OPTIONS=-Dwflib.anomalyTest=hands`).
- Gaps: worn-artefact effects end to end (Curios only with `-PwithCurios`: `Artifacts.shieldDose` / restores tested as
  pure math, not through a worn slot); detector state machine (client-only; harness screenshots); wfcore
  `AnomalySiteDensity` (needs a server with rule files). wfworldgen: `internal/world/anomalies_test.go` (3, pass).

## Asset pipeline (GAMMA -> WFLib)

GAMMA is installed at `~/Games/GAMMA` (`gamma/mods/*` in MO2 order from `gamma/profiles/G.A.M.M.A/modlist.txt`,
base archives in `anomaly/db/`). All converted art is third-party: **internal only**, provenance in the
`CREDITS.md` beside the models; clear or replace before anything ships.

1. Extract base files the mods don't ship loose: `devtools/xray-db.py <out> '<glob>' <archive>...`
   (e.g. `'meshes/dynamics/artefacts/*'` from `anomaly/db/meshes/*.db*`, `'textures/artifact/*'`,
   `'configs/*'` from `anomaly/db/configs.db0`).
2. `devtools/gamma/gamma_find.py <gamma> <base> <rel>`: the file that wins in GAMMA (mods top-first, then base).
3. `devtools/gamma/gamma_artefacts.py <base>/configs <gamma> [out.json]`: every artefact section resolved (base LTX +
   mods + DLTX `mod_system_*` patches) with fx, light, weight, tier.
4. `devtools/xray-ogf.py <ogf>` lists children with texture and **shader**; the shader decides the slot kind:
   `models\artefact`, `artefact2`, `xanomaly` -> `body` (texture alpha is gloss: forced opaque); `selflightl` ->
   `glowsolid` (opaque, self-lit); `lightplanes` -> `glow` (additive light cards; drawn alpha-blended);
   `xdistortcolor(l)` -> blended veil; `xdistort` -> refraction only, not drawn.
5. `GAMMA=<mo2 dir> devtools/gamma/artefact_batch.py <base> src/main/resources/assets/wflib [id...]`: converts
   `BATCH` (both batches; ids limit it): textures (max 512 px, `_bloom.png` blurred copies for blended glows), glbs (`--centre`).
   Afterwards by hand: Ring scaled x4 and stood upright; Sparkler/Dummy shells MASK 0.3, Fireball shell OPAQUE.
   Delete `_bloom.png` files no blended glow uses.
6. New artefact: add the `Artifact` constant (GAMMA numbers, light = trail_light_color), add to `ALL` and
   `AnomalyMix.Loot`, item model json (copy `models/item/battery.json`), lang, Curios tag, `ItemMasses` entry, then
   run `hold` (with Curios) and `gallery` day and night. Check the id doesn't collide with an existing item (every
   anomaly block has an item of its own id).

## Remaining work (rough priority)

1. Rebuild the Prism instance's WFLib / WFMedical / MineAssault / GemRender / WFWeight from pushed code (link-check
   dependents first, see Build).
2. **Screen effects not yet ported** (Screen and camera effects, "Not ported"); camera translation; review the
   ported ones in play (strengths are GAMMA's; WFMedical's own overlays still layer on top).
3. **Detectors**: review DAR hands in game (`wf_zone.mjs`); zoom placement is approximate (`aim_hud_offset_pos`
   only); third-person / inventory pose is bind pose; then delete the old static `models/detector/<id>.glb`.
4. **Remaining GAMMA artefacts** (user, 2026-10-29: perks later):
   - **Perk artefacts (25)**: GAMMA's Perk-Based Artefacts (`perk_based_artefacts.script`) gives each a scripted perk
     (e.g. af_serofim cheats death once). Each needs its own effect: af_ball, af_bat, af_black_angel, af_cell,
     af_chelust, af_cocoon, af_dragon_eye, af_elektron, af_fonar, af_fountain, af_generator, af_kogot, af_lighthouse,
     af_medallion, af_moh, af_peas, af_repei, af_sandstone, af_serofim, af_skull_miser, af_spaika, af_star_phantom,
     af_sun, af_tapeworm, af_zhelch.
   - Effects still missing for other sections: additional_inventory_weight (x8), satiety_restore_speed (x4).
   - **Junk, out (28)**: af_atom, af_black, af_ear, af_fire_loop, af_grapes, af_kislushka, af_signet; 11 `af_ah_*`;
     af_geliy, af_gimlet, af_vaselisk; af_lucifer; af_base, af_base_1; Amebas x3; af_tlf_prometheus.
5. **User's Echo detector model** (when they provide it).
6. Nice to have: bloom that spills past the silhouette (only the Soul has a halo, via its core sprite; GAMMA's comes
   from screen-wide bloom); third-person artefacts sit at the fist rather than floating over it.
7. Licences for the third-party art before any public release.

## Gotchas

- Shell is fish/zsh-like: unquoted `$VAR` holding several paths does not split. Wrap multi-path loops in `bash -c`.
- The bench anomaly sits 6 blocks ahead of the player; any visual check in front of it is seen through its lens.
- WFMedical resolves a **stale** local WFLib snapshot without complaint; republish WFLib first.
- `wflib:flash` is taken (anomaly block item): artefact ids must not collide with anomaly ids.
- `Map.ofEntries` in `ItemMasses` throws on duplicate keys at class load: check for duplicates when adding masses.
- An old worldgen pack naming placed features `wflib:anomaly_cluster_tier_N` makes wfcore throw: regenerate it.
- Dev runs: a brand-new run dir shows FML's early window unparked until `config/fml.toml` has
  `earlyWindowControl = false` (the bench script sets it).
- MC dev windows get exactly GL 3.2 on NVIDIA; GL 3.3-only calls abort the JVM.
- GemRender LEVEL pass: opaque at `AFTER_ENTITIES` / `AFTER_BLOCK_ENTITIES`, blended held to `AFTER_WEATHER`
  (`DirectRenderer.flushOpaque`; held instances' bone data copied, bases rebased): clouds stay behind blended parts.
- MineAssault dev runs load `libs/wfweight-*.jar`: WFLib's artefact stamina needs WFWeight's `StaminaModifiers`
  (refreshed 2026-10-29); a stale jar => `ClassNotFoundException IStaminaModifier` at load.
- `Entity.equals` / `hashCode` = entity id; singleplayer client and server players share it: per-player static maps
  touched on both sides need one map per side (`Bolts`).
- `tools/handclip/handclip.py` (local) reads `hands_orientation[1]` as heading; X-Ray's y is PITCH
  (`setHPB(x, y, z)`). WFMedical's bandage bake (0, 20, 0) is affected.
