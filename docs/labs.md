# WFLabs

System `labs` (`com.wf.wflib.labs`, R17 switchable, default on, config `wflib-labs.toml`). Real game code over parameter grids -> tables on disk. Nothing reimplemented: flight = `RoundArc`, cover = `Ricochet` + `BlockPen` + drill rule, armour = `ArmorResolution.resolveSlot`, blast = `EntityProcessorCross.calculateDamage`, wounds = `DamageEvents.resolveHit` + `MedicalEngine` steps.

## Run

| where | command |
|---|---|
| in game (op) | `/wflabs run <id\|all> [k=v ...]`, `/wflabs list` |
| dev server, wflib | `./gradlew runServer -Plabs=all [-PlabsArgs="ranges=0,100 rounds=wflib:5.56*"]` (game dir `run-labs`, flat world, EULA written) |
| dev server, WFMedical | same, in `WFMedical` (loads wflib from mavenLocal; adds `wfmedical:*`) |
| any server, any jar set | JVM property `-Dwflabs=<ids\|all>` `[-Dwflabs.args="k=v ..."]`: runs after `ServerStartedEvent`, writes, halts (exit 1 on failure) |
| production server | `JAVA_TOOL_OPTIONS=-Dwflabs=all` or a `user_jvm_args.txt` line; no gametest class referenced |
| shot recorder | `/wflabs record start`, `/wflabs record stop` -> `shots.ndjson.gz` |
| warm server | `-Dwflabs.serve=<port>` (0 = any): after `ServerStartedEvent` the server thread serves runs until `shutdown`; `wflabs.mjs run --serve`, `wflabs.mjs query` |

- Dev (`!production`) + `wflabs` => `WFLibContent.everything` (whole catalogue). Production => only content the pack requests.
- Default grid excludes dev/test content; `dev=1` adds it (`content_origin=dev`).
- Output `<gameDir>/wflabs/<yyyyMMdd-HHmmss>/` (recorder: `...-shots`): `<table>.ndjson.gz` (one JSON array per row, column order) + `manifest.json`.

## Warm server

`-Dwflabs.serve=<port>` => `LabServe`: 127.0.0.1 only, NDJSON over TCP, one JSON per line each way. Ready line `[labs] serve listening 127.0.0.1:<port>`. Dev env: whole catalogue as with `-Dwflabs`. Use a gametest server (`runGameTestServer`); dedicated `runServer` needs `max-tick-time=-1` (server thread blocks in the loop).

| request | frames |
|---|---|
| `{op:"list"}` | `{type:"list", producers:[{id,table,name,domain,columns}], config_files:[..]}` |
| `{op:"run", ids:[..], args:"k=v ..."\|{k:v}, config:{"<file>":{"<dotted.key>":v}}}` | `queued{position}`? then per table `table{id,name,file,columns,domain}`, `rows{table,rows:[[..]..]}`, `progress{table,rows,index,total}`; end `done{dir,manifest}` or `error{message}` |
| `{op:"shutdown"}` | `shutdown`; exits after queued runs |

- Run dir written as in batch: `rows` frames == file lines, same order, byte-identical JSON. `done.manifest` == `manifest.json`.
- FIFO, one run at a time, on the server thread (as `-Dwflabs`). Socket IO: own threads, producers never block on a socket (`offer` to a per-client buffer).
- Per-client buffer `-Dwflabs.serve.queueBytes` (default 64 MiB chars): over => buffer cleared, `error` frame, socket closed after flush (2 s cap). Write failure => client gone. Gone client => run cancelled, run dir deleted; queued runs of a gone client skipped. Read EOF alone (half-close) != gone.
- Run dir name `yyyyMMdd-HHmmss[-n]`, never reuses.
- `config`: loaded `ModConfigSpec` files by file name (`ModConfigs`), any mod. Validate all (file, key, type, range) before any set; fail => `error`, no run, nothing changed. Apply = `ConfigValue.set` on the in-memory config + `ModConfigEvent.Reloading` to the owner (what a reload fires => owner snapshots, e.g. `MedicalConfig.Values`, rebuild). Restore in `finally`: original values (absent keys removed) + Reloading again. Toml never written (sha unchanged). Manifest `overrides` = `{file:{key:value}}` as applied; `content.configs` = on-disk text (no overrides).
- Owners must rebuild cached snapshots in a `ModConfigEvent` handler (Loading and Reloading). Value types: double, int, long, boolean, string, enum; lists unsupported.

## Grid args

| arg | meaning | default |
|---|---|---|
| `ranges=a,b,c` or `a:b:step` | m | `0,10,25,50,100,150,200,300,400,500,750,1000,1500,2000`; cover / armour / wounds / chain: `0,100,300,800` unless set |
| `rounds=glob,glob` | round id glob (id or path) | all |
| `launchers=glob,glob` | launcher id glob | all |
| `seed=` | every dice roll | 20261103 |
| `samples=` | ricochet seeds per cell | config `samples` (128) |
| `wound_samples=` | seeded hits per wound cell | wounds 32, chain 16 |
| `hit_samples=` | dispersion samples per hit_zones cell | 2000 |
| `thickness=` `angles=` `blocks=` | cover grid | `0.25,0.5,1,2` / `0,45,70,85` / named set + one cube per distinct pen triple |
| `covers=block@thickness@angle,...` | chain covers (plus `none`) | dirt 1, planks 0.5, stone 0.5, stone 1, iron 0.5, stone 1 @70 |
| `limbs=` | chain limb rows (`all` row always) | `head,torso` |
| `stances=` | `stand sneak kneel prone`; hit_zones / chain | hit_zones all, chain `stand` |
| `aims=` | `centre` (torso+chest OBB mid) `head` | hit_zones all, chain `centre` |
| `moas=` | dispersion MOA list replacing each launcher's own (hit_zones, chain); `dispersion_moa` column = value used | launcher's |
| `bearings=` | deg from body front, +90 = body local +x side; chain: first value | `0` |
| `armor=` | garment id glob, comma list; never re-admits `armorExclude` matches | all |
| `distances=` | explosive, m | `0,1,2,3,5,8,12,20,30,50,80,120` |
| `scenario=` `scenario_json=` | `wflib:scenario` only (exactly one): JSON file path on the server / inline JSON (CLI args split on spaces: inline needs none, or use the warm-server map form) | required |
| `dev=1` | include dev/test content | off |
| `missiles=` | missile preset globs (id or path); air medium, not interceptors | all |
| `missile_ranges=` `missile_samples=` | missile_flight ranges m / seeded launches per cell (also interception) | `250,500,1000,2000` / 4 |
| `missile_lanes=` `missile_max_ticks=` | concurrent lanes / tick cap per batch | flight 16, interception 4 / 6000 |
| `defenders=` `offsets=` `approach=` `defense_chunks=` | interception: defender globs, target offset from defender m, launch distance m, held radius chunks | all / `0,100` / 1200 / 14 |

## Manifest

`schema` 1, `tables[{id,name,file,columns[{name,unit,role,kind}],rows,millis}]`, `grid`, `mods{id: version}`, `repos[{path,commit,dirty}]` (null when no git), `content`, `overrides{file:{key:value}}` (warm server `config` only).

`content`: `environment{production,dist,whole_catalogue_requested}`, `mod_jars[{id,version,file,sha256}]` (sha null for directories), `packs{owner: {pack: version\|sha}}` (`LabContents` registry), `datapacks[]` (selected pack ids), `configs{file: toml text}` (`wflib-*.toml` + files named by `LabContent.configFiles()`).

## Tables

Units SI; `mm_rha` pen; `deg` angles. `content_origin` = `shipped|dev`. Launcher default `wflib:<round path>` = round's own preset; other launchers via `LabLaunchers.REGISTRY`.

| table | dims | measures |
|---|---|---|
| `round_flight` | launcher, round, calibre, content_origin, range_m | muzzle_ms, dispersion_moa, damage_scale, mass_kg, caliber_mm, pellets, reached, tick, time_s, speed_ms, energy_j, drop_m, armor_pen_mm_rha, block_pen_mm, damage_hp, pierce_dt, pierce_dr, threat_kind, motor, launcher_extra (JSON) |
| `cover` | launcher, round, content_origin, range_m, block, thickness_m, angle_deg | speed_ms, verdict, p_ricochet, p_perforate, p_stop, spent, exit_speed_ms, exit_pen_mm, stop_depth_m, ricochet_speed_ms, impulse_kg_ms, block_toughness, block_damage_frac, samples |
| `cover_blocks` | block, material_class | resistance, toughness, ricochet_deg, equivalent_blocks, examples |
| `personal_armor` | launcher, round, content_origin, range_m, armor, zone | speed_ms, damage_base, headshot_factor, damage_in, protection_type, outcome, through, absorbed, wear_points, wear_frac_worst, worst_condition |
| `explosive` | source, source_kind, content_origin, distance_m, armor | blast_size, radius_m, frag_count, damage_unarmored, protection_type, outcome, through, absorbed, wear_points |
| `wfmedical:wounds` | launcher, round, content_origin, range_m, armor, limb | speed_ms, damage_in, armor_outcome, armor_wear_frac, p_major_trauma, p_fracture, p_internal_bleeding, wound_mode, bleed_ml_tick, pain, effective_max_hp, p_incapacitated, t_incapacitated_s, p_dead, t_dead_s, samples, horizon_s |
| `wfmedical:hit_zones` | launcher, round, content_origin, range_m, dispersion_moa, stance, aim, bearing_deg, part | p_hit_part, p_miss, samples |
| `wfmedical:chain` | launcher, round, content_origin, range_m, dispersion_moa, cover, thickness_m, angle_deg, armor, limb, stance, aim | speed_ms, p_hit, p_cover_perforate, p_cover_ricochet, p_cover_stop, exit_speed_ms, damage_in, armor_outcome, armor_wear_frac, p_major_trauma, p_fracture, p_internal_bleeding, wound_mode, bleed_ml_tick, pain, p_incapacitated, t_incapacitated_s, p_dead, t_dead_s, p_incapacitated_total, p_dead_total, samples |
| `hazards` | hazard, kind, content_origin, intensity, armor, modifier, shelter, duration_s | amount_in, amount_after_shelter, protection_type, outcome, through_hit, absorbed_hit, hits, through_total, dose_total_rad, dose_mult, wear_first_contact_pts, wear_per_interval_pts, armor_life_s |
| `wfmedical:hazards` | hazard, kind, content_origin, intensity, armor, shelter, duration_s | amount_after_shelter, hits, p_major_trauma, armor_wear_frac, wounds, bleed_ml_tick, pain, p_incapacitated, t_incapacitated_s, p_dead, t_dead_s, samples, horizon_s |
| `wfweight:loadout` | armor, weapon, ammo, kit, content_origin | total_kg, armor_kg, weapon_kg, ammo_kg, kit_kg, unplaced, carry_capacity_kg, load_frac, threshold_index, speed_mult, jump_mult, speed_locked, jump_locked, hard_locked, overweight_hp_s, max_stamina, stamina_use_mult, sprint_s, regen_s, jumps_to_empty, groups_over, group_over, kinetic_through_frac, blast_through_frac |
| `wflib:scenario` | scenario, case_id, case, launcher, round, range_m, step, stage, layer | speed_in_ms, speed_out_ms, p_pass, p_reach, verdict, detail (JSON) |
| `shots` (recorder) | kind, round, content_origin, shooter, target, threat_kind, part, outcome | tick, x, y, z, speed, caliber, pen, damage_in, damage_out, exit_speed, spent |

## Hazards and shelters

- `LabHazards.REGISTRY` (owners register emitter constants): mustard gas, phosgene (mist), fire normal / phosphorus / thermite, radiation field (RAD/s x 1, 10, 100, 1000). Tear gas: no damage, no row.
- `hazards` (wflib): damage hazards through `ArmorResolution.resolveWhole` (whole-entity door); radiation through `ContaminationUtil.contaminate` once per second (armour, Rad-X `modifier=rad_x`, `DoseShields`); armour wear = `acuteWear` + `sustainedWear` per exposure interval, `armor_life_s` = weakest layer.
- `wfmedical:hazards`: one `DamageEvents.resolveHit` per hit interval (limb by the real hit sampler), body stepped between hits, untreated afterwards. Radiation sickness (private thresholds in `HazardEntityEffects`), anomalies, psy, EMP, item hazards, glyphid acid, laser: not mapped (no registered emitter constants / not hit-based).
- wfcore shelter `wfcore:gt_hazmat` (kind `gas` only): occupant dressed in the four `gtceu:hazmat_*` pieces, answer = `MistEntity.isAffectable` (the gate every cloud tick uses; `MistEntity.IMMUNITY` holds wfcore's `HazardProperty.ProtectionType.FULL` predicate). Other kinds pass through. wfcore `HazmatArmorStats` (vanilla ARMOR attribute 1/piece) is outside `ArmorResolution` (door reads `ArmorSpec`, never the attribute; `ArmorEvents.onAttributes` strips ARMOR from managed pieces): no row.
- `LabShelters` (`LabShelter.shield(occupant, kind, amount)` runs the owner's real shielding; `registerLazy` for suppliers): every hazard row repeats per shelter, `none` first. No registered shelter => no extra rows.
- Manifest table `domain`: `player` (default), `vehicle` (`LabProducer.domain()` override), `shared` (round_flight, cover, cover_blocks, shots), `missile`.

## wfweight loadout

- Producer `wfweight:loadout` (`compat.wfweight.WeightLoadoutProducer`): registered when `wfweight` loaded + `labs` + `armor` on. Dependency direction: wflib -> wfweight (optional, existing `compat.wfweight` bridges); wfweight stays free of wflib.
- Cell = armour piece (or `none`) x weapon x ammo load x kit on a `LabRows.victim` inventory; one worker per armour piece. Weights: `WeightViews1211.weightOf` (wfweight resolver + wflib `ItemWeights` contents); totals / threshold: `PlayerWeightTracker.refresh` on `WeightViews1211.player` (equipment bonuses, `CarryModifiers`); groups: `resolveGroupLimitState`; stamina: `StaminaMath` ticked as `UltimateWeight1211.syncStamina` does (`drained`, `clamp`, `resolveExhausted`, `StaminaModifiers.recoveryPerTick`).
- Params: `armor=` glob, `inserts=1`, `weapons=` item id globs (default `none`), `ammo=` / `kits=` comma list of loads `id*count[+id*count]` (`id` = ammo variant else item; default `none`), `ref_hp=` (20).
- `sprint_s` full -> exhausted sprinting, `regen_s` exhausted -> recovered resting (null: not reached in 3600 s). `jumps_to_empty` back-to-back, no regen. `kinetic_through_frac` / `blast_through_frac` = `ArmorResolution.resolve` through / `ref_hp` for the piece alone; join to `personal_armor` on `armor`.
- `unplaced` = items that did not fit the 41 slots; weights count placed only. Manifest `content.packs["wfweight:weights"].weight_config` = SHA-256 of the active weight config (yaml, not a `config` override target).
- Not mapped: fall damage multiplier, backpack / Curios contents, creative immunity.

## Missiles

| table | dims | measures |
|---|---|---|
| `missile_flight` | missile, content_origin, range_m | warhead, attack_profile, cruise_ms, health, p_detonated, p_dud, p_timeout, p_offloaded, t_flight_s, burnout_s, apex_m, impact_ms, miss_m, miss_median_m, samples |
| `missile_vulnerability` | missile, source, source_kind, content_origin, distance_m | response, health, blast_size, damage_in, damage_per_hit, hits_to_down |
| `interception` | defender, missile, content_origin, offset_m, approach_m | cruise_ms, health, evasion, rcs, p_kill, p_leak, p_fail, p_timeout, kill_range_m, t_kill_s, spent, closest_intercept_m, leak_miss_m, samples |

- `missile_flight`, `interception`: `LabProducer.ticksWorld()` => real server ticks (`LabTicks`), headless only; `all` skips them in game. Lanes at x=200000, 4096 apart: chunks held + entity sections loaded before start; origin = generator surface, cleared above (radius 4, 64 up); batch start aligned to game time % 1200; `level.random` reseeded per batch; missile UUID + entity seed fixed; each tick waits for `WorkerPool` idle (recon passes adopt on a fixed tick). `trace=1` logs each missile's telemetry.
- Missiles = preset `builder` + warhead `wflib:inert`: flight, fuze, damage response, intercept rolls untouched; blast never runs (world kept). Blast effect => `explosive`. Cluster splits (RecursiveFrag) not followed.
- Flight: ground launch -> ground target +x; lab listener spans the corridor => entity path the whole way (no offload). `miss_m` = detonation / dud point to target, horizontal.
- Vulnerability: blast sources + damage as `explosive` (`ExplosiveProducer.sources` / `damage`, nearest-surface distance, density 1) -> `MissileEntity.hurt(explosion, dmg)` repeated on an armed, unspawned missile until downed (cap 10000); null `hits_to_down` = immune. No round rows: flying `MissileEntity.isPickable()` = dud only => rounds never strike one. Other live paths: interceptor kill / chip (`interception`), CA laser / collision.
- Interception: own defender / attacker side per engagement (lanes never share a recon net), no lab listener (offload / sim / onload as live). KILL = threat downed, or its end tick == an INTERCEPTED event of a lane interceptor; FAIL = fuel out first; else LEAK. `spent` = max(site count, interceptor entities seen in the lane).
- Gap: laser / collision damage not in `missile_vulnerability`; countermeasures not mapped. Interception rows: interceptors spawned by defenders draw entity UUIDs from unseeded entity randoms => rare one-interceptor divergences between runs (2026-11-04: 3 of 24 cells); flight + vulnerability identical across runs.

## Models and limits

- Flight: level shot along +x from muzzle, no wind (no drift column); rockets burn `motorAccel` x `burnTicks` along the nose; speed = step crossing the range, time/drop interpolated in-tick.
- Cover: infinite wall of stacked 1 m blocks (+ remainder), incidence from normal. Live multi-block perforation differs: vanilla inside-start clip enters block n+1 `0.001 x remaining step` deep => live exit speed <= 1% above row (single block exact).
- Ricochet = seeded roll; `p_ricochet` = share of `samples` seeds. Only block 1 can ricochet (game rule). Drill rounds: `penetration` blocks with `resistance <= penetrationResistance` pass, rest as pen.
- Personal armour: DT/DR door per slot, no incidence term. Zone = equipment slot; suits answer for occupied slots. Head: `headshot_factor` applied (unclaimed bodies); wounds / chain: no factor (WFMedical claims the body, limb decides).
- Armour sets: every `ArmorItem` with a spec (authored or vanilla-derived), bare and with each compatible insert filling all slots (`+insertxN`); `LabArmors.sets(ctx)` = `ArmorSetups.all()` minus `armorExclude` (`wflib-labs.toml`, glob list, default `["gtceu:*"]`; garment or any insert matching) then `armor=` and dev filter. Effective list: manifest `grid.armor_exclude`. Lists are not `config` override values: edit the toml. Wear = `ceil(points x threat.wearFactor)`.
- Explosive: standard cross processor, clear LOS (density 1), nearest-surface distance, `calculateDamage(..., radius)`; per-limb door as BLAST on chest. Not evaluated: fragment bomblets, shaped-charge cone, claimed-body random limb pick.
- Wounds: untreated. `MedicalEngine.bleedAndRegen` + `advanceTrauma` + `settle` every `updateIntervalTicks`, horizon 1800 s. Not simulated: stress, substances, wake-up, treatment. Sim cached by wound signature.
- Hit zones: `Rounds.spread` at `dispersion_moa / 60` deg about the aim ray; each sample through `Rounds.hitLive` (AHF rig OBBs, registration envelope, PRECISE gap rule) on a `LabRows.victim` posed by `AhfStance.request` + `StanceLogic.serverTick` x16 (`sneak` = vanilla shift pose, no stance state). Level shot, zero-drop sights, shooter at aim height `range_m` away on `bearing_deg`; segment = range plane +-2 m. Unarmed idle pose. `part`: 6 `HitboxPart` + `other` (box fallback); `p_miss` repeats per part row; first struck part (no pierce). Idle arm bob follows `tickCount`: each stance built at 16 tick counts, sample i shoots body `i % 16` (time average). Cell result depends on (dispersion_moa, range, stance, aim, bearing, seed, samples) only: identical across launchers sharing dispersion, thread counts, runs. Rows written on the calling thread in grid order.
- Chain: wound columns conditional on perforating the cover; limb row `p_hit` = `p_hit_part` of the limb (stance, aim), `*_total` = `p_hit x p_cover_perforate x p`. `limb=all` row: `p_hit` = sum over parts; p_major_trauma / p_fracture / p_internal_bleeding / p_incapacitated / p_dead = part-weighted, conditional on a hit; `t_*` weighted by those; `*_total` = `p_cover_perforate x sum_part(p_part x p)`; limbs the garment does not cover answer bare; armor_outcome / wear / wound_mode / bleed / pain null. Ricochet off cover counts as no hit. Before 2026-11-04: `p_hit` = fixed rectangles (torso 0.5 x 0.7 m, head 0.25 x 0.25 m at the aim centre), no stance / aim dims.
- Dice: fixed `seed`; sample i uses `seed + i x 0x9E3779B97F4A7C15`.

## Scenarios

`wflib:scenario` (`explicitOnly`: `all` skips it; domain `shared`). Launcher -> flight to range -> ordered layers; one row per (case, launcher, range, layer).

| key | type | default |
|---|---|---|
| `name` | str | `scenario` |
| `launcher` | id \| [id] | every selected launcher (exact id, must be selected) |
| `range` | m \| [m] | `ctx.ranges()` |
| `layers` | [{`stage`, ...keys}] | required, non-empty |
| `samples` | int | `samples=` param / config |

- Sweep axis = any array: `launcher`, `range`, any layer value. Cases = cartesian product, order launcher, layers (layer then key order), range; last axis fastest. Cap 200000 => `IllegalArgumentException`. Unswept launcher / range expand per row, not per case.
- `case_id` = index in the product; `case` = swept values only, `layer1.thickness=0.5 layer1.angle=30 range=100` (empty when nothing swept).
- Stage unknown => `IllegalArgumentException` listing ids; unknown layer key => same. Stages: `ScenarioStages.REGISTRY` (bare id).
- Chain: `speed_in` of layer n+1 = `speed_out` of n; `p_reach` = product of `p_pass` through this layer. `p_pass == 0` => later layers emit nothing.
- Range beyond round life => one row, `step` 0, `stage` `flight`, verdict `EXPIRED`, `speed_in_ms` = muzzle.
- Rows ordered launcher id, case, range, layer; same seed => identical.

| stage | keys | `detail` |
|---|---|---|
| `cover` | `block` id, `thickness` m (>0), `angle` deg (0) | p_ricochet, p_stop, spent, depth, exit_pen, damage_frac |

`cover`: `LabCover.evaluate`, `samples` / `seed` as the `cover` table; `p_pass` = `p_perforate`; `speed_out` = `exit_speed`; verdict = `LabCover.Verdict` upper case.

```json
{"name": "wall then plate", "launcher": "wflib:5.56x45/ball", "range": [100, 300],
 "layers": [
   {"stage": "cover", "block": "minecraft:oak_planks", "thickness": [0.5, 1], "angle": 30},
   {"stage": "cover", "block": "minecraft:iron_block", "thickness": 0.25}]}
```

-> 2 x 2 = 4 cases, 2 rows each (fewer after a stop). Run: `/wflabs run wflib:scenario scenario=/srv/s.json`.

## Api (`labs.api`)

| type | role |
|---|---|
| `ScenarioStage` / `ScenarioStages.REGISTRY` | scenario layer: `id()`, `apply(ctx, ScenarioState, spec)` -> `Result(speedOut, pPass, verdict, label, detail)` |
| `LabProducer` | `id()`, `table()`, `explicitOnly()`, `produce(ctx, sink)`; fill `LabProducers.REGISTRY` at construction, guard `WFSystems.enabled("labs")` |
| `LabTable` | name + columns (`DIM`/`MEASURE`, `NUM`/`STR`, unit) |
| `LabSink` | `row(Object...)`, thread-safe |
| `LabArmors` | `sets(ctx)`: armour sets minus `armorExclude`, `armor=`, dev |
| `LabContext` | `level()`, `ranges()`, `rounds()`, `launchers()`, `seed()`, `param`, `paramNum`, `includeDev()`, `executor()`; `LabContext.of(...)` = memory context. `executor()` work: no chunk / in-level entity access (`getChunk` off-thread waits on server thread blocked in `LabRows.each` => deadlock); world work first on calling thread |
| `LabLauncher` / `LabLaunchers.REGISTRY` | muzzle velocity, dispersion, damage scale, extra |
| `LabHazard` / `LabHazards.REGISTRY`, `LabShelter` / `LabShelters` | hazard emitter constants, shelter shielding callbacks; `LabShelters.shield` = per-item table, server thread |
| `LabContent` / `LabContents.REGISTRY` | packs, config files (also listed in `list.config_files`; `config` overrides reach any loaded config file), dev predicate for manifest `content` and `content_origin`; `markDev(id)` |
| `LabRecorder.record(table, schema, row...)` | rows into the active recording |
| `LabTicks` | `tick(server)` = `tickServer` minus autosave / gametest start, then queued tasks; `hold` / `release` chunks; `align`. Headless only (`available()`). Tick count gates queued tasks => entity sections never load without it |
| `LabDefender` / `LabDefenders` | `place(level, at, team)` -> `Site` (`tick`, `spent`, `remove`); `registerLazy` for data-driven defences. wflib: both interceptor turrets; CA: every vehicle with `air_defense` |
| `LabFlight`, `LabCover`, `LabRows` | flight / cover evaluators and producer plumbing for dependents' chains |

## Parity gametests

`LabServeGameTest` (batch `wflib_labs`): streamed rows == written rows, override applied / restored / toml sha unchanged / bad input rejected without partial apply, slow client dropped and server reusable.

Row == live: `MissileLabGameTest` (missile_vulnerability hits_to_down vs live `ExplosionAEF` blasts at a held armed missile), `RoundLabGameTest` (cover vs live `RoundPierceEvent`, flight vs live `Rounds.step`), `ArmorLabGameTest` (personal_armor vs `ArmorResolution.applyToEvent` on a real entity, explosive vs a real `ExplosionAEF`), WFMedical `LabGameTest` (wounds vs a real player hit, same seed), `WeightLabGameTest` (loadout vs live `UltimateWeight1211` on a survival server player: total kg, speed attribute, sprint / recovery ticks, one-jump drain), `HitZonesGameTest` (hit_zones cells vs `Rounds.fire` rounds struck on in-world bodies per stance, first part per `ProjectileStrikeEvent`; tolerance 4 sigma of both binomial errors + 0.01). Batches `wflib_labs`, `wfmedical_labs`.
