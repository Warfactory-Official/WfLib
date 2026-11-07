# Soldiers

System id `soldier` (`com.wf.wflib.soldier`, api in `soldier.api`). A soldier is a **server player with no client**
(`SoldierPlayer`, in the player list, survival) driven by a squad AI: vision with a cone and an awareness value that builds
over time, gunshot hearing, cover and peeking, bounding overwatch, suppression, grenades and smoke, grenade avoidance, first
aid and revival. The guns, grenades and medicine belong to other mods and are plugged in through three SPIs.

## Plugging in

| SPI | supplied by | gives the AI |
|---|---|---|
| `SoldierWeapon` | the gun mod | ammunition, reload/unjam/cancel, one shot through the player's own fire path, arming |
| `SoldierThrowables` | the gun/grenade mod | frag vs smoke, launch speed, pin/throw |
| `SoldierMedical` | the medical mod (optional) | condition, treatment plan, CPR, kit, give-up |

Register with `SoldierServices.weapon(..)` etc. during mod construction. A gun mod reports shots with
`SoldierControl.shotFired(shooter, origin)` (soldiers hear them). `Soldiers.spawn(level, at, yaw, SoldierSpec, owner)` puts one
in the world; `SoldierServices.side(name, spec)` names a side the `/soldiers spawn <side> <n> [radius]` command can field (also `/soldiers clear`, `profile [reset]`, `check`, `routebench`, `async on|off`; permission level 2). `SoldierControl` carries orders, squad objectives and telemetry for scripted tooling (`filmingGate` lets a tool
run Carpet-driven players the same AI drives).

How a soldier works with no connection: `SoldierConnection` is a dead wire with an embedded channel (set by
`mixin.soldier.ConnectionMixin`), `ServerCommonPacketListenerImplMixin` drops every packet sent to it, `PlayerListMixin` keeps
its join and death out of chat, and `SoldierPlayer.markPlaced` swaps its container menu for one that never syncs (the inventory
sync was the largest single per-tick cost of a bot).

## Why it is slow today, and how to make it asynchronous

From a spark profile of Carpet-driven bots (read from a screenshot, so the bot count is not known): one fake player's own tick is about 8.5% of the server thread before any AI runs
(`ServerPlayer.tick` 5%, of which container sync 4.9% with weight-tracking mixins, `doTick` 3.4%, ragdoll ticking). On top of that the
AI itself runs on the server thread: every tick each soldier does line-of-sight rays to every enemy, and every ~20 ticks a cover search
(a few thousand rays) and a danger-weighted A* (up to 5000 nodes, one exposure test per node).

The glyphid swarm (`colony`) already solved the same problem, and the same machinery is the way to do it here:

| glyphid | what it gives soldiers |
|---|---|
| `GlyphidBrain.plan(snapshot, mind)` is a pure function; `GlyphidBody` reads the world into a `GlyphidSnapshot` and carries the `GlyphidPlan` back out | split the AI into **snapshot (tick thread) -> brain (worker) -> plan applied (tick thread)** |
| `SimKind.prepare` (tick thread, level Pre) -> `advance` (worker, overlaps the vanilla level tick) -> `resolve` (tick thread, level Post); `SimTier` hands the list to the worker while a pass runs and merges an inbox at the join | run the soldier brain as an async `SimKind` with the same ownership rules |
| `SimWorldPrefetch`: the worker never touches the world; what it needs is recorded as *wanted* and read on the tick thread a tick ahead; a missing read is "unknown", never a guess | a block-section snapshot the worker queries (below) |
| `WorldThread.assertOff/assertOn` | fail loudly when a world read leaks onto a worker |
| shared flow fields per destination instead of one A* per bug | one flow field per squad objective and floor, not one A* per soldier |
| tiers: entity when something can interact, record otherwise, hysteresis, per-tick promote/demote caps | a soldier far from every real player becomes a record |
| `SwarmBench` / `SwarmProfiler` | a `soldierbench` so numbers, not guesses, choose what to optimise |

### Design

1. **Snapshot** (tick thread, level Pre): one immutable `SoldierFrame` per level per tick: soldiers and real players near any soldier (position,
   eye, velocity, crouch/prone, alive, team, downed), live grenades and smoke clouds. Per soldier: own pose, ammunition, condition, current
   action state. Cost is O(entities), shared by all soldiers.
2. **World view for the worker**: `SoldierWorld`, backed by per-section copies of the block palette (`PalettedContainer.copy()` is cheap and safe
   to read concurrently) plus a `BlockState -> collision class` table (empty / low / thin / solid, filled on the tick thread, read-only after).
   Sections are refilled from a *wanted* set with a per-tick budget; a block-change hook bumps a section version so edits are seen within a tick or two.
   Line of sight is a voxel traversal over this view (smoke cells included), not `level.clip`.
3. **Brain** (worker, pure): perception (awareness values), threat memory, cover search, route search, peek cycle, firing decisions, grenade and
   medical choices. It owns each soldier's `Mind` for the pass and returns a `SoldierPlan`: movement keys, aim point, posture/lean/ADS request,
   trigger/reload/unjam intent, throw, treatment. No Minecraft types that read the world; testable on a synthetic box world.
4. **Body** (tick thread, level Post): applies plans: set the player's input, rotations, stance request, call `SoldierWeapon.fire/reload`, start the
   throw, start the treatment. Decisions are one tick old, which is nothing against 3-10 tick reaction times. The real shot still goes through the gun
   mod's server fire path, so ballistics and attribution are unchanged.
5. **Budgets**: searches become jobs with a node/ray budget per tick (resumable A*), results arrive next tick, the soldier keeps its previous plan
   meanwhile. Idle soldiers think at 1/10 rate staggered by id; soldiers in contact at full rate. Squad objectives share a flow field.
6. **Record tier** (the part that makes it "entirely" asynchronous): a soldier farther than ~96 blocks from every real player, with hysteresis,
   is saved (inventory, medical attachment, orders) as a record and its `SoldierPlayer` removed (no player tick, no chunk loading). Records hold
   position or follow the flow field at walking speed; the worker advances them. When a player comes near, the soldier is placed back from the
   record. Records are `persistent()`, which also fixes "soldiers vanish when the server stops". Bodies are therefore bounded by the number near players.

### Status

- **Baseline** (`/soldiers profile`, 12v12 in the city, ~7-11 soldiers alive): the AI handler cost 2-4 ms per tick (p95 6-12 ms) plus the soldier
  bodies' own player tick 1.3-1.7 ms (about 0.15-0.25 ms each). Spikes were the route searches: a live 30-block search with an exposure test per node
  costs 27-50 ms on the tick thread (`/soldiers routebench`), 94-327 ms worst tick in a fight.
- **Done, measured:** route searches run on a worker over a block copy (`SoldierSections`, `Terrain`/`SnapshotTerrain`, `SoldierJobs`).
  The copy agrees with the live world on walkability for every one of 16,000 sampled cells and on sight for 96-99.5% of sampled lines
  (`/soldiers check`); the same search over the copy is 2-3x faster; handing one to a worker costs the tick 0.07-0.17 ms. In the fight
  benchmark `steer` went 1.8 -> 0.017 ms per tick, `route` 1.9 -> 0.02 ms, worst tick 94 -> 16 ms. Also: at most 3 searches start per tick, treatment plans and
  sight checks are cached per tick. `/soldiers async off` switches back to the live search for comparison.
- **Next by cost:** perception (about 1.1 ms/tick, 100-540 rays), the soldier bodies (1.3-1.7 ms, only a record tier removes that), cover searches (spikes).

### Order of work

0. Done: engine moved here with the SPIs; inert container menu.
1. Done: `/soldiers profile` with per-phase sections, `routebench`, `check`.
2. Partly done: the block copy and the route search over it; sight, cover and perception still read the live level.
3. Brain/Body split of `SoldierAi`, `SoldierKind` with `async() = true`.
4. Flow fields, budgeted searches, idle rate.
5. Record tier and persistence.

Players cannot be glyphid-style records while they are bodies: everything near a player stays a real `ServerPlayer`, so the per-body cost
(item 0) still matters and is why the inert menu comes first.
