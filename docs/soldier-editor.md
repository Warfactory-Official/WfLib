# Creative soldier workshop

Open `/soldiers editor` in Creative mode, or use **WFLib → Soldier Editor** in the creative tab.
The empty editor egg opens the workshop. Saved eggs deploy their named variant on a block;
using an egg in air or sneaking opens the editor again. This UI requires the matching WFLib
version on both client and server. Survival and spectator players cannot edit, issue or deploy
these variants, even by sending the GUI packet directly. Operators may manage definitions from
console; deploying near a player and receiving an egg need an actual creative player.

## Workflow

1. Add a faction: stable lowercase ID, display name, friendly/hostile toward real players.
   Members of the same faction are allies; different factions fight one another.
2. Add a variant, name it, choose its combat role and equipment. Each variant has a stable ID
   for commands and eggs; renaming the display name preserves that ID.
3. Search installed weapons/uniforms by name, pack or item ID. Hover over a browser row to
   preview it; clicking commits the choice. Rotate the player preview to check the outfit.
   Armor choices include compatible plate-insert combinations from WFLib's armor catalogue.
   Equipping a piece that occupies multiple body slots clears overlapping pieces.
4. Tactics sliders set spare magazines, perception range, aim spread and grenade quantities.
   Role selection applies the role's recommended weapon and tactical defaults; customize afterward.
5. Save, get a reusable spawn egg, deploy copies nearby, or deploy the faction's saved variants
   in rotation using the deploy-count slider. Deployment stays on the caller's floor and verifies
   loaded chunks, standing space, floor support and world border. The existing global cap is 40.
6. Clear deployed members without deleting their definitions, delete a variant, or delete an
   entire faction with its variants and deployed members. Destructive GUI actions ask confirmation.

Definitions live in the world's `data/wflib_soldier_roster.dat`, shared across dimensions and
persisted by SavedData. Restarting the server retains the roster, not live soldier bodies (the
existing AI still removes its fake-player bodies when the server stops). Template edits affect
future deployments. A faction's player disposition also updates its current deployed members.
Deleted variants/factions invalidate their editor eggs. An egg resolves the receiving world's
variant ID; it does not carry an arbitrary inventory or executable NBT.

The original MineAssault friendly/enemy eggs and `/soldiers spawn` remain legacy interfaces.
Use workshop eggs/commands for custom variants and saved faction definitions.

## Roles

| Role ID | Behavior |
|---|---|
| `rifle` / `rifleman` | Medium-range bursts, ordinary squad bounds |
| `grenadier` | More frequent frags against covered contacts; existing squad cooldown and friendly-fire checks remain |
| `medic` | Extra medical supplies, rescue priority, rear formation and greater separation from the enemy |
| `mg` / `machinegunner` | Long suppressive bursts, overwatch and later bounds |
| `sniper` | Deliberate single shots, tighter aim, longer sight and stand-off; does not chase stale contacts |
| `heavy` | Actual exoskeleton garment required; 40 health, knockback resistance, steady movement and sustained bursts |
| `assault` | Forward formation/bound priority, shorter engagement range and quicker bursts |

Riflemen, medics and gunners share contacts and rescue duties through the existing squad AI.
All still react to grenades, suppression, weapon state, injuries and cover. Medical treatment and
revival use the registered SoldierMedical provider (MineAssault's WFMedical integration).
Exoskeleton recognition accepts powered WFLib ExoProfiles and named legacy exosuit garments;
legacy clothing does not acquire a powered reactor or battery from this feature. Ballistic
protection comes from the actual equipped armor/plates. No infinite ammunition or invulnerability.

Soldiers now stand at ease without a threat, including at previously used cover. Combat can
request kneeling/crawling; grenade escape is upright and treating a casualty still kneels.
An explicitly scripted filming bot can retain its requested peaceful crouch.

## Commands

Creative players can use the management commands without operator status. Existing debug/profiling
commands retain their operator check. IDs are lowercase letters/digits/underscore/hyphen, 32 max.
Equipment IDs accept namespaces, slashes and modular armor suffixes. Tab completion uses the
server's installed choices. `none` removes an armor slot.

```mcfunction
/soldiers editor
/soldiers faction list
/soldiers faction add monolith true
/soldiers faction add allies false
/soldiers unit create monolith_rifle monolith rifle ma_rifles:akm
/soldiers unit rename monolith_rifle Monolith Rifleman
/soldiers unit equip monolith_rifle head mwarchive:pack/armor/helmet
/soldiers unit equip monolith_rifle chest wflib:plate_carrier+wflib:steel_platex2
/soldiers unit equip monolith_rifle feet none
/soldiers unit egg monolith_rifle
/soldiers unit spawn monolith_rifle 4
/soldiers faction spawn monolith 8
/soldiers faction clear monolith
/soldiers unit remove monolith_rifle
/soldiers faction remove monolith
```

The gun/helmet examples require those installed IDs; select from the actual tab suggestions or GUI.
`faction add` also updates an existing faction's disposition. Console can add/remove/rename/equip
and list definitions; it cannot claim to be a creative player to receive eggs or deploy near itself.

## Implementation and validation

WFLib owns roster, commands, packets, editor egg, screen and role doctrine. MineAssault continues
to own gun/grenade/medical actions. Its magazine issue order now reserves grenade hotbar slots
before adding spares, preventing grenadier/machinegunner kits from overwriting their own magazines.

The equipment catalogue is cached on the server; client filtering and hover previews are local.
Only visible equipment rows are widgets/rendered icons. Slider drags never send packets. Roster
responses are compressed and size bounded. The server validates creative mode, revision, counts,
faction membership, compatible equipment slots, installed usable guns, grenade types and exoskeleton
requirements. It never accepts client-created equipment stacks. A stale edit returns the current
roster rather than silently overwriting a different editor's update.

Regression tests cover stance, role doctrine, command resource IDs, roster round trips/deletion,
invalid definitions, and large catalogue wire limits alongside the existing soldier suite.
Native QA runs in a separate muted client/save under `build/editor-client`, never the user's
visible client. QA screenshots and logs are archived with the build. Game balance across every
weapon/armor pack is not implied by those functional checks.

## Local integration handoff (2026-11-08)

Worktrees: `/home/overlord/Codebases/soldier-editor/WFLib` and
`/home/overlord/Codebases/soldier-editor/MineAssault`, both `feat/soldier-editor`.
WFLib rebases the committed main headlamp/aim fixes through `e959d88`. The distributable also
preserves the active instance's existing main-worktree WFRegistry, Signals, Subject and Replay
compatibility edits, current SoldierLightClient tuning and four pixelated detector textures. These were copied verbatim; the other
agent's original working files were not changed. Keep these integration changes when rebuilding
for the current instance; the feature commit itself is separate for review/cherry-picking.

A pre-existing Zig array-repetition expression failed under the installed Zig 0.16. It now uses
`@splat(-1)` with the same `[36]i8` type/value. All eight native libraries were rebuilt and the
narrowphase parity tests passed. Other platforms were cross-compiled, not runtime tested.

The native QA fixture uses MineAssault's existing MCP harness on loopback port 18235, a separate
Xvfb display and muted client/save. Its tools were not connected to the user's visible client.
The harness's older `stage_bot` endpoint accepts Carpet actors only; native workshop soldiers
were checked with GUI deployment, commands, `stage_bots` telemetry and real inventory data.
The harness close-world tool assumes Flashback is present, so QA saved through Minecraft's
normal Save and Quit button instead. These harness limitations are outside this editor change.
