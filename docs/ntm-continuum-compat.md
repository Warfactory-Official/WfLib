# NTM: Continuum compatibility (draft 3, started)

Status: **draft for discussion**, second pass. Read from both code bases (Continuum `main` at `e2f987a5`, Golem Core `5275cc5`);
nothing here has been run with both mods loaded. "Verified" means read in the source; "to validate" means it needs a test in a game.

## 0. Decisions

| | Decision |
|---|---|
| Armour | **WFLib's system wins.** Continuum's armour pieces get WFLib values; Continuum's own reduction stands down. |
| Doors | **Follow WFLib's door system**, starting with level 1 (recipes and items, data only). |
| Method | **No mixins** where avoidable. Continuum is modular by design: use what it and Golem Core already expose. |
| Golem Core | A **hard dependency** of Continuum, so it can be assumed whenever Continuum is present. |
| Baseline | The Continuum repository's `main` (`e2f987a5`), node `1.21.1-neoforge`. |
| Glyphids | **WFLib's colony replaces Continuum's.** Continuum's textures are referenced only when it is present, never copied. |
| Packaging | A **separate bridge addon** (`bridge-continuum/`, id `wflib_continuum`), a Golem-based mod. |
| Radiation | **Continuum's field stays the owner**, WFLib reads and writes through an adapter. |
| MK5 | **Ask Continuum for a ray seam.** Done (below). |
| Upstream | Small additive changes to Continuum are fine. Done (below). |

## 0.1 Status

Everything marked *tested* is a GameTest in `bridge-continuum` that passes (17 required). Run them with
`./gradlew -p bridge-continuum build` then `./gradlew runGameTestServer -PwithContinuum -PcontinuumOnly -x buildNatives`.

| Piece | State |
|---|---|
| Continuum seams (worktree `~/Codebases/NTM-Continuum-compat`, branch `compat/wflib-seams`, `21a7aed7`) | `EntityNukeExplosionMK5.RAY` hook with an overridable `createRay`; `CommonConfig.override`. In use. Not pushed. |
| Armour | WFLib's `wflib:armor_profile` datapack registry; the bridge empties Continuum's per-item resistance, strips `resistance` from its 34 sets, seeds 137 profiles. *Tested:* data stood down, Continuum's own reduction is the identity, a hit on a worn set is reduced and not to the level a second reduction would leave, seeded pieces add up to the set (thresholds sum, resistances combine), the converter's maths, the profile codec reads and refuses. |
| Doors, level 1 | The 15 assembly recipes make `wflib:` doors; Continuum's door items are off every creative tab. *Tested:* all 15 recipes, the tabs. Placed doors and levels 2 and 3 (substituting the blocks, keys and locks) are not built. |
| Glyphids | Continuum's `entity_glyphid*` natural spawns vetoed, hives off (override plus config reload), WFLib glyphids drop Continuum's meat (new `EntityGlyphid.DROPS`). *Tested:* veto, hives, drops. Placement is WFLib's own seeder (`ColonyConfig.naturalSpacing`, default 512 blocks, 70% of cells); Continuum placed about one hive per 256 chunks, so WFLib colonies are sparser and bigger. A tuning choice, not code. |
| Radiation | New `RadiationField.Provider` seam in WFLib. The bridge makes Continuum's field the one WFLib reads and feeds, and tells Continuum through `ContaminationUtil.RAD_IMMUNE` that players are WFLib's. Continuum's item hazards become `wflib:hazard` rows (from `hbm:hazards`), and its protection data become the WFLib tags (`hazmat`, `hazmat_advanced`, `digamma_protection`, `faraday`, `hazard_protection/*`). *Tested:* WFLib reads and feeds Continuum's field; players are immune to Continuum's contamination, a pig is not; every id-keyed hazard row matches level for level; every set that passes a check is in the tag. **Not proven:** one dose per player in a real exposure (a fake player cannot stand through Continuum's 200-tick grace); hazard modifiers (RBMK, RTG, depletion) are not carried, so those items use their base level. |
| Nukes | WFLib's parallel MK5 ray behind Continuum's five-method `IExplosionRay`, answering the new hook. *Tested:* a 24-radius blast into solid stone and into solid dirt, craters within 10% (counted after the world writes settle). **Off by default** (stone parity fails, see Open). A failed WFLib ray ends the blast without Continuum's abort path. |
| Switchboard | `FeatureSource` API in WFLib; the bridge's group "NTM: Continuum" (five boxes, master) is on the screen, seen live. *Tested:* registered, writes through. Clicking a box was not exercised. |
| Masses, probe, wiki | Continuum's item masses (from its recipes, steel plate as the unit), `ILookOverlay` lines in WFLib's probe, and its QMAW manual pages as WFLib wiki articles (items they describe open them). *Tested:* masses, probe fill conversion, wiki converter. |
| Soldiers with Continuum guns | `SoldierServices.addWeapon` (new, `CombinedWeapon`) lets a second gun mod sit beside the first; the bridge's `SednaWeapon` reads a magazine-fed Sedna gun's ammunition, presses `GUN_PRIMARY`/`RELOAD` through `handleKeybind`, and arms soldiers from the gun's default ammo. *Tested:* unit test for the combiner; GameTests arm a fake player, fire (magazine drops) and reload an emptied gun. Needed a Golem Core fix (branch `compat/null-channel-guard`, `068fe20`): `canSendToPlayer` threw for a stub connection with no netty channel. Not tested with a real soldier entity in the world; guns without a detachable magazine (single-shot launchers) are not offered. |
| Warheads | `wflib_continuum:he`, `nuclear`, `nuclear_small` registered with `Warheads` (HE via `ExplosionLarge`, nuclear via the MK5 entity plus torex). *Tested:* HE removes the block under it, nuclear raises one MK5 entity. Not carried on a real WFLib missile. |
| Bullets and penetration | Every Continuum bullet config (183) gets a `Threat` (bore, penetration in mm of steel, kind) from a hand-set cartridge-and-load table (`Ballistics`): 9 mm 1.2 mm up to a .50 BMG AP at 47 mm and a 240 mm DU shell at 288 mm; Continuum's own threshold-negation and armour-piercing fields raise it. A `StrikeContext.ADAPTERS` seam (new, WFLib) lets any damage source from a Continuum bullet answer armour and hull code. The 51 *plain* bullets (no custom flight or impact lambda, physical damage) are not flown as Continuum's entity: the spawn is turned into a WFLib round (`EntityJoinLevelEvent`, no mixin) with the same speed, gravity, life, ricochet angle, armour piercing and a tracer, so they get WFLib's lag compensation, terrain and tracers. Explosive, incendiary, rocket, flame and beam loads stay Continuum's but still carry the threat. Ammo items show the figures in their tooltip. *Tested:* every config has a profile and the figures order as real ones do (9 mm < 5.56 < 7.62 < .50 < AP < DU < shells); a damage source from a bullet carries a threat; a plain bullet becomes a round and hurts a husk; a worn Continuum plate takes a converted bullet down. *Not done:* the table is mine, not Continuum's, and wants review; vehicle-armour hits by Continuum bullets were not shot at a real WFLib vehicle; Continuum's headshot band is replaced by WFLib's part model; `accel`, lock-on and spread submunitions stay entities. |
| Protection stats | WFLib's armour tooltip (DT, DR, tier, condition) now shows on any item that has an armour profile, not only WFLib's own pieces (`ArmorDataClient.onTooltip`). Continuum's own "radiation resistance" line is unchanged beside it. |
| Base styles | `data/<ns>/wflib/site_styles/*.json` (new): a style is a set of block roles plus per-block weathering variants; each base picks one by its position (the plain look has weight 2), `/pmc buildbase <kind> <seed> <style>` forces one. The bridge ships `ntm_bunker` (brick concrete with cracked/mossy/broken variants, reinforced brick, concrete pillars, reinforced glass, steel blocks, barbed wire on the wall) and `ntm_industrial` (rebar concrete, asbestos concrete, steel decoration, asphalt, metal fence). *Tested:* both offered; a base built in `ntm_bunker` stands in more than 50 Continuum blocks, some weathered. *Not done:* NTM's own structure shapes (silos, radio masts, vertical shafts) are not new base pieces; only the look follows. |
| Recon | Continuum missiles and rockets are contacts on WFLib's recon net (radar app, scopes) with a cross-section from their size, a stealth missile a tenth of it; its explosions (`HbmEvents.EXPLOSION`) are seismic events. *Tested.* Continuum's own radar blocks and alerts are untouched. |
| Orbit | A Continuum satellite reaching orbit (`XSatelliteRegistry.ORBITED`) is mirrored as a WFLib satellite `NTM-<frequency>` with the payload that matches (relay, radar, miner, laser, imagery); it comes down when Continuum's is gone. Unaffiliated (no net), on a track through world spawn, because the hook does not say where it launched. *Tested.* |
| Explosion effects (experimental) | Seam in Continuum: `ExplosionCreator.INTERCEPT` (a Golem `Hook`, commit `2d70b506` in the compat worktree). The bridge answers it with WFLib's GemRender small/standard/large effects (switch `explosionfx`, on). The nuclear cloud (`EntityNukeTorex`) stays Continuum's. *Tested:* the three sizes are intercepted. **Not looked at in game.** |
| Not built | door level 2 (substituting the 15 `hbm:` door blocks and block entity needs a WFLib refactor first: the `instanceof DoorBlock` sites become an interface) and level 3 (keys and locks); infantry hit-location for Continuum guns; drone containers (nothing needed bridging found). |
| Open | `nukes` stays off until the stone crater parity (Continuum 1255 vs WFLib 2838 blocks) is understood; radiation exposure per player in a real world; hazard modifiers. The RBMK items' wiki icons come from the block particle when the item has none; the Template pages are skipped. In the old test instance, EMI's "Error Rendering" on Continuum's assembly machine page was GTCEu's modularui mixin failing against JEI 19.57, not the bridge; the new instance has GTCEu removed. |

Findings from running them together:
* **WarForge and Golem Core do not load together as shipped.** `WarForgeMod.commonSetup` registers `GolemProtection` when `golemcore` is loaded, and that class
  needs `dev.golem.core.world.Protection$Provider`, which exists in no Golem Core commit. WarForge had to be taken out of the harness for the test.
* **A mock player in a GameTest crashes the server in this stack** (`ahf:hit_authority`, `wfweight:weight_update` payloads with no connection); tests use a
  fake player or a mob.
* **Continuum's config is read in its own constructor, before an addon's.** `CommonConfig.override` therefore needs a reload (`MainRegistry.loadConfig`) to take;
  the bridge does it. An upstream improvement is to re-apply overrides when they are declared late.
* **Continuum's glyphid entity ids are `hbm:entity_glyphid*`**, not `hbm:glyphid*`; the first veto matched the wrong ids and the test caught it.
* **A GameTest only registers when its template namespace is enabled**, so the bridge ships its own `wflib_continuum:empty` template.
* Continuum's own build needs every node's Golem jar; a clean build here used a worktree registering only the two NeoForge nodes (not committed).

## 1. What we are dealing with

| | WFLib | NTM: Continuum |
|---|---|---|
| Mod id | `wflib` | `hbm` |
| Scope | shared Warfactory library; ports of parts of NTM (radiation, hazards, doors, MK5, damage) | the whole of HBM's Nuclear Tech Mod, about 4,400 source files |
| Targets | 1.21.1 NeoForge | six nodes; ours is `1.21.1-neoforge` |
| Base layer | own `core` (systems, features) | **Golem Core** (`dev.golem.core`): hooks, data maps, registry substitution, damage events |

Both descend from NTM NEXT, so the same ideas exist twice under two package names. Run together, nothing is isolated: **confirmed in
source, the damage is applied twice** (Golem's NeoForge bridge listens to `LivingIncomingDamageEvent` at normal priority, after WFLib has
already reduced the hit at HIGH, and then reduces the already-reduced amount again).

## 2. How to get in without mixins

Golem Core was built so that other mods can change a Golem-based mod without patching it. Everything below is **verified present**.

| Seam | What it does | We use it for |
|---|---|---|
| **`ModEntries.start(entry)`** and `ModEntry.substitute() / declare() / registered()` | the one start-up path for Golem-based mods; `substitute()` runs before any registry is filled | the entry point of our bridge |
| **`Substitutions.block / item / entry(registry, id, factory)`** | replaces another mod's block, item, block-entity type, entity type... **under its own id**, before registries fill; worlds, recipes, tags and the owner's own references keep working. The substitute must subclass the class it replaces; one mod per id; a miss is reported at registry fill | doors, MK5 entity, Continuum blocks that need WFLib behaviour |
| **Data maps with patches** (`DataMaps`) | values on registry entries as data. A mod changes another mod's map with `data/<own ns>/value_maps/patches/<map ns>/<registry>/<map>.json` (`replace`, `values`, `remove`), applied after every copy of the map's own file, order well defined. `map.byId()` / `byTag()` read the merged result; `map.changed` fires on reload | neutralising Continuum's armour values; reading its hazard, armour and emission data |
| **Hooks** (`Hook`, phases, `Verdict`) | about 50 in Continuum and Golem. *Replaceable* (listeners can decide): the whole `Environment` family (breathing, pressure, surface, orbit, sunlight, groundwater, gravity, compression, growth, satellites), `SkyOwnership.OWNERS`, `ContaminationUtil.RAD_IMMUNE`, `ItemReactorSensor.USE`, `ItemBedrockOreBase.SOURCE`. *Notify-only*: `HbmEvents` (explosion, bomb, missile, machine recipe, entity irradiated), `Environment.NUCLEAR_BLAST`, `AMBIENT_RADIATION`, `ArmorUtil.HAZMAT`, `ContaminationUtil.GEIGER_LINES`, `HazardTypeRadiation.STACK_RADIATION`, `ILookOverlay.PROVIDERS`, `TileEntityDoorGeneric.MOVED`, Golem `DamageEvents`, `Spawns.CHECKS` (a natural-spawn veto), `Attacks.INTERCEPTORS`, `Deaths.ALLOW`, `ChestLoot.INJECTIONS`, `BlockBreaks.HANDLERS` | stand-down verdicts, spawn vetoes, loot, look-at overlay, reacting to events |
| **Datapack tools** | recipe removals (patch style), loot tables and biome modifiers with `neoforge:conditions` | door and glyphid recipes and drops, world gen |
| **Plain static config fields** | Continuum's config classes are public static fields read live (`MobConfig.enableHives`, `RadiationConfig.enableChunkRads`; world-gen keys are bound to them) | switching a Continuum feature off from `registered()`. Not a mixin, but it writes another mod's field: fragile against `/hbm reload`. Prefer an upstream override API (below). |

### Where the seams run out

What is **not** replaceable today, and the smallest additive change that would fix each. These would be small pull requests to Continuum or
Golem Core, not patches of ours:

| Gap | Smallest upstream seam |
|---|---|
| Damage: `DamageEvents.HURT` and `ATTACK` only notify, so Continuum's reduction cannot be skipped | none needed for armour: see 3.1, it is solved with data. Only entity-side providers and `ArmorFSB.handleHurt` stay, and those are not DT/DR. |
| The MK5 ray is created inside `EntityNukeExplosionMK5.tick()` and held in a private field | a `protected IExplosionRay createRay(...)` factory, or a `Hook` that answers with a ray |
| Config values are only changeable by editing the file | `CommonConfig.override(key, value)` applied after load, so addons can state intent without poking fields |
| `ChunkRadiationManager.proxy` is a final facade over a static system | not needed if Continuum's field stays the owner (section 5.1) |

### Packaging: a bridge addon, not code inside WFLib

Substituting Continuum classes means subclassing them, which needs Continuum **and Golem Core on the compile classpath**. Putting that in
WFLib would load Continuum classes from a library every pack has, and breaks WFLib's own rule that its source does not name a peer's
internals. Proposed instead:

* a small **bridge addon** (working name `wflib-continuum`, id `wflib_continuum`), NeoForge 1.21.1 only, `compileOnly` on Golem Core and
  Continuum, **mandatory dependencies on `wflib`, `hbm` and Golem Core**, so it loads only when all three exist;
* it is a Golem-based mod: its `@Mod` constructor calls `ModEntries.start(entry)`, `substitute()` replaces what must be replaced,
  `declare()` registers its own few things, `registered()` wires the bridges once every registry is filled;
* it carries a **datapack** (value-map patches, recipes, loot, tags, WFLib armour and hazard rows for Continuum ids), so tuning is data and
  changes need no code;
* it lives in this repository as its own source set and jar (the same pattern Pluginless uses for `src/pda`), so it versions with WFLib;
* the feature switchboard gets a **"NTM: Continuum" group** that appears only when `hbm` is loaded, one box per bridge (WFLib armour on
  Continuum gear, WFLib doors, WFLib glyphids, ...), so any of it can be switched off.

## 3. Per-domain plans

### 3.1 Armour and damage: WFLib values, Continuum's reduction off
**Verified:** every number in Continuum's reduction comes from data maps (`ArmorDataMaps.DAMAGE_RESISTANCE` per item, `ARMOR_SETS` per set,
`ENTITY_DAMAGE_RESISTANCE` per entity type). Its code layer (`registerItem` / `registerSet`) has no callers. So the reduction is data, and
data can be patched. Steps:

1. **Neutralise** Continuum's reduction: a patch `data/wflib_continuum/value_maps/patches/hbm/item/damage_resistance.json` with
   `{"replace": true}` empties the per-item map. The per-set map also carries things we must keep (set membership for hazmat and digamma
   checks), so it cannot be replaced wholesale; the bridge instead serves a **derived patch** generated at load from Continuum's own
   `armor_sets` (same entries, `resistance` removed) through an `AddPackFindersEvent` pack, so it follows Continuum updates with no copy to
   maintain. Entity innate resistance is left alone at first (WFLib's resolver reads it already).
2. **Give the pieces WFLib values.** WFLib's `ArmorProfiles.author(itemId, ArmorSpec)` is code only today; add a **data loader**
   (`data/<ns>/wflib/armor_profiles/*.json`: item or tag, preset or explicit DT/DR per protection type, condition, inserts). That is
   independently useful (any mod's armour can be tuned by a datapack) and it is where the bridge's table of Continuum pieces lives. WFLib
   already derives a profile from vanilla attributes for armour it does not know, so there is no unprotected gap while the table is built.
   Continuum's numbers (readable through `byId()`) seed a **first draft** of the table and are then tuned; they are not the final values.
3. **Radiation resistance** (`RADIATION_RESISTANCE`) is replaced the same way: WFLib's armour `RADIATION` row decides the dose multiplier.
4. What stays Continuum's on purpose: `ArmorFSB` set effects, power-armour battery drain, armour modules (`ArmorModHandler`), gas-mask
   filter logic. None of it is DT/DR. **To validate:** that none of these re-reads the neutralised maps.
5. `IResistanceProvider` (Continuum entities that add DT/DR dynamically) is called by WFLib's resolver as a `DynamicResistance` source.

Result: one reducer per hit, WFLib's, with Continuum armour behaving as authored WFLib armour (condition, wear, layers, inserts), and no
mixin.

### 3.2 Doors: WFLib's system
**Verified:** the two rosters are the same fourteen `DoorDecl` doors plus the blast door under the same names (`vault_door`,
`transition_seal`, `silo_hatch`, `qe_containment`, `water_door`, ...), with the same dimensions in `DoorDecl` and `DoorType`.
Continuum's `BlockDoorGeneric` is a multiblock machine with a lockable block entity; WFLib's `DoorBlock` is its own multiblock with
placeholders, redstone-and-key opening and a GemRender model.

Three levels, each usable alone, in the order I would do them:

1. **Recipes and items (data only).** Remove Continuum's door recipes (patch-style recipe removal), add recipes that produce the WFLib door
   with the same materials, hide the Continuum door items from the tabs. New doors are WFLib's. Worlds and structures keep their old ones.
2. **Substitute the blocks.** `Substitutions.block("wflib_continuum", "hbm:vault_door", ...)` for each door with a `BlockDoorGeneric`
   subclass that delegates to WFLib's door logic and model, plus `Substitutions.entry(..., "block_entity_type", ...)` for the door entity.
   The id, loot, tags, recipes and every structure that places a Continuum door then get WFLib's behaviour. This is the part that needs care:
   the substitute must satisfy Continuum's class contract (`BlockMultiblockMachine`, `IBomb`, `IToolable`, `IRadResistantBlock`) while doing
   WFLib's job, so it is an adapter, not a rewrite. **To validate:** how many of Continuum's own callers cast to `TileEntityDoorGeneric`.
3. **Key and lock parity.** Continuum's keys and padlocks (`TileEntityLockableBase`) map to WFLib's `DoorKeyItem` / `DoorLockItem`; a
   conversion recipe and, for placed locks, a migration on load.

`TileEntityDoorGeneric.MOVED` (notify) is the hook to keep Continuum's advancements and machines that listen to doors informed.

### 3.3 Glyphids: WFLib's colony replaces Continuum's
**Verified inventory.** Continuum: nine entity types (`glyphid`, `behemoth`, `brenda`, `scout`, `nuclear`, `bombardier`, `digger`,
`brawler`, `blaster`) on its own AI and `GlyphidStats`; the hive world feature (`GlyphidHiveFeature`, gated by `MobConfig.enableHives`);
`BlockGlyphid` hive blocks and `BlockGlyphidSpawner`; items `glyphid_meat`, `glyphid_meat_grilled`, `glyphid_gland`, `egg_glyphid`;
33 lang keys; **no custom sounds** (it plays vanilla zombie sounds); no data-driven loot of its own (`Entities.noLoot`, drops in code).
WFLib: the same nine castes under `colony` with the off-world simulation, nests, spawner blocks, and entity textures for all nine plus
infestation (the same set of names as Continuum's, to compare pixel for pixel), an OBJ model of the same name.

So **almost nothing needs bringing over as files**, because WFLib already has the entity assets. What needs doing:

| Need | How |
|---|---|
| Continuum glyphids stop appearing naturally | `Spawns.CHECKS` veto for the `hbm:glyphid*` entity types (replaceable, no config poke); hives off via the world-gen key or an upstream config override |
| WFLib's colonies take their place in world gen | WFLib has a colony seeder (`ColonySeeder`); add a pass that places them where Continuum's hive feature would (biome and rarity from its `ModBiomeModifiers` entry) |
| Drops Continuum players expect (meat, gland, eggs) | a WFLib entity loot table with `neoforge:conditions` mod_loaded `hbm` dropping `hbm:glyphid_meat` and `glyphid_gland`, same odds as Continuum's drop code (to read before writing it) |
| Items and blocks that spawn Continuum glyphids (`egg_glyphid`, `glyphid_spawner`, hive blocks) | keep as plain items and decor, or substitute the spawner block with a subclass that spawns WFLib's caste (same `Substitutions` mechanism as doors) |
| Hive block looks (`glyphid_base*`, eggs, stalactites, stalagmites: about 16 textures, mostly Continuum-only) | referenced by id from WFLib's nest generator when `hbm` is present; **only copy files** if you want WFLib nests to use that look without Continuum, which needs the licences checked first (Continuum is LGPL-3.0) and which I would not do by default |
| Stats | Continuum's `GlyphidStats` (two profiles, `70K` and `NT`) are NTM's numbers and WFLib has its own `GlyphidStats`, probably the same table: a parity test, no code (to validate) |
| Other Continuum code that references glyphids (`BlockAllocatorGlyphidDig` in explosions, `LootGenerator`) | keep working, they key off the Continuum entity types that no longer spawn |

### 3.4 Hazards, hazmat, items
**Verified:** Continuum's item hazards are the data map `hbm:hazards` (`HazardDataMaps.HAZARDS`, plus `HAZARD_MULTIPLIER`, `CELL_HAZARDS`
and per-block `RADIATION_EMISSION`), with modifiers and transformers in code for RBMK, RTG, fuel depletion and Zirnox.
WFLib ships no hazard rows (`wflib:hazard` is empty by design).

* **Bridge**: at server start and on `HAZARDS.changed`, convert `HAZARDS.byId()` / `byTag()` into `wflib:hazard` rows, so WFLib's inventory
  scan, Geiger HUD and PDA see Continuum's items. `RADIATION_EMISSION` becomes `wflib:rad_source` rows.
* **One effect, not two.** WFLib applies the dose for players; Continuum is told to skip radiation on them through the replaceable
  **`ContaminationUtil.RAD_IMMUNE`** hook (verdict `ALLOW` for players). Item hazards that are not radiation (toxic, explosive, hot,
  hydroactive...) stay Continuum's. **To validate:** that `RAD_IMMUNE` gates the whole inventory-radiation path and not only the ambient one.
* **Modifiers that need Continuum items** (RBMK, RTG, fuel depletion) stay in Continuum; their result reaches WFLib through
  `HazardTypeRadiation.STACK_RADIATION` (a notify hook, so listeners can contribute) and the per-stack value WFLib reads.
* **Hazmat and protection classes**: `ARMOR` protection classes, Faraday and digamma sets fill the WFLib item tags
  (`wflib:hazmat`, `hazmat_advanced`, `faraday`, `digamma_protection`) from `ArmorDataMaps.PROTECTION_CLASSES`, `FARADAY_ARMOR`, `ARMOR_SETS`.
* Gaps WFLib's own `radiation.md` lists (RBMK, RTG, dosimeter, digamma, AE2) are exactly what the bridge fills, by reading Continuum.

### 3.5 Items, blocks and weights (cheap, high value)
From Continuum's registries and data maps at server start: `ItemMasses` entries, block penetration and hardness rows (from blast
resistance), the `wflib:rad_resistant` tag from `IRadResistantBlock` implementers, `wflib:geiger_counter` tag members. All data, no seams.

### 3.6 Everything else, later
`ILookOverlay.PROVIDERS` lets WFLib's look-at probe show Continuum block info (and the reverse); `Environment` hooks let WFLib's `orbital`
answer pressure, breathing and sky ownership for Continuum machines; `IRadarDetectableNT` entities appear in WFLib radar; Continuum warheads
registered as WFLib warheads (the SPI exists); a `SoldierWeapon` for Continuum guns; drone service of `IDroneContainer`.

## 4. Seam inventory

| Need | Seam | Status |
|---|---|---|
| Replace a Continuum block, item, entity or block-entity type | `Substitutions` | exists |
| Neutralise or change Continuum data | data-map patches, derived pack | exists |
| Read Continuum data | `DataMap.byId()`, `byTag()`, `changed` | exists |
| Veto Continuum natural spawns | `Spawns.CHECKS` | exists |
| Make Continuum skip radiation for an entity | `ContaminationUtil.RAD_IMMUNE` | exists |
| Answer environment questions | `Environment.*` hooks | exists |
| React to blasts, bombs, missiles, irradiation | `HbmEvents`, `Environment.NUCLEAR_BLAST` | exists (notify only) |
| Swap the MK5 ray | `createRay` factory or hook | **needs one additive upstream change** |
| Change Continuum config from an addon | `CommonConfig.override` | **needs one additive upstream change** |
| Skip Continuum DT/DR | not needed | solved by data |

## 5. Still-open domains, with my leaning

### 5.1 Radiation field
Both run the same algorithm. Without mixins the clean option is **Continuum's field stays the owner** when `hbm` is present, and WFLib's
radiation system reads and writes through an adapter to it (WFLib keeps what it does better and which sits on top of the field: armour wear
by dose, artefact soak, the GemRender Geiger HUD, the PDA, `wflib:hazard` rows, fog if wanted). The alternative, WFLib's field as owner,
needs Continuum's own sources (reactors, bombs, hazard blocks calling `ChunkRadiationManager.proxy`) redirected into it, which is a mixin or
an upstream seam. Needs your call; the adapter option costs one interface in WFLib's `radiation` and keeps saves untouched.

### 5.2 Nukes
`EntityNukeExplosionMK5` exists in both and the maths should be near identical. With the `createRay` seam (or a `tick()` override in a
substituted subclass, which duplicates Continuum's logic and I would avoid), Continuum's bombs run WFLib's `ExplosionNukeRayParallelized`.
First a **parity GameTest** (same bomb and seed, compare blocks destroyed, crater profile, fallout strength). `HbmEvents.BOMB_DETONATED`
and `NUCLEAR_BLAST` keep firing from Continuum's entity, so its advancements and satellites keep working.

## 6. Phases

| Phase | Deliverable |
|---|---|
| 0 | Test instance with WFLib, Golem Core and Continuum (1.21.1 NeoForge). **Conflict log** (double-applied events, duplicate HUD/commands). A `compat` GameTest module that runs only when all three load. Confirm the "to validate" items above. |
| 1 | Bridge addon skeleton on `ModEntries`; switchboard group "NTM: Continuum"; **armour**: patches, derived set pack, WFLib armour-profile data loader, first-draft table. The single-reduction GameTest. |
| 2 | **Data bridges**: hazard and emission rows, hazmat tags, masses, penetration, rad-resistant and Geiger tags; drift test. **Glyphids**: spawn veto, loot, colony placement. |
| 3 | **Doors**: recipes and items, then block and block-entity substitution, then keys. **Radiation** adapter. |
| 4 | **Nukes** after the parity test and the upstream seam. Later bridges (3.6). |

Phase 0 and the armour data loader (useful on its own) can start now.

## 7. Risks

* **Substitution is a contract.** A substitute must keep working for every Continuum caller of the original class (casts, interfaces).
  Doors are the largest case; each substitution gets a GameTest that exercises Continuum's own callers.
* **Derived pack timing.** The `armor_sets` derivation must run before Golem applies patches; to validate with Golem's `DataFiles.stackedFolder`.
* **Writing another mod's static fields** is the weakest tool in the list; use it only until the config override exists.
* **Drift between two Warfactory teams.** Parity tests (stats, doors, MK5) are the guard.
* **Performance.** Row generation happens once per reload and is cached; no per-tick map lookups.

## 8. What I need from you

1. **Packaging:** a separate bridge addon jar in this repo (my recommendation) or the code inside WFLib behind a soft dependency.
2. **Radiation:** Continuum's field owns with a WFLib adapter (my leaning), or WFLib's field owns and we ask for a redirect seam.
3. **Nukes:** OK to ask for the `createRay` seam, or leave Continuum's MK5 alone for now.
4. **Upstream changes:** are two small additive changes to Continuum (`createRay`, `CommonConfig.override`) acceptable to make?
5. **Doors level:** stop at recipes and items first, or go to full block substitution from the start.
6. **Glyphid hive look:** reference Continuum's block textures only when it is present (my recommendation), or copy them into WFLib.
