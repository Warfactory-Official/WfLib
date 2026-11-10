# Commanding soldiers: companions, fire teams, territories, depots

Part of the `soldier` system ([soldiers.md](soldiers.md)). Everything here sits on one idea: **a soldier has one order**, whether a player
gave it ("move there") or a map-maker assigned it ("patrol the north gate"). The squad AI carries it out, the HUD shows it, the
soldier acknowledges it by voice.

## Orders

`SoldierOrder` (api): `follow`, `regroup` (run back, then follow), `hold`, `move`, `attack` (move, then hold), `suppress` (put fire
on a point for 30 s), `patrol <territory>`, `guard <territory>`, `resupply`. An order is turned into what the AI already speaks
(mode, anchor, hold radius) by `Orders.apply`; `SoldierBrain.tick` runs the order's own logic every 10 ticks (escort, patrol walk,
resupply trip, arrival becomes hold). Combat always wins: while anything threatens the soldier, patrols and trips pause and the AI
owns the movement; they carry on afterwards.

## Territories (regions)

`Territory`: id, name, owning team, dimension, a vertical cylinder (centre, radius, minY/maxY) and an optional patrol route of
waypoints. Saved with the world (`TerritoryBook`).

| Command (op) | |
|---|---|
| `/soldiers territory create <id> <radius> [team]` | around you |
| `/soldiers territory waypoint add <id>` / `clear` | route stops, at your feet, in order |
| `/soldiers territory list` / `remove <id>` | |
| `/soldiers assign <team\|soldier> patrol <id>` / `guard <id>` / `hold` / `follow` | standing orders for soldiers nobody owns |

A **patrol** walks the route in order and loops; without a route it picks points inside the cylinder, never right next to the last
one. At each stop the soldier halts 3-10 s and looks about. A **guard** stands at a point and wanders at most 8 blocks between
stops of 15-35 s. A stop that takes more than 60 s to reach is abandoned. Deleting a territory sends its patrols back to following
their owner.

## Depots (resupply)

`Depot`: any container block (chest, barrel, shulker box, or any modded block entity with an inventory), a team (or any), and
`infinite` (an armoury crate that refills any kit from nothing). Saved with the world (`DepotBook`).

`/soldiers depot add <id> <team|any> <infinite>` (look at the container) / `list` / `remove <id>`.

A soldier's **kit** is whatever it carried once armed (`Kit`: each item, how many, and a stack to copy). A kit is *low* when a
stackable is down to half, or a single item is gone. A soldier that is following, holding, patrolling or guarding, with nothing
threatening it and a usable depot within 96 blocks, goes on its own (checked every two minutes); `resupply` orders the trip from
up to 160 blocks. It walks to within 3.2 blocks of the container, takes what its kit lacks, says `resupplied`, and goes back to
what it was doing. A depot with nothing for it is crossed off and the next is tried; a trip that takes 90 s is abandoned
(`ack_refuse`).

## Companions and fire teams

A player's soldiers are **companions**: `Companion` records (callsign, side, role, gun, grenades, spares, worn armour as full
stacks, voice, fire team, last order), saved per player (`SquadBook`). Soldiers themselves are not saved, so the record brings
them back: on login they are placed beside their owner, on logout and shutdown their last order is written back and they are
removed. A companion left more than 96 blocks behind (or in another dimension) is brought to its owner when it has no contact. A companion that
dies leaves the roster and its owner is told.

Four fire teams, Alpha to Delta, up to 24 soldiers per player; new recruits go to the emptiest team.

* **Recruit:** sneak + use, empty hand, on a friendly soldier nobody else owns (egg soldiers, `/soldiers spawn friendly`).
  `/squad recruit <variant>` spawns a saved variant from the soldier workshop straight onto the roster (creative / op).
* **Equip a force:** variants are the equipment definitions (`/soldiers editor`). `/squad kit <all|alpha|...> <variant>` re-equips
  whoever is selected: inventory cleared, armour, gun, grenades, spares and role replaced, the AI re-arms it and the roster keeps
  the new kit. Armoury depots keep it topped up.
* **Command:** `/squad order <all|alpha|bravo|charlie|delta|name> <order> [territory]`; point orders use the crosshair, with
  several soldiers spread in the squad AI's formation around the point. `/squad list`, `/squad team <name> <team>`,
  `/squad dismiss <name>`.

## The squad UI (client)

* **HUD strip** (top left): a row per fire team, a card per soldier: face, health, ammunition and spare magazines, and a corner
  letter for its order (F H M A S P G R). The selected group has a red bar under its letter and a red frame. Orange frame: in a
  fight. Blue frame: on a resupply trip. Red X: downed.
* **Command wheel:** hold `G`, move to an order, let go. Scroll or `1`-`5` (everyone, Alpha..Delta) change group. Orders that need a
  point are greyed out when the crosshair was not on anything. Patrol and guard use the territory under the point (or under you).
* **`X`** sends the selected group to the crosshair; **`V`** cycles the group.
* **Feedback:** a pulsing ring and a post at the ordered point, a line to it from every soldier that took the order, the order's name
  floating over each of them, and a line of text above the hotbar ("Alpha: Move (3)"). One soldier (the nearest that has the line)
  answers by voice.

The client only ever *asks* (`SquadPackets.Command`); the server checks range (256 blocks), loaded chunk, ownership and that the
territory exists. The channel is optional: without WFLib's UI a client commands with `/squad`.

## Voices

New callout events (pack format `voices.json`, `tools/voices/convert.py`, [VOICE_PACKS.md](../VOICE_PACKS.md)):

* **Non-combat lines** both the STALKER and Trepang2 voice sets carry plenty of speech that is not combat (jokes and stories, greetings, replies when talked to, looting, bantering at a post); the converter maps those by folder and file name (see below). `greet` is said when the owner returns after a minute or more, `talk` when they sneak-use the soldier.
* **Idle variants** `idle_alert` (a fight ended under a minute ago), `idle_hurt`, `idle_night`, `idle_rain`, `idle_chat` (a squadmate or the owner
  within 6 blocks; the conversation lines), `idle_bored` (five quiet minutes). A quiet soldier picks among the variants its speaker has, weighted by
  situation, with plain `idle` always in the running; one shared 30 s cooldown. Packs with only `idle` behave as before.
* **Acknowledgements** `ack_follow ack_hold ack_move ack_attack ack_suppress ack_patrol ack_resupply ack_regroup ack_refuse`, and
  `low_ammo`, `resupplied`.

## Not done yet

* `suppress` and the patrol/guard walk have only been exercised through their state machines (GameTests), not with a gun mod and
  a real fight; the stand-in contact a suppress order keeps fresh is the part most worth watching.
* The client UI (HUD, wheel, markers) compiles but has not been run on a client.
* `low_ammo` is defined but nothing says it yet; soldiers do not yet *ask* for a resupply by voice.
* A territory cannot garrison itself (spawn its own soldiers when a player comes near); soldiers nobody owns are still not saved
  with the world.
* No management screen (rename, reorder, per-soldier orders): `/squad` covers it. The reference for one is STALKER GAMMA's
  companion list; Steve's Army has a squad command screen with a friend-or-foe tab.
* Voice packs themselves: the installed packs were converted before this and have none of the new lines. Re-run `convert.py` (it
  now maps the STALKER `talk/`, `reactions/`, `states/meet`, `states/loot`, `fight/post_combat_wait`, `alife/` folders and the Trepang2
  `Spawn/` and `SusMode/` banter) to new pack ids, then install them next to the old ones.
