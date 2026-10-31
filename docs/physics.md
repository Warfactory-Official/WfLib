# Physics

System `physics`: switchable, default on, no `requires`; `geom` is bare mandatory. Dependents consume `physics.api` only.

## Surface

| area | api |
|---|---|
| body | `PhysicsBody`, `BodyState`, `StepResult`, `SpringRows`, `Coupling`, `DeckView`, `PhysicsStep` (register/predict/warm/stats) |
| hulls | `ConvexHull`, `HullPiece`, `HullTag`, `CompoundBody`, `SweptHull`, `ConvexSweep`, `BoxBuffer`, `OrientedBoxes` |
| terrain | `ChunkCollisionCache`, `SectionSource`, `SectionCollision`, `SectionScan`, `SurfaceSection`, `SurfaceBake`, `DriveSurface`, `CellTops`, `Narrowphase` |
| providers | `CollisionProvider`, `CollisionProviders.PROVIDERS` (`OrderedRegistry`; ids `create`, `sable`, `gametest_plank` are CA's) |
| deck | `DeckBody`, `DeckCarrier`, `DeckRider`, `DeckAttachment`, `DeckClip`, `DeckCollision`, `DeckEffects`, `DeckFrame`, `DeckSnapshot`, `DeckRejoin`, `ServerDeckRejoin`, `CarrierDecks`, `CarrierLink`, `VehicleParenting`, `TickClock` |
| prediction | `Prediction<I>`, `Predictable<I>` |
| config | `PhysicsSettings`, `DeckSettings`: `current()` / `override(x)`; `null` clears |
| event | `api.event.PhysicsLevelUnloadEvent` |

## Tick slots (`ServerTicks.LEVEL_POST`)

| slot | order | owner |
|---|---:|---|
| `PHYSICS` | 2000 | `RigidStep.tick` |
| `COUPLING` | 1990 | dependent tow solve |
| `CARRIED` | 1980 | dependent carried riders |
| `PREDICTION` | 100 | `PredictionAcks.sendDue`; last by design |

- Dependent tick work needing "after the step" MUST use a slot below `PHYSICS`; `ServerTicks.Listeners` stay NORMAL (HIGHEST reorders wflib's NORMAL Post listeners).
- One `PhysicsSettings.current()` capture per step; workers read only that instance.
- `asyncStep` on/off: results bit-equal (golden `StepGameTest`); results apply in tick order.
- `nativeNarrowphase` bit-equal to Java path (`NarrowParityTest` in CA asserts jar natives load); missing platform lib => Java fallback.

## Deck

- NBT key `wflib_deck` (player data, rejoin capture). No legacy reader.
- Rider/push eligibility: `instanceof PhysicsBody` / `DeckBody`, never a dependent class.
- `DeckCarrier` = mount pose contract only; `harnessedVehicles` is dependent-side (`HarnessCarrier`).
- Mixins `mixin.physics.{MixinEntityDeck,MixinLivingEntityDeck,MixinPlayerDeck,MixinServerGamePacketListenerImplDeck,MixinLevelChunkCollision}` guard on `PhysicsSystem.active()`; physics off => vanilla.

## Prediction

- `Prediction<I>`: history 64, idle 2 ticks; counters `matched()/corrected()/replayed()`.
- Server sends `PredictionState` only when `Prediction.due`.
- Slots on `Predictable`: `newInput`, `copyInput`/`applyInput`, `afterReplayStep`.

## Config / wire

| item | value |
|---|---|
| file | `wflib-physics.toml`: `asyncStep` true, `nativeNarrowphase` true, `threads` 0 (cores/2), `deckParenting` true, `carrierDecks` true |
| registrar | `physics-3`: `wflib:deck_rejoin`, `wflib:prediction_state` (both play-to-client) |
| threads | `wflib-physics-N` |
| props | `wflib.physics.{debug,debugAll,substeps,iterations}` |
| natives | `native/{jni,narrow}.zig` -> `wfnarrow[_v3]`, `buildNatives` (8 targets), zig 0.16 on PATH or `-Pzig`; JNI `Java_com_wf_wflib_physics_rigid_NarrowNative_*` |

## Traps

- Engine and vehicle gameplay keys split: engine keys here, vehicle keys (suspension, tow, ...) stay in the dependent.
- Pool shutdown: level unload + `ServerStoppedEvent` (5 s).
- Gametests needing ticking chunks: `ForcedChunks.set` + `ForcedChunks.ticking`.
- `collisionTree()` package-private; `Narrowphase.nativeLoaded()` is the api probe.

## Tests

- `PhysicsGoldenTest`, `PredictionGoldenTest`, `DeckGameTest`, `StepGameTest`, `PhysicsGameTest`: hashes/counters bit-equal to the pre-move CA copy. Re-record only on a deliberate solver change.
- Vehicle-level hashes live in CA (`StepDeterminismGameTest`).
