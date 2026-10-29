# Radiation + hazards

Port of NTM NEXT `handler/radiation`, `hazard`, `extprop/HbmLivingProps`, `util/ContaminationUtil`,
`EntityEffectHandler` (hazard arms), `HbmPotion` (radiation effects), `/hbmrad`. Upstream:
`~/projects/ntm-next-workspace/ntm-next/common/src/main/java/com/hbm/`. Only variations from it below.

| package | upstream |
|---|---|
| `radiation` | `handler/radiation` (Java sweep only) |
| `hazard` | `hazard`, `HbmLivingProps` -> `LivingHazardProps`, `EntityEffectHandler` -> `HazardEntityEffects` |

## Divergences

| area | NTM NEXT | here |
|---|---|---|
| backend | native `radsim` (C++/SIMD) + Java fallback | Java sweep only; `RadsimBackend`, `lib/internal/natives` absent |
| MC | 26.2 | 1.21.1; sidecar read rides `IOWorker.lambda$loadAsync$4`, carried to `LevelChunk` via `ChunkDataEvent.Load` (FULL tags only) |
| Unsafe | `jdk.internal.misc.Unsafe` via trusted lookup | `VarHandle`; Vector API module load via `sun.misc.Unsafe` read of `IMPL_LOOKUP` (`-Dwflib.vector=false` => scalar) |
| fog/destroy queues | lock-free XADD MPSC | `LongEventQueue` (monitor); offers rare |
| `TLPool` shared tier | MPMC array queue | `ConcurrentLinkedQueue` |
| start hook | `serverStarting` | `ServerAboutToStartEvent`: 1.21.1 `ServerStartingEvent` fires after spawn chunks load |
| sidecar | `<dim>/hbm/radiation`, type `hbm_radiation` | `<dim>/wflib/radiation`, `wflib_radiation` |
| registries | `hbm:*` | `wflib:hazard` (synced), `wflib:radiation_settings`, `wflib:rad_diffusivity`, `wflib:rad_source` |
| item rows | `HazardRegistry.registerItems` (NTM fuels/waste) | none; packs ship `wflib:hazard`, code calls `HazardSystem.register` |
| shield blocks | code `markRadResistant` | + block tag `#wflib:rad_resistant`, resolved at server start |
| suits / masks / reacher | NTM armour classes, gas-mask filters | item tags `wflib:hazmat`, `hazmat_advanced`, `faraday`, `digamma_protection` (full set), `hazard_protection/<class>` (head), `reacher`, `geiger_counter` |
| dose mult | `10^-sum(HazmatRegistry resistance)` | wflib armour `RADIATION` DR pass-through x `10^-0.2` under Rad-X; players only |
| armour wear | none | `RADIATION` exposure tier = incident RAD/s x `armorWearPerRad`; filter wear -> `CHEMICAL` exposure |
| surface decay | grass -> `waste_earth`, leaves -> `waste_leaves` | `RadiationWorldHandler.wasteEarth` (dirt) / `wasteLeaves` (null = air); pack overrides |
| entity conversions | + nuclear creeper, duck -> quackos | vanilla only; `Mob.convertTo` |
| sickness FX | NTM vomit/sweat payloads | vanilla block particles |
| gas toast | `PlayerInformPayload` | action bar |
| fog | effectNT `RadFog`, 100-block send | `wflib:rad_fog`; count-0 packet, `ya < 0` = cluster marker |
| HUD | `RadiationHudRenderer` dose bar + rate icons | `GeigerHud`: GemRender LCD bottom-left, rate = synced `radBuf`, dose, log bar; gate = inventory has `#wflib:geiger_counter` |
| container trafo | box / bag / toolbox / crates | any `container` / `bundle_contents` stack; box sqrt -> `#wflib:rad_containment`; bag x2, sealed crates absent |
| MK5 fallout | `EntityFalloutRain`: terrain -> fallout / waste blocks | `Fallout` per-chunk ground source: `falloutRad*(1-d/R)^2`, `R = 2.5r`, `falloutHalfLifeSeconds`; loaded chunk surface pocket driven to it each second; no blocks, no force-load |
| artefacts | none | worn artefacts' `radiation_immunity` soak dose after armour (`Artifacts.shieldDose`), skipped by `RAD_BYPASS` |
| command | `/hbmrad` | `/wfrad` + `get <pos>`, `geiger` |
| config | `ConfigSchema` | `wflib-radiation.toml` |

## Gaps

- No shipped hazard rows, rad sources, diffusivity classes or protective items: data/tag only.
- `HazardTransformerRadiationME` (AE2).
- Modifiers needing NTM items: RBMK, RTG, fuel depletion, decay heat. `HazardModifierLevel` ported.
- `DIGAMMA2` contamination gate (NTM robes) never blocks.
- Advancements (`HbmCriteria` radiation/digamma).
- Crater biome dose, pollution soot in lung disease, balefire/blackfire dose (fire system), MKU, oil, bomb timer.
- Dosimeter / digamma diagnostic items: `ContaminationUtil.print*` callable, no items.
- MK5 `statFacNoRad` / `moreFallout`, `BombConfig.falloutRange`, detonation advancement.
- Client radiation aura FX (`CLIENT_RADIATION_FX`).
- Upstream TODO kept: diffusivity identity edits do not invalidate section diffusivity.
- Unresolved: gametest lane, `incrementRad` into section 0 on the lane load tick -> 49.5 next tick, `0.0` after (stays uniform kind); +10 ticks or section 2 holds. Upstream fixtures prime + wait 3 ticks.
- `markRadSource` / `markRadResistant` after server start: no sweep join, no rebuild of built sections, no unmark (upstream same). Load-time only.
