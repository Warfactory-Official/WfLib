# NTM: Continuum compatibility (draft)

Status: **draft for discussion**. Written from a read of both code bases; nothing here has been run with both mods loaded yet.
"Verified" below means read in the source, not observed in a game. Section 9 lists what needs deciding.

## 1. What we are dealing with

| | WFLib | NTM: Continuum |
|---|---|---|
| Mod id | `wflib` | `hbm` (repo `Warfactory-Official/NTM-Continuum`, `~/Codebases/NTM-Continuum`) |
| Scope | shared Warfactory library: ordnance, soldiers, armour, PDA, contracts, ports of parts of NTM | the whole of HBM's Nuclear Tech Mod (about 4,400 source files: machines, power, fluids, reactors, guns, missiles, space, world gen) |
| Targets | 1.21.1 NeoForge | six nodes: 26.1 / 1.21.1 / 1.20.1 across NeoForge, Fabric, Forge; **our node is `1.21.1-neoforge`** |
| Base layer | its own `core` (systems, hooks, features) | **Golem Core** (`dev.golem.core`): cross-version hooks, data maps, damage events, entity data |
| Lineage | radiation, hazards, doors, nuke MK5, damage handling were ported from **NTM NEXT** | same lineage, ported separately |

Because both descend from NTM NEXT, much of the code is the same ideas under two package names (`com.hbm.handler.radiation.RadiationSystemNT`
and `com.wf.wflib.radiation.RadiationSystemNT`; `com.hbm.util.DamageResistanceHandler` and `com.wf.wflib.damage.api.DamageResistanceHandler`).
That is why running them together is not safe by default: two copies of the same simulation, hooked to the same game events, each
unaware of the other. It is also why compatibility is tractable: the data shapes line up.

## 2. What "complete compatibility" should mean

Four layers, in the order they have to be built.

1. **Coexist.** Both installed, nothing is applied twice, nothing crashes, one HUD, one command per job. This is the floor.
2. **Interoperate.** Continuum's items and blocks take part in WFLib's systems: its radioactive items have WFLib hazard rows, its armour has
   WFLib protection profiles, its blocks have WFLib penetration and rad-resistance values, its items have weights. And the other way round
   where it makes sense (a WFLib soldier can use a Continuum gun).
3. **Override.** Where WFLib's implementation is better (the MK5 ray caster, the radiation field, the armour resolver, doors, the PDA),
   Continuum's content runs on WFLib's engine when both are present, chosen per domain, switchable.
4. **Present as one.** One place to see and switch it (the feature switchboard, `docs/features.md`), PDA apps for both without duplicates,
   saves that carry over.

## 3. Collision map

Strategy key: **Coexist** (make them not fight), **Bridge** (feed data across), **Defer** (one side switches itself off), **Replace**
(WFLib's engine runs Continuum's content), **Hide** (suppress a duplicate in UI/creative).

| Domain | Continuum (verified) | WFLib | When both load | Proposed |
|---|---|---|---|---|
| **Damage / armour reduction** | `util/DamageResistanceHandler`, `ArmorFSB`, `IResistanceProvider`; registered through Golem `DamageEvents.HURT` / `ATTACK`; armour values are data maps (`ArmorDataMaps`: radiation resistance, protection classes, sets, DT/DR) | `damage.DamageEventHandler` on `LivingIncomingDamageEvent` at HIGH, resolver in `armor` (DT and DR per type, per slot, wear charged to the layer that stopped it) | a hit can be reduced twice (once by each), or by the wrong one for the wearer | **Defer + Bridge**: WFLib resolves; Continuum's DT/DR table is read into WFLib armour profiles (section 5.2) and Continuum's own reduction is skipped for entities WFLib handled. First thing to prove with a test. |
| **Radiation field** | `handler/radiation/RadiationSystemNT` (same algorithm), facade `ChunkRadiationManager.proxy`, sidecar `<dim>/hbm_radiation`, `RadiationFog`, config `RadiationConfig.enableChunkRads` | `radiation` system, sidecar `<dim>/wflib/radiation`, `/wfrad`, `GeigerHud` | two fields ticking, two fogs, two saves; a source added to one is invisible to the other | **Defer** one. Default: WFLib's runs, Continuum's tick is off (`enableChunkRads` is already a switch), Continuum's `ChunkRadiationManager.proxy` calls are redirected to ours. Import Continuum's sidecar on first load. |
| **Item hazards / contamination** | `hazard/*` (`HazardSystem`, `HazardRegistry`, types, modifiers, transformers incl. RBMK/RTG/Zirnox), rows as data maps `hbm:hazards`; `util/ContaminationUtil`; `EntityEffectHandler` | `hazard` system, rows in the synced `wflib:hazard` registry, **no shipped rows**; `LivingHazardProps`, `HazardEntityEffects` | Continuum's items hurt via Continuum; WFLib's scan runs over the same inventory and finds nothing (no rows) or double-applies if rows are added | **Bridge**: generate `wflib:hazard` rows from `hbm:hazards` at server start, and let WFLib's scan own the effect while Continuum's per-tick hazard arm stands down. The modifiers that need Continuum items (RBMK, RTG, fuel depletion) stay in Continuum and are called through a seam (5.3). |
| **Hazmat and protection checks** | `HazmatRegistry` (+ `HAZMAT` notify hook), `ArmorRegistry`, set checks, gas-mask filters | item tags `wflib:hazmat`, `hazmat_advanced`, `faraday`, `digamma_protection`, `hazard_protection/<class>`; armour wear + filter wear | two answers to "is this player protected" | **Bridge**: fill WFLib's tags from `ArmorDataMaps` (`PROTECTION_CLASSES`, `FARADAY_ARMOR`, sets) |
| **Nuclear blast (MK5)** | `EntityNukeExplosionMK5`, `ExplosionNukeRayBatched`, `ExplosionNukeGeneric`, `EntityFalloutRain`, `EntityNukeTorex`, `BombConfig`, `Environment.NUCLEAR_BLAST` notify hook; spawned by bomb blocks and missiles | `explosion` system: `EntityNukeExplosionMK5`, `ExplosionNukeRayParallelized` (parallel, section snapshots), `Fallout` as a per-chunk ground source, torex | both entity types exist; a Continuum bomb runs Continuum's | **Replace** (opt-in, default on if the numbers match): when a Continuum bomb would spawn its MK5 entity, spawn WFLib's with the same parameters. Needs a seam in Continuum (5.4) or a mixin. Fallout stays one system (radiation owner). |
| **Doors** | `ModDoorBlocks`: 14 `BlockDoorGeneric` doors (`DoorDecl`) plus `BlastDoor` and vanilla-style doors | `door`: 15 doors, one GemRender model each, redstone and key only, `DoorType` table | two sets of the same doors in the creative menu and in recipes | **Hide** one set (default: show Continuum's recipes, give WFLib's a conversion) or **Replace** the renderer. Decision needed (section 9). Tables share NTM's numbers so a bunker plan fits both. |
| **Missiles / ordnance** | `entity/missile/*`, `CustomMissile*`, `MissileStruct`, warheads | `missile`, `warhead`, `round`, `countermeasure`, `drone`; the warhead framework is pluggable | independent unless a Continuum warhead should fly on a WFLib missile or the reverse | **Bridge** later: register Continuum warheads as WFLib warheads (SPI exists) so WFLib guidance flies them. Not needed for coexistence. |
| **Mines, charges, detonators** | `MineConfig`, mine blocks, `ModExplosivesBlocks`, detonator items | `mine`, `demolition` (mining charge, detonator that fires "other detonatables") | duplicates; the detonator may or may not fire Continuum charges | **Bridge**: Continuum's charge blocks implement WFLib's detonatable seam (or the reverse) |
| **Drones** | `ModDroneBlocks`, `IDroneContainer`, 37 classes | `drone` (delivery, mine-laying, demolition missions, squads), `work` | parallel features | **Coexist**; look at `IDroneContainer` to let WFLib drones service Continuum drone containers |
| **Grenades, guns, magazines, barrels, lasers** | `items/weapon`, 53 gun classes, 10 magazine, 13 barrel, 17 laser | `grenade`, `magazine`, `barrel`, `laser`, `round`, `ammo`, soldier weapon SPIs | Continuum weapons do not know WFLib's magazine/barrel state | **Bridge** in two tiers: (a) soldiers can fire Continuum guns (a `SoldierWeapon` for Continuum), (b) barrel heat and magazines optional |
| **Glyphids** | `Glyphid*` (22 classes) | `colony` (simulated colony, castes, nests, `EntityGlyphid`) | two glyphid populations | **Defer** one (open question: which); share spawn data |
| **Satellites / space** | `ModSatelliteBlocks`, `SatelliteDataMaps`, `Environment` | `orbital` (orbits, ground stations, pads, shuttles, PDA console) | both own "satellite" | **Coexist**, then **Bridge**: Continuum's `Environment` answers (pressure, breathing) are a hook other mods already feed; WFLib orbital should answer through it |
| **Radar / recon** | `ModRadarUtilityBlocks`, `IRadarDetectableNT`, 28 radar classes | `recon` (sensors, radar, telemetry), PDA Radar and Telemetry apps | parallel | **Bridge**: `IRadarDetectableNT` entities appear in WFLib radar |
| **Physics** | `com.hbm.physics` (GJK, convex mesh, rigid body), `SubLevelBlasts` (Sable) | `physics` (rigid-body engine, decks, prediction), `ragdoll`, `corpse` | two engines; Continuum's is small and used for blasts and sub-levels | **Coexist** (they do different jobs); check `SubLevelBlasts` against WFLib decks |
| **Pollution / soot** | `handler/pollution`, `RadiationConfig` soot and lead | none | n/a | **Keep Continuum's**; WFLib hazard can read it as a dose source |
| **Wiki / PDA** | `wiaj`, `qmaw` (in-game wiki), `CommandWikiRender` | PDA with apps, `wiki` system, feature switchboard | two UIs | **Bridge**: a PDA app that opens Continuum's wiki pages; list both in the switchboard |
| **Item weights** | none | `compat/wfweight` plus `ItemMasses` (mass of non-wflib items) | Continuum items weigh the default | **Bridge**: a mass table for Continuum items (cheap, high value, see 5.1) |
| **Commands** | `/hbm...` (`HbmCommands`, `CommandRadiation`) | `/wfrad`, `/wflib ...` | two commands for the same thing | **Coexist**; alias where one side is deferred |

Out of scope on purpose: Continuum's machines, power, fluids, conveyors, reactors, world generation. WFLib has nothing to say about them.

## 4. The mechanisms available

Continuum is a Warfactory project on Golem Core, so there are three kinds of way in. In order of preference.

1. **Upstream seams.** Ask Continuum (and Golem Core) for the hook we need: a *cancellable* or *replaceable* version of what it already
   publishes as notify-only (`HbmEvents`, `Environment.NUCLEAR_BLAST`, `HazmatRegistry.HAZMAT`, `DamageEvents`). Cleanest, survives refactors,
   and the same team owns both sides. Slowest because it is a change in another repo.
2. **Data.** Continuum already exposes most of what we need as data maps (`hbm:hazards`, armour resistance, protection classes, sets, DT/DR,
   radiation emission per block). Reading them at server start and writing WFLib rows (hazard registry, tags, profiles, masses) needs no change in
   Continuum and cannot break at runtime. This carries most of layer 2.
3. **Our own mixins, applied only when `hbm` is loaded** (a mixin plugin that checks the mod list; `required = false`; targets pinned to a
   Continuum version range). Brittle to refactors but available today. Use for the few places a seam does not exist yet: the
   `ChunkRadiationManager.proxy` redirect, the MK5 spawn, the damage-handler skip. Each one is recorded in a table with the Continuum
   version it was written against, and each has a GameTest that fails loudly when the target moves.

Supporting pieces:

* **Soft dependency.** `compileOnly` on the Continuum 1.21.1-neoforge jar (and Golem Core), the same pattern as GregTech and WarForge in
  `build.gradle`. Every touch goes behind `ContinuumCompat.active()`. The package is `com.wf.wflib.compat.continuum`. This respects the
  architecture rule that wflib source does not name a dependent *mod*: Continuum is a peer, not a dependent, but the same hygiene applies,
  so block and item ids live in datapack files (the `site_blocks` pattern), not in code, wherever they can.
* **Providers in the switchboard.** A new group appears only when `hbm` is loaded, one box per overlapping domain: *Use WFLib's radiation /
  armour resolution / MK5 / doors*. The box is the provider choice; defaults favour WFLib where this document says WFLib is better. Because the
  box is read at launch like the rest, "which engine runs" never changes mid-game.
* **A `Provider` record in `FeatureCatalogue`** (id, WFLib on, Continuum on) so the screen can say "Radiation: WFLib (Continuum's is off)" and the
  config file can name it.

## 5. Per-domain plans

### 5.1 Items and blocks (the cheap, high-value layer)
Generate, at server start (and on `/reload`), from Continuum's registries and data maps:
* `ItemMasses` entries for Continuum items (by category defaults, overridden by a shipped table).
* penetration and hardness rows for Continuum blocks, using Continuum's own blast resistance as the source.
* `wflib:rad_resistant` tag members from Continuum's `IRadResistantBlock` implementers and `markRadResistant` calls.
* tag membership for `wflib:geiger_counter` (Continuum's Geiger items) so the HUD works.
Effort: small. Risk: low. Does not need the engine decisions below.

### 5.2 Armour and the hit pipeline
Order matters; this is the first real test.
1. Read `ArmorDataMaps.DAMAGE_RESISTANCE` (per item), `ARMOR_SETS` (per set) and `ENTITY_DAMAGE_RESISTANCE` (per entity type) into WFLib `ArmorProfiles` for every Continuum armour item, at the same priority and with
   the same per-type split WFLib uses (physical, blast, fire, ...), so Continuum armour is a first-class WFLib armour.
2. Make exactly one reducer run per hit. WFLib resolves at `LivingIncomingDamageEvent` HIGH; Continuum reduces through Golem `DamageEvents.HURT`.
   Preferred: an upstream flag, `DamageEvents.HURT` listeners skipped when an attribute on the event says "already resolved". Fallback: a
   mixin on `DamageResistanceHandler` early-returning when WFLib has resolved the hit.
3. `IResistanceProvider` (Continuum entities that add DT/DR dynamically) is called by WFLib's resolver as a `DynamicResistance` source (WFLib
   already has `DynamicResistance`), so a Continuum boss keeps its behaviour.
4. Wear and modules: Continuum armour mods (`ArmorModHandler`) stay Continuum's; WFLib reads the worn module's effect only through the
   data map. Exoskeleton and power armour (`ArmorFSBPowered`) get a WFLib `Dress` only if the data map allows it, otherwise they stay un-managed
   (WFLib already derives a profile from vanilla armour for pieces it does not know).

### 5.3 Radiation and hazards
1. Decide the owner (default WFLib). Turn Continuum's chunk system off via its own switch (`enableChunkRads`) applied at config load, and redirect
   `ChunkRadiationManager.proxy.*` to `RadiationSystem` (one mixin, or an upstream `proxy` seam).
2. **Sources.** Continuum blocks that emit (`HazardDataMaps.RADIATION_EMISSION`) register as WFLib `wflib:rad_source` rows from the data map;
   Continuum code that calls `incrementRad` goes through the redirect, so reactors and bombs feed WFLib's field with no change to them.
3. **Saves.** On first load with both present, import `<dim>/hbm_radiation` into WFLib's sidecar (same shape, per chunk); otherwise worlds lose their
   contamination when the owner changes.
4. **Items and players.** Convert `hbm:hazards` to `wflib:hazard` rows; hazard modifiers that need Continuum items (RBMK, RTG, fuel depletion,
   Zirnox) stay in Continuum and are exposed to WFLib as `HazardModifier`s through a registration on its side. `HazardTransformerRadiationME`
   (AE2) is the gap WFLib's docs list: Continuum's version can be called.
5. **HUD and commands.** One Geiger HUD (WFLib's), `/hbmrad` aliased to `/wfrad`.
The `radiation.md` "Gaps" list is almost entirely things that only Continuum can fill because they need its items (RBMK, RTG, dosimeter,
digamma). Compatibility is how WFLib's radiation gets complete.

### 5.4 Nukes
Both have `EntityNukeExplosionMK5`; the mathematics should be near identical (same ancestry), so the proof is a **parity GameTest**: same bomb,
same seed, compare destroyed block counts, crater profile and the fallout source strength between Continuum's `ExplosionNukeRayBatched` and
WFLib's `ExplosionNukeRayParallelized`. If within tolerance, replace by default. The seam: Continuum bomb blocks and missiles call a spawn
function for the MK5 entity; ask for a `Hook` there that can substitute the entity type (a Golem `Verdict`-style hook already exists for
`Environment`). `BombConfig` values (`falloutRange`, `moreFallout`, `statFacNoRad`) are read and honoured by WFLib's version (they are on
WFLib's own gap list). `Environment.NUCLEAR_BLAST` keeps firing from whichever runs, so Continuum's advancements and satellite detector keep working.

### 5.5 Doors
Same numbers, same idea, different code. The decision is about the player, not the engine: show both sets, or one. Two clean options:
* **Alias**: Continuum's recipes stand; WFLib's door items are hidden from the creative menu and a datapack recipe converts either to the other.
  WFLib's renderer draws Continuum's door blocks via a model override (a `DoorVisual` registered for `BlockDoorGeneric`), which is the
  "WFLib does it better" path without two blocks.
* **Hide**: simply hide WFLib's set when Continuum is present. Cheapest; loses the better renderer.

### 5.6 Everything else (later)
Weapons for soldiers, warhead registration, radar targets, wiki app, drone containers, `Environment` answers for orbital: each is small once
layers 1 to 3 hold and each is independent.

## 6. Phases

| Phase | Deliverable | Needs |
|---|---|---|
| 0 | Test instance with WFLib and Continuum (1.21.1 NeoForge) and Golem Core; **conflict log**: mixin clashes, duplicate event handling, duplicate HUD, duplicate commands. A GameTest module `compat.continuum` that only runs when `hbm` is loaded. | a built Continuum jar (`build/prism-nukefx/hbm-neoforge-1.0.4-1.21.1-nukefx-local.jar` is the 1.21.1 NeoForge one on disk; confirm version) |
| 1 | **Coexist**: one damage reduction per hit, one radiation owner with Continuum's off, one Geiger HUD. Switchboard group "NTM Continuum" with the provider boxes. | seams or mixins (section 4) |
| 2 | **Interoperate by data**: hazard rows, armour profiles, tags, masses, penetration values (5.1, 5.2 steps 1 and 3, 5.3 steps 2 and 4). | none |
| 3 | **Override**: MK5 parity test then replace; door alias; radiation save import. | upstream seam or mixin |
| 4 | **Later bridges**: soldier weapons, warheads, radar, wiki, drones, orbital. | per item |

Phase 0 and 2 can start now without any decision from Continuum's side.

## 7. Verification

* Compat GameTests run in the Continuum-plus-WFLib instance only: single reduction (one hit, expected damage), one radiation field (source
  placed through Continuum code is read by WFLib's `/wfrad get`), hazard row parity (every `hbm:hazards` entry has an equal WFLib row), MK5
  parity, door placement.
* A **drift check**: a test that reads Continuum's data maps and fails when a new item has no mapping, the same idea as the feature catalogue test.
* The existing harness (`Warforge Harness Dev` instance and `ma_mcp.py`) plus a second instance with Continuum for screenshots of the
  Geiger HUD, PDA and doors.

## 8. Risks

* **Mixin brittleness.** Continuum is moving (1.0.1, six nodes). Every mixin needs a version range and a test. Prefer upstream seams.
* **Two Warfactory teams, one lineage.** Duplicate fixes will drift; the parity tests are the guard.
* **Golem Core on the classpath.** Compiling against Continuum needs Golem Core jars; the Gradle setup there is Stonecutter and multi-version, so
  our `compileOnly` should point at the 1.21.1 NeoForge artefacts only.
* **Save compatibility.** Changing the radiation owner must not lose contamination (5.3.3).
* **Performance.** Hazard scans and damage handlers are hot paths; a bridge must not add a map lookup per tick per item. Generate rows once and
  cache.

## 9. Decisions needed

1. **Who wins each overlap by default** (radiation, hazards, armour, nukes, doors, glyphids). The table proposes WFLib for radiation, armour and
   nukes (pending parity), data bridge for hazards, and asks you about doors and glyphids.
2. **Seams or mixins.** Are you willing to have a small set of cancellable hooks added to Continuum/Golem Core (damage handled, MK5 spawn,
   radiation facade), or should compat carry its own mixins into Continuum for now?
3. **Doors:** alias (Continuum's blocks, WFLib's renderer), hide, or keep both?
4. **Hard or soft dependency on Golem Core.** Soft (compileOnly, reflection-free behind `active()`) is proposed.
5. **Which Continuum version** is the supported baseline.
6. **Glyphids:** which colony model survives when both are installed.
7. **Defaults when Continuum is present:** should the NTM group appear ticked for WFLib's engines, or all unticked until a pack chooses?

## 10. First steps if you say go

1. Build or fetch the Continuum 1.21.1 NeoForge jar and Golem Core, set up the second harness instance, and capture the Phase 0 conflict log.
2. Add `compat/continuum` with `ContinuumCompat.active()` and the data bridges of 5.1 (masses, penetration, rad-resistant tag, Geiger tag).
3. Add the hazard-row and armour-profile generators (5.2.1, 5.3.4), with the drift test.
4. Write the single-reduction and one-radiation-field GameTests so the Phase 1 work has a target.
