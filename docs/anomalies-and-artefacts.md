# Anomalies, anomaly sites, artefacts, detectors (STALKER Zone project)

Handoff doc: what the project is, what exists, where it lives, how to build and test it, and what is left. State
as of 2026-10-28. Read this top to bottom before touching anything; the "Gotchas" section has cost real hours.

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

| repo | path | branch -> remote | role |
|---|---|---|---|
| WFLib (mod id `wflib`) | `~/Codebases/WFLib-main` (worktree of `~/Codebases/WF-Ballistics`) | `main` -> `origin/main` (Warfactory-Official/WF-Ballistics) | anomalies, sites, artefacts, detectors, bolts, bench |
| WFMedical | `~/Codebases/WFMedical-neoforge` | `wf/deploy-neoforge` -> `origin/neoforge-1.21.1` | stress (= psy), healing/drain/clotting hooks |
| WFWeight | `~/Codebases/Ultimate-Weight` | `master` | `api/StaminaModifiers` (artefact power_restore) |
| wfcore | `~/Codebases/WFModernCore-1.21.1` | `anomaly-site-density` (pushed) | feeds site density from worldgen resource rules |
| wfworldgen (Go) | `~/Codebases/wfworldgen` | `master` -> `origin/master` | "Anomalies" section: pick fields / autogen cells per tier |
| MineAssault | `~/Codebases/MineAssault/.claude/worktrees/instance-build` | local `instance/ma-0f896c4-wflib-main` (no remote) | `ZoneHandsViewmodel`: detector + bolt in first-person arms |
| GemRender | `~/Codebases/GemRender` | `main` | glTF rendering, lens (refraction) pass, lights; WFLib needs >= 0.1.18 |

Data flow: wfworldgen writes resource rules (system `anomaly`, targets `wflib:anomaly_site_tier_1..4`) -> wfcore
`integration/wflib/AnomalySiteDensity` sets `AnomalySites.density` -> WFLib's `anomaly_site` structure picks a tier
per candidate chunk -> `SitePainter` builds terrain, ruins, anomalies and hidden `ZoneArtifact`s -> players find
them with a `DetectorItem`, probe with `BoltItem`, wear them in Curios -> `Artifacts` applies immunities and
restores, calling WFMedical / WFWeight hooks when those mods are loaded.

### Push state (nothing below is pushed; the user confirms pushes)

- WFLib `main`, 5 commits ahead of origin: `fe57d6d` 12 artefacts, `df2d339` Ring + hold bench, `d124a74` gallery
  bench, `07356ba` glow/depth fixes, `f0bf92c` third-person size. Plus uncommitted: this doc, `tools/gamma/`,
  `models/artifact/CREDITS.md`.
- WFMedical `7c83840` (needs the WFLib API above: `Artifacts.clotter`, `Artifacts.psyProtection`).
- wfworldgen `ef84222`, `42ba399`.
- **Push order: WFWeight -> WFLib -> WFMedical.** Remote CI resolves the published snapshots; WFMedical's CI fails to
  compile until WFLib's nightly with the new API is published.
- MineAssault: the instance jar is built from the local worktree branch. MineAssault `master` cannot build (it
  imports `com.wf.gemrender.gltf.GltfSkeleton`, which is on no pushed GemRender branch). **Cherry-pick
  `ZoneHandsViewmodel` (c0385d2 on that branch) to master once GemRender's GltfSkeleton lands.**

## Build, install, run

- WFLib: JDK 21, `./gradlew build publishToMavenLocal` (publishes `com.wf.wflib:wflib:<mod_version>-SNAPSHOT`).
  Jar: `build/libs/wflib-<mod_version>.jar`.
- WFMedical: JDK 21, `./gradlew build`. It asks for `wflib ${wflib_version}-SNAPSHOT` with mavenLocal first: **always
  republish WFLib first**, else it silently compiles against a stale snapshot ("cannot find symbol clotter").
- WFWeight: JDK 25, `./gradlew :1.21.1:neoforge:publishToMavenLocal`. Its shared JUnit tests are not wired.
- MineAssault instance jar: in the worktree, `./gradlew -I ../maven-local-first.gradle -Pahf_version=1.0.10
  -Pgemrender_version=0.1.18 build -x test`. Never `pkill -f instance-build` (matches your own shell).
- Prism instance: `~/.local/share/PrismLauncher/instances/wf2-internal-client-0.1.0-internal.20261017-2139/minecraft/mods`.
  Move replaced jars to `mods-backup-YYYYMMDD/`, never delete. Before swapping WFLib, **link-check dependents**
  (MineAssault, CombinedAssault, wfcore, WFMedical...) against the new jar: list members that disappeared
  (`javap -p -s` old vs new) and grep the dependents' constant pools (`javap -v`, Methodref/Fieldref) for them. A
  WFLib-only swap once crashed MineAssault with NoSuchMethodError. Check `pgrep -a java | grep wf2-internal` first:
  the user may be playing.

**`tools/` is gitignored upstream** ("tied to this dev setup"): `anomaly-bench.sh`, `park-window.py`,
`xray-ogf.py`, `xray-db.py`, `xray-assets.py` and `tools/gamma/*` exist only in the `~/Codebases/WFLib-main` worktree,
not in a fresh clone. Back them up before deleting that worktree.

## WFLib code map

### Anomalies (`com.wf.wflib.anomaly`)

- `AnomalyType` (22): VORTEX (`wflib:gravity_anomaly`), WHIRLIGIG, SPRINGBOARD, ELECTRO, BURNER, ACID, STEAM, FLASH,
  GHOST, NET, BALL_LIGHTNING, LIQUID_GOLD, GREEN_DRAGON, REBOUNDER, MEPHISTOPHELES, BURNT_FUZZ, GRABBER, COMET,
  RADIATION_FIELD, THERMAL_FIELD, ACID_FIELD, NO_GRAVITY. Each = behaviour (`MincerTuning` | `BlastTuning` |
  `FieldTuning`) + `AnomalyLook` + damage type. One block per type (`ModBlocks`), one shared block entity.
- Ported from GAMMA zone sections + xray-monolith code. Look: GemRender lens refraction (`Lenses.submit`) for
  gravitational ones, X-Ray particles (`client/xray`, `tools/xray-assets.py`) for the rest.
- Client: effects only where the chunk section is compiled and in render distance; idle loops start within 24
  blocks, stop past 32; no anomaly sound may be `"stream": true` (only 8 streaming channels exist).
- Commands: `/wflib anomalies [on|off]`, `/wflib anomalies site <tier> [motif|any [ruin|any|none]]` (builds a site
  near you, searches 128 blocks for ground that takes one), `/wfdebug anomaly status|measure|trace|walkaway`.
- **Deferred by the user until psy existed: Radar, Darkness, psy field.** Psy now exists (WFMedical stress), so these
  are buildable: they should raise stress through WFMedical (see `StressBridge`), scaled by
  `Artifacts.psyProtection`. Skipped on purpose: Witches' Galantine (= Electro), zone_unknown (= weak Grabber).

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
- `AnomalyMix`: which anomaly types per element/spot (FLOOR, CEILING, FIELD) and the loot table (`Loot`, built from
  `Artifact.ALL`; `pickLoot(r, tier, kinds)` filters by each artefact's `minTier` and element).

### Artefacts (`com.wf.wflib.artifact`)

- `Artifact` record: id, glb model (`models/artifact/<id>.glb`), texture slots, arcs, core sprite, per-hit
  `immunity` (`Hit`: SHOCK, BURN, STRIKE, WOUND, BULLET, EXPLOSION, CHEMICAL, PSY), health / power (stamina) /
  bleeding restore, bloom, light, detect rank, element, min tier. Values are GAMMA's resolved numbers.
- `Artifact.ALL` (14): BATTERY, SOUL (earlier), and the 2026-10-28 batch: CRYSTAL, ITCHER, SPONGE, FIREBALL, SPARKLER,
  GLASS_BEADS, MOONLIGHT, FLASH (item `wflib:flash_artifact`: `wflib:flash` is the Flash anomaly's block item),
  ICE, RING, DUMMY, STONE_FLOWER. Items registered from `ALL` in `ModItems.ARTIFACTS`; masses in `ItemMasses`; Curios
  tag `data/curios/tags/item/artifact.json`; lang `item.wflib.<id>`.
- `Artifacts` (server): `LivingIncomingDamageEvent` at HIGH (before WFMedical turns hits into wounds) scales by
  `1 - immunity`, stacked per worn artefact; `hit(DamageSource)` maps damage types to `Hit`. Once a second: health
  restore -> `Artifacts.healer`, bleeding restore -> `Artifacts.clotter`. `psyProtection(player)` for stress.
  Stamina: `compat/wfweight/ArtifactStamina` (WFWeight `StaminaModifiers`).
- Hooks claimed by WFMedical (`compat/wflib/ArmorBridge`, `StressBridge`): `healer` -> `MedicalHealing.heal`
  (negative = `drain`: a self-healing blunt_force_trauma on the torso), `clotter` -> `MedicalHealing.clot`, stress
  gain x `(1 - psyProtection)`.
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
- Missing GAMMA effects, not implemented: radiation immunity (ignored; WFLib has a `radiation/` package to wire it
  to), radiation_restore_speed, additional_inventory_weight (WFWeight carry capacity), satiety_restore_speed.

### Detection, collection, bolts (`artifact/detect`)

- `ZoneArtifact`: hidden entity holding the artefact stack, placed by `SitePainter`; shown to a client only inside its
  detector's reveal radius; taken by interacting. `detectRank` = lowest detector rank that finds it.
- `DetectorTier`: ECHO (rank 1, range 30, reveal 3, LED), BEAR (2, 30, 4, DIRECTION), VELES (3, 30, 5, SCREEN). Beep
  interval lerps 2 s -> 0.05 s with distance. `DetectorItem`, `Detection` (server search), client `DetectorClient`
  (models, sounds, faces), `DetectorHud`, `DetectorProbe`, `ZoneArtifactRenderer`.
- `BoltItem` / `BoltEntity`: thrown, trips anomalies (items otherwise never set them off).
- Models: `models/detector/{echo,bear,veles,bolt}.glb` from GAMMA "Devices of Anomaly Redone" + base bolt (see
  `models/artifact/CREDITS.md`). **Echo is a placeholder for the user's own model**: replace `echo.glb` keeping
  the slot names `DetectorClient` binds.
- MineAssault `ZoneHandsViewmodel`: detector in the left hand with its face drawn on the device (Bear LED ring,
  Veles blips), bolt in the right with a throw tied to the item cooldown; uses WFLib `PlayerArms`.

## Tests and benches

- Unit (`src/test/.../anomaly`): AnomalyPathTest, AnomalyTypesTest, BlastTuningTest, MincerTuningTest, SitePlanTest;
  `artifact/client/HeldArtifactPoseTest`. GameTests: `anomaly/gametest/RebounderGameTest`, `SiteLayoutGameTest`
  (layouts need Blocks, which plain unit tests can't load), plus `ItemMassGameTest`.
- `tools/anomaly-bench.sh`: dev client into `run/saves/anomaly-bench`, window parked on Hyprland **workspace 15**
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
- **Gaps (the user asked for prepared tests for everything):** no automated test of `Artifacts.hit()` mapping per
  damage type, `psyProtection` stacking, WFMedical `drain`/`clot` from artefacts, `AnomalyMix.pickLoot` tier
  filtering, `ZoneArtifact` reveal/take, detector beep timing, bolt tripping, wfcore density plumbing, or the
  wfworldgen Anomalies output. Visual checks (gallery) are screenshots judged by eye.

## Asset pipeline (GAMMA -> WFLib)

GAMMA is installed at `~/Games/GAMMA` (`gamma/mods/*` in MO2 order from `gamma/profiles/G.A.M.M.A/modlist.txt`,
base archives in `anomaly/db/`). All converted art is third-party: **internal only**, provenance in the
`CREDITS.md` beside the models; clear or replace before anything ships.

1. Extract base files the mods don't ship loose: `tools/xray-db.py <out> '<glob>' <archive>...`
   (e.g. `'meshes/dynamics/artefacts/*'` from `anomaly/db/meshes/*.db*`, `'textures/artifact/*'`,
   `'configs/*'` from `anomaly/db/configs.db0`).
2. `tools/gamma/gamma_find.py <gamma> <base> <rel>`: the file that wins in GAMMA (mods top-first, then base).
3. `tools/gamma/gamma_artefacts.py <base>/configs <gamma> [out.json]`: every artefact section resolved (base LTX +
   mods + DLTX `mod_system_*` patches) with fx, light, weight, tier.
4. `tools/xray-ogf.py <ogf>` lists children with texture and **shader**; the shader decides the slot kind:
   `models\artefact`, `artefact2`, `xanomaly` -> `body` (texture alpha is gloss: forced opaque); `selflightl` ->
   `glowsolid` (opaque, self-lit); `lightplanes` -> `glow` (additive light cards; drawn alpha-blended);
   `xdistortcolor(l)` -> blended veil; `xdistort` -> refraction only, not drawn.
5. `tools/gamma/artefact_batch.py <base> src/main/resources/assets/wflib`: converts the 2026-10-28 batch (edit
   `BATCH` for new ones): textures (max 512 px, `_bloom.png` blurred copies for blended glows), glbs (`--centre`).
   Afterwards by hand: Ring scaled x4 and stood upright; Sparkler/Dummy shells MASK 0.3, Fireball shell OPAQUE.
   Delete `_bloom.png` files no blended glow uses.
6. New artefact: add the `Artifact` constant (GAMMA numbers, light = trail_light_color), add to `ALL` and
   `AnomalyMix.Loot`, item model json (copy `models/item/battery.json`), lang, Curios tag, `ItemMasses` entry, then
   run `hold` (with Curios) and `gallery` day and night. Check the id doesn't collide with an existing item (every
   anomaly block has an item of its own id).

## Remaining work (rough priority)

1. **Push** (with the user's go): WFLib -> WFMedical; wfworldgen. Then the instance gets jars built from pushed code.
2. **Prepared tests** for the gaps listed under Tests.
3. **Remaining GAMMA artefacts.** 87 sections remain; 68 use only effects that exist, 19 need new ones (radiation
   restore x9, inventory weight x8, satiety x4). The user wants **no junk artefacts**. Junk screen of the 68
   (2026-10-28):
   - **Next batch, 13** (all in GAMMA's random anomaly spawns, `drx_da_main_artefacts.script`): af_blood Stone Blood,
     af_lobster_eyes Lobster Eyes, af_medusa Jellyfish, af_mincer_meat Meat Chunk, af_pin Pin (tier 1); af_baloon
     Bubble, af_black_spray Black Spray, af_bracelet Bracelet, af_eye Eye, af_fire Flame, af_fuzz_kolobok Kolobok,
     af_glass Firefly, af_vyvert Wrenched (tier 2). 9 of them carry `radiation_immunity` at rate-sized values
     (+-0.0005..0.0017), ignored until radiation is wired (item 4): adding them first drops a radiation side effect.
   - **Unique (GAMMA spawns them once)**: af_dummy_pellicle Pellicle, af_oasis_heart Heart of the Oasis. Candidates
     for rare tier-4 (PIT) finds; user's call.
   - **Junk, out (53)**: the 32 of `items_artefacts_junk.ltx` (no effects: af_atom, af_ball, af_bat, af_black,
     af_black_angel, af_cell, af_chelust, af_cocoon, af_dragon_eye, af_ear, af_elektron, af_fire_loop, af_fonar,
     af_fountain, af_generator, af_grapes, af_kislushka, af_kogot, af_lighthouse, af_medallion, af_moh, af_peas,
     af_repei, af_sandstone, af_serofim, af_signet, af_skull_miser, af_spaika, af_star_phantom, af_sun, af_tapeworm,
     af_zhelch); 11 `af_ah_*` "Anomalous Sample" collector items (New Storylines; flat 4000, junk meshes, "lack of
     useful properties"); af_geliy, af_gimlet, af_vaselisk (New Storylines quest objects, no effects); af_lucifer
     (scripted easter egg, 666); af_base, af_base_1 (abstract parent sections); Amebas x3 (no spawn group or
     detector); af_tlf_prometheus (quest reward).
4. **Radiation**: map `radiation_immunity` / `radiation_restore_speed` onto WFLib's `radiation/` system.
5. **Psy anomalies** (Radar, Darkness, psy field) feeding WFMedical stress.
6. **User's Echo detector model** (when they provide it).
7. **MineAssault**: move `ZoneHandsViewmodel` to master once GemRender `GltfSkeleton` is pushed.
8. **GemRender blended draw order** (needs the user's decision; affects every GemRender translucent part): the
   level pass flushes opaque and blended piles together at `AFTER_ENTITIES` (`direct/DirectEvents.onRenderStage`),
   before vanilla clouds, so clouds draw over blended parts (Flash's rays, Moonlight's arcs, the Battery's lightning)
   outside the hand. Fix idea: flush only opaque there, blended at `AFTER_WEATHER`.
9. Nice to have: bloom that spills past the silhouette (only the Soul has a halo, via its core sprite; GAMMA's comes
   from screen-wide bloom); third-person artefacts sit at the fist rather than floating over it.
10. Licences for the third-party art before any public release.

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
