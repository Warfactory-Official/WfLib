# NTM: Continuum's structures, as reference for WFLib bases

What Continuum's own buildings are made of and how they are put together, read from its NBT files
(`assets/hbm/structures/*.nbt`, legacy 1.7 block names with meta) and its Java components
(`world/gen/component`), and seen placed in the test world with `/place structure hbm:<id>`.

## Inventory

| Structure | Size (x, y, z) | What it is | Main materials (blocks placed) |
|---|---|---|---|
| `military_base` | 47 x 20 x 81 | fenced compound with an apron, snow biomes | asphalt 1323, brick_concrete 365 (+cracked 108, broken 38), platemetal 362, concrete_smooth 345, brick_asbestos 203, concrete_asbestos 200, fence_metal 221, concrete_slab 230, brick_light 124, deco_steel 68, sandbags 60, barbed_wire 87, steel_scaffold 36 |
| silo (`SiloComponent`, in `features`) | 2 x 3 chunks | missile silo: surface yard and shaft | concrete_smooth, concrete_colored, fence_metal, deco_steel posts, barbed_wire, asphalt, hazard-striped `concrete_colored_ext`, bunker doors |
| `radio` (radio_house) | 37 x 25 x 18 | concrete radio station, antennas on the roof | concrete_smooth 2140, brick_concrete 574 (+cracked/broken/mossy 389), iron_bars, slabs |
| `forest_post` | 19 x 18 x 11 | two-storey concrete post | concrete_smooth 502, brick_concrete 58 (+worn 109), brick slabs |
| `labolatory`, `water_pump`, `forestchem` | ~17-21 cube | mostly underground concrete | concrete_smooth, tile_lab, concrete_rebar, steel |
| `factory` | 38 x 41 x 19 | brick hall, gabled slab roof, chimney | cobblestone 1770, bricks 787, stone slabs 674 |
| `dish`, `dead_dish_small` | 29 x 51 x 34 | radar dish on a concrete pad | concrete_smooth 4427, deco_aluminium 886 |
| `repeater_radio`, `broadcaster_tower`, `tower_base` | up to 116 high | steel lattice masts, radome | deco_rusty_steel, deco_steel, deco_titanium, fence_metal, steel_grate |
| `abandoned_arcticstation` | 38 x 14 x 24 | platemetal modules on legs | platemetal 646, steel_scaffold 60 |
| `falloutshelter_house` | 20 x 21 x 11 | wooden house over a reinforced-brick shelter | reinforced_brick 362, planks, spruce stairs |
| ruins a-j | 7-16 square | broken concrete shells | concrete_smooth, concrete_pillar, brick_concrete (half of it worn) |
| bunker (`BunkerComponents`) | procedural | underground rooms and corridors | concrete_smooth_stairs, reinforced_stone, vinyl_tile, fans, bunker doors |

## How the military base is built (the reference for a soldier base)

Seen in the world, not only from the file:

- **Ground**: the whole compound is **asphalt**. No grass inside. White strips (`concrete_colored` white) mark the
  apron like a runway; hazard stripes (`concrete_colored_ext` hazard) mark doors and pads.
- **Perimeter, in layers**: a **low brick-concrete wall** (two high), **chain-link** (`fence_metal`) on it,
  **concertina wire** (`barbed_wire`) on angled steel outriggers (`steel_poles`) above that, **dark steel posts** at the
  corners. Reads as one deliberate barrier, not a castle wall.
- **Gate**: a **steel gatehouse frame** (`deco_steel`, a hazard-striped header over a dark opening) in the wall, a
  **red-and-white striped pole** beside it. NTM does use red and white, but only for that pole.
- **Towers, in pairs at the gate**: **steel lattice stilt towers** (`steel_scaffold` legs, a platemetal cab with
  windows, a ladder) on the outer side; **concrete lookout towers** (brick-light shaft, open lookout under an
  overhanging slab roof) inside. Never one tower on its own.
- **Buildings**: low (3-5 high) flat-roofed boxes with a parapet, of **brick_concrete** or **brick_light**, roofs of
  **concrete_slab**; one **Quonset hut** (a barrel roof stepped in `concrete_smooth`) as the big hall; a **platemetal**
  shed. Few buildings, set **against the perimeter**, the middle left open.
- **Yard**: **sandbag** lines and an octagonal sandbag ring in the open middle, crates and drums.
- **Wear**: about a third of the brick-concrete is the cracked or broken variant of the same block. Wear stays inside
  one family (brick_concrete, cracked, broken, mossy); it never mixes materials.
- **Scale**: compact. A fenced square of about 35, an apron of the same again outside it.

The silo's surface follows the same rules: an asphalt yard, fence and wire round it, the hatch inside a
**hazard-striped rectangle**, concrete-smooth buildings with steel posts.

## The rules NTM follows

1. **One family per building.** A building is brick-concrete, or concrete-smooth, or platemetal, or brick; trim comes
   from the same family (its slab, its stairs, its pillar). Steel is detail (posts, grates, pipes, scaffold).
2. **Hard ground.** Military and industrial sites stand on asphalt or concrete, not grass.
3. **Layered, low perimeters** (wall + fence + wire), so the site looks guarded but stays open to the eye.
4. **Towers in pairs at gates**, lattice or concrete, with open lookouts and an overhanging roof.
5. **Colour only as signage**: hazard stripes on pads, doors and headers; red and white on a barrier pole;
   white lane marks. Never coloured walls.
6. **Silhouettes that say what a building is**: the Quonset barrel roof, the gabled factory with its chimney, the
   radome on a lattice mast, the dish on its pad.
7. **Wear is a variant of the same block**, in moderation.

## Built: four concrete kits (`bridge-continuum`, `site_styles/`)

| Kit | After | Ground | Perimeter | Gate | Towers (gate / corner) | Halls | Extra |
|---|---|---|---|---|---|---|---|
| `ntm_military` | military_base | grass, asphalt roads | layered (brick-concrete + chain-link + wire) | steel frame, hazard header, red/white pole | lattice / concrete lookout | Quonset (concrete) | brick-concrete wear 12% |
| `ntm_silo` | the silo | grass, asphalt roads | chain-link + wire | steel frame, pole | lookout / lookout | flat, reinforced-stone trim | silo pad in place of the helipad |
| `ntm_signal` | radio_house, forest_post | grass, concrete roads | concrete panels | open, lamps in the posts | lookout / lookout | flat | lattice radio mast on the plaza, wear 25% |
| `ntm_depot` | the asbestos buildings | grass, concrete roads | layered (asbestos concrete) | steel frame, pole | lattice / lattice | Quonset (steel) | lattice mast |

A style says how a base is put together in `layout` (`paved`, `perimeter`, `gate`, `gateTowers`, `cornerTowers`, `hall`,
`mast`, `silo`) and names block states in `parts` (`hazard`, `poleA`/`poleB`, `wire`, `lattice`, `cab`, `arch`, `lamp`,
`hatch`), so the plain look and the older kits are unchanged. Full paving (`paved`) exists but no kit uses it: a compound paved in the road's own block hid the roads.

## What this meant for the bases (the proposal the kits follow)

| Now | After NTM |
|---|---|
| grass between buildings inside the wall | the NTM kits pave the whole compound in their ground block (asphalt); lanes only marked |
| wall: concrete panels or hesco; fence: chain-link only | a layered perimeter role: low wall + fence + wire on outriggers, posts at corners |
| gate: open gap with two lamp posts | a steel gate frame with a hazard header, a striped barrier pole, two towers flanking it |
| one tower design (concrete shaft) | gate towers as steel lattice stilt towers, corner towers concrete lookouts, the same pair in every base of the kit |
| all buildings flat boxes | a Quonset hall for barracks or the vehicle shed, flat-roofed boxes for the rest |
| bays and helipad edged in the curb block | pads and bay floors marked with hazard stripes |
| no wear | optional wear inside one family (brick_concrete -> cracked/broken), about 20% |
| (none) | a fortress may carry a silo pad: hazard rectangle round a hatch |

## Tools

Renderers in the session scratchpad (`ntm/`): `render.py` (plan, floor slices, elevations, material counts),
`layers.py` (every horizontal layer), `iso.py` (isometric view), reading the legacy NBT with a small parser.
