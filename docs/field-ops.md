# Field ops: where contracts happen, and what stands there

A contract used to drop a handful of soldiers on a random flat spot. It now picks ground on purpose, builds a base on it, and
posts the force through the base. Everything lives in `com.wf.wflib.contract.site`; the soldier machinery it drives is
`soldier.api.Sites` / `SiteSpec` (squads with a position, a radius and a patrol route).

## The pipeline

1. **Force.** The contract's squads (counts already scaled by the player's tier) decide the base: `BaseKind.forForce(soldiers + 3*tier)`,
   or the contract names one: `"site": { "base": "outpost" | "compound" | "fortress" }`.
2. **Site selection** (`SiteSelector`). Candidates on a ring round the player; each is screened (centre and four edge points: dry,
   solid, not a coast) and then surveyed over the whole footprint. If nothing fits, the next smaller archetype is tried.
3. **Survey** (`SiteSurvey`, over `Terrain`). Refuses: lava; any wet biome (ocean, river, beach) over a third of the footprint;
   water over 1/12; loose or slippery ground (sand, gravel, powder snow, ice, mud) over 1/6; hollow ground (a cave roof or ravine edge
   in the top three blocks) over 1/8; a height range beyond what the archetype can grade (5 / 7 / 9 blocks). Scores what is left:
   flatness, nearby natural cover, a centre above its surroundings.
4. **Layout** (`BaseLayout`, pure). See the rules below. Output is a `BasePlan`: pieces, soldier zones, vehicle slots, the cache spot.
5. **Build** (`BaseBuilder`). Grades the pad to the survey's median level, blends a six-block apron into the land, clears trees and
   rock above it, then places the pieces in a biome palette (`Palette`: temperate stone, arid sandstone, cold deepslate).
6. **Soldiers** (`BaseSoldiers`). The contract's squads keep their indices (index 0 holds the command post, which is what a
   kill-the-target contract wants) and take the most important posts in order: command, wall patrol, outer patrol, reserve,
   outposts, gates, towers, nests. Up to the archetype's size, extra soldiers fill the empty posts by weight, snipers on towers and
   machine-gunners in nests when the force has them.
7. **Vehicles** (`SiteVehicles`). Bays record slots; a `VehicleProvider` (a vehicle mod) parks a vehicle in each. When any soldier
   of the base sees an enemy, each parked vehicle rolls 35% once per alert; on a hit the nearest free soldier runs to it and the
   provider seats them.

## Layout guidelines (what makes it read as lived in, and worth planning around)

* **Walls and gates.** A walled base has gates on opposite sides, towers on the corners and flanking the gates; a small post has
  sandbags and one watch tower instead of a wall.
* **Interior.** Command building near the middle (cartography table, chests, the cache barrel), barracks with bunks, a depot of
  crates and hay, a vehicle bay by the room it needs, a radio mast, a campfire, lanterns on posts. Nothing overlaps; there is a
  two-block margin so soldiers can walk between buildings.
* **Approaches.** Machine-gun nests in sandbag horseshoes covering the gates, trench runs outside the wall: an attacker crosses a
  killing ground.
* **Beats.** One patrol loop inside the wall, one outside it, plus small outposts (tent, nest, campfire) out in the approaches, each
  with its own four-point beat. Overwatch on towers, a reserve in the barracks, guards at the gates.
* **Size.** Outpost r16, compound r28 (wall 18), fortress r44 (wall 28, thicker wall with a wall-walk, bunkers, two bays, more of
  everything). A fortress is about twenty-five posts.

## Extending it

* A new piece: add a `BasePlan.Type`, place it in `BaseLayout.plan` (use `pack` to find room), build it in `BaseBuilder.piece`.
* A new archetype: a `BaseKind` constant; the layout and builder read only its numbers.
* A new biome look: a `Palette` and a rule in `Palette.at`.
* A vehicle mod: implement `contract.api.VehicleProvider` and call `SiteVehicles.provider(...)` at setup.

## Not here yet

Data-driven archetypes (JSON in a datapack), NBT structure templates for pieces, patrolling vehicles (a provider method that drives
a route), and bases for the Black Site and engagements, which still raise plain squads.
