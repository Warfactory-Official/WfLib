# Barrels

Barrel heat, jams, cook-off, wear, quick-change spares (`barrel/`). Gun state + fire path = consumers (MA guns, CA vehicle weapons).

## API
| type | use |
|---|---|
| `BarrelSpec` | per-gun model, `CODEC` = pack JSON (MA gun `barrel`, CA weapon `barrel`); `type` present => quick-change; `variants` {id: {length m, velocity}} = other barrels of the type |
| `BarrelState` | `(temp, at, wear)`; `at(spec, now)` lazy cooling + wear, `fired`, `spread`, `jamChance`, `cookOffRate`, `glow`, `haze`, `happens` |
| `BarrelTypes` | type -> name + spec; consumers register at content load, both sides |
| `Barrels` | `wflib:spare_barrel` component (type, variant, state) + item (`WFLibContent.spareBarrel` request), `coolest`, `exchange` (in-place swap, variant both ways), `name` (variant lang `barrel_type.<ns>.<path>.<variant>`) |
| `client.BarrelGlow` | `submit(pos, glow)` per frame -> point light |

## Model
- `T(t) = 20 + (T0 - 20) e^(-t/tau)`; per round `+perShot`, cap `max`. Game ticks => no ticking, stacks cool anywhere.
- Wear: `dw/dt = (T - wear.from) / (max - wear.from) / wear.life`, integrated closed-form inside `at`; permanent (spare swap only).
- Spread `= spread(T) + wear * wear.spread` deg half-angle, added to the gun's cone.
- Jam per round `= jam.base + jam.ramp(T) + wear * wear.jam`.
- Cook-off: hazard `cookOff(T)` /s on a chambered round; open bolt never (consumer).

| field | default |
|---|---|
| `perShot` / `tau` / `max` | 2 degC / 120 s / 800 degC |
| `spread` | 150 -> 600 degC, +0.35 deg |
| `jam` | base 0; 300 -> 700 degC, 0.03 |
| `cookOff` | 320 -> 600 degC, 0.6 /s |
| `wear` | from 450 degC, life 600 s, +0.3 deg, +0.02 jam |
| `swap` | self 6 s, coop 2.5 s |
| `variants` | none; `velocity` 1 |

## Rulings
- Spare = physical item carrying its own heat + wear; swap exchanges in place (no free slot needed); coolest, then least worn picked.
- Spare spec = its type's, not the gun's.

## Gaps
- Glow = light only; mesh does not emit.
- No spare barrel recipe (creative / give / pack recipes).
