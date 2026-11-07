# Ragdoll

System `ragdoll`, requires `physics`. API `ragdoll.api.Ragdolls`. Trigger: WFMedical `MedicalStance.downed`. Commands: `/wflib ragdoll <player> on|off|blast <m/s>|hard`.

## Design
- Own XPBD solver (`sim.Ragdoll`), no MC types, no natives. 8 substeps/tick, 1 iteration. `RigidSolver` REJECTED: ball joints only, inertia fixed per tick.
- 11 bodies = AHF `LocalRig.Slot` order; boxes = rig boxes from `HumanoidRig.baseSpec`.
- Server authority: rewind, hits, medical read the sim pose. Client authority FORBIDDEN (Ragdollified: conflicts with rig).
- Wire + draw = torso + 11 rotations; `RagdollPose.channels` FK => joints never open; knee / elbow / waist twist dropped (mesh has none). Sim position only for the torso.
- Player entity: xz torso, y lowest body. Client moves dropped (`MixinServerGamePacketListenerRagdoll`), `Player.travel` off both sides.
- Inert body (any `LivingEntity`, [corpses](corpses.md)): `Ragdolls.handOff` / `restore` / `state`; never stands, no fall damage.

## Carry
- `key.wflib.carry` on a running body (corpse or ragdolled player) -> fireman carry; carry system restrictions + walk x0.7. Drop key lets go, momentum kept.
- Torso = kinematic grip (`Ragdoll.grip`, infinite mass, lerped over substeps); rest hangs. Carrier box (+-0.15, feet .. +1.15) in the world boxes after the 12 tick lift: limbs hang front / back, not through. Box at bbox width / 1.34 => legs propped level.
- Hold ends: carrier dead / gone / other level / ragdolled / > 3 m after lift; body stands, ends or is handed off (death while carried => corpse drops). `Carry.BODY` attachment polls `Ragdolls.carrier`.
- Sim-owned bodies neither push nor are pushed (`MixinLivingEntityRagdoll`): carried box sits on the carrier.

## Joints
- Soft: last 30 % of range: spring + damping ramp. Neutral: weak pull to relaxed posture (Mount 2003). Both below gravity torque.
- Knee, elbow: hinge axis alignment. Swing z + twist removal apart DIVERGES past ~100 deg flexion (coupled).
- Limit table: `Skeleton`. No reference: no studied mod simulates knee, elbow or spine (`ref/ragdoll-mods/FINDINGS.md`).

## Traps
- Release: `stand` BEFORE `AhfStance.setDowned(false)`: entity at body low point, 1 mm in floor => no room => forced PRONE, stuck.
- `level.hasChunksAt` +-3 blocks false => sim frozen. Gametest lane on a chunk border with one forced chunk never settles.
- Sleep wake: cache epoch is level-wide => compare box hash around the body.
- `ChunkMap.move` NPE on an unlisted FakePlayer.

## Measured (2026-11-07, rubble, `wf_ragdoll_cost`)
| case | mspt | fps | |
|---|---:|---:|---|
| 24 idle bots | 4.51 | 993 | |
| 24 awake | 6.86 | 960 | 140 us/step |
| 24 asleep | 4.25 | 1001 | |

- Knee stop, worst rate drop deg/tick^2: soft 7.4, hard 17.7 (`RagdollTest`).
- Wire: ~130 B/tick per viewer awake (computed), 0 asleep.

## Gaps
- No collision with vehicles, decks, Create / Sable providers, other entities.
- Round shove + `causeFallDamage` path not verified in game; explosion throw needs `DamageSource.getSourcePosition` != null (TNT seen, wflib blasts unverified).
- Stand-up = 14 tick pose blend, no clip. First person camera: no block clip.
- Carried body drawn from the server pose: lags the carrier's predicted position by ping + 1 tick.
