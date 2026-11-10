# Commanding soldiers: companions, fire teams, territories, depots

Part of the `soldier` system ([soldiers.md](soldiers.md)). Everything here sits on one idea: **a soldier has one order**, whether a player
gave it ("move there") or a map-maker assigned it ("patrol the north gate"). The squad AI carries it out, the HUD shows it, the
soldier acknowledges it by voice.

## Limits and persistence

There is no cap on soldiers, on a squad's size or on a garrison: each soldier is a chunk-loading player with a full AI tick, so
the server's tick time is the limit. Soldiers that belong to no roster are saved with the world (`PostBook`): the state of each
(side, kit, worn armour, position, post, order, owner, garrison) is written down every 30 s and when the server stops, and put
back a few per tick on the next start, loading each one's chunk to receive it. A crash loses at most the last 30 s. Companions
are in their owner's `SquadBook` instead and come back with them.

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
| `/soldiers territory list` / `remove <id>` | `list` shows each garrison's living / target size |
| `/soldiers territory garrison <id> <count> <patrol\|guard> <reinforce seconds> [variant,variant]` / `garrison <id> clear` | the territory raises and keeps up its own garrison |
| `/soldiers assign <team\|soldier> patrol <id>` / `guard <id>` / `hold` / `follow` | standing orders for soldiers nobody owns |

A **patrol** walks the route in order and loops; without a route it picks points inside the cylinder, never right next to the last
one. At each stop the soldier halts 3-10 s and looks about. A **guard** stands at a point and wanders at most 8 blocks between
stops of 15-35 s. A stop that takes more than 60 s to reach is abandoned. Deleting a territory sends its patrols back to following
their owner.

### Garrisons

A territory with a `Garrison` raises its own force. Every five seconds it counts the soldiers it has raised (alive, or still waiting
to come back after a restart), and recruits the shortfall at random free ground inside itself (five a pass), each from the next saved
variant in turn (all saved variants, or only the ones named). The new soldiers patrol or guard the territory. A loss is replaced no
sooner than the reinforcement delay after it. Lower the size and the surplus is sent home; clear the garrison and the soldiers stay,
as loose soldiers. Which side they fight for is their variant's faction. A territory whose centre chunk is not loaded raises nothing,
so a garrison fills when somebody comes near, and a soldier is a chunk-loading player, so a garrison also keeps its own ground loaded.

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

* **Management screen** (`Home`): four fire-team columns of soldiers (face, name, order, health) on the left, what they can be told
  to do on the right. Click selects (a team's name selects the team; shift or ctrl adds; ctrl+A everyone). Everything else is a
  drag: drop soldiers on another column to change their fire team (the line shows where they land, so it also reorders), on an
  order (Follow me, Hold here, Regroup, Resupply) or a territory (Patrol or Guard, by the switch above the list) to give it, on
  the red strip to discharge them. Clicking an order with nothing selected gives it to everyone. The **Territories** tab is the
  map-maker's: each territory has a card with its garrison size, patrol or guard, reinforcement delay and variant, edited in place
  (shift: x10), and a "new territory where you stand". The server checks every soldier named is the player's and that edits come
  from an operator.
* **HUD strip** (top left): a row per fire team, a card per soldier: face, health, ammunition and spare magazines, and a corner
  letter for its order (F H M A S P G R). The selected group has a red bar under its letter and a red frame. Orange frame: in a
  fight. Blue frame: on a resupply trip. Red X: downed.
* **Command wheel:** hold `G`, move to an order, let go; you keep walking while it is open (the mouse chooses, the camera stays still). Scroll or `1`-`5` (everyone, Alpha..Delta) change group. Orders that need a
  point are greyed out when the crosshair was not on anything. Patrol and guard use the territory under the point (or under you).
* **`X`** sends the selected group to the crosshair; **`Insert`** cycles the group.
* **Feedback:** a pulsing ring and a post at the ordered point, a line to it from every soldier that took the order, the order's name
  floating over each of them, and a line of text above the hotbar ("Alpha: Move (3)"). One soldier (the nearest that has the line)
  answers by voice.

The client only ever *asks* (`SquadPackets.Command`); the server checks range (256 blocks), loaded chunk, ownership and that the
territory exists. The channel is optional: without WFLib's UI a client commands with `/squad`.

## Getting soldiers (recruiting)

Interchangeable **sources**, switched on in `wflib-recruiting.toml` (`sources = [...]`; default `rescue`, `pda`, `barracks`), any mix at once:

| Source | How |
|---|---|
| `pda` | The **Forces** app on the PDA: its HIRE tab lists every friendly saved variant (role, gun, mags, gear, price, upkeep, delay; price and upkeep only show when there are some, from the config or a mod wallet such as Pluginless), pick one, pay, and they appear 28-44 blocks away and walk to you after `callin.delaySeconds` (default 45). Its FORCES tab lists your soldiers (team, order, health, ammo) with hold, follow and release buttons. Needs a PDA carried. The default way. `/wfpda open wflib:reinforcements [forces]` opens it directly. |
| `barracks` | Use (right-click, not sneaking) a barracks block: a menu of what it has for hire. `/soldiers barracks add` marks the block you look at (any block); `remove`, `list`. Mods register their own, below. |
| `rescue` | Sneak and use an unowned friendly soldier with an empty hand (what existed before). |
| `garrison` | The same on a territory garrison's soldier, at `garrison.cost*`; the territory raises a replacement. Off by default. |

`/soldiers recruiting` shows what is on and the prices. Prices are an item (`costItem`, `costItemCount`) and/or currency from a wallet
(`costCurrency`). **Wages** (`wages.enabled`, off by default): once per in-game day each soldier is paid `wages.item` / `wages.currency`
from its owner; one that cannot be paid leaves, or with `wages.defectionChancePercent` (default 2, `wages.defection = false` to
switch off) turns hostile where it stands. A paid hire is saved with the roster, so a restart does not lose one on its way, and it waits
for an absent hirer.

**For other mods** (`com.wf.wflib.soldier.api`): `Recruiting.barracks("mymod:barracks", state -> state.is(MyBlocks.BARRACKS), Cost.items(Items.EMERALD, 3), 20)`
makes a block a barracks; a multiblock implements `BarracksType` (`matches` says whether the clicked position is a working one,
`offers` what it has). `Recruiting.source(RecruitSource)` adds a whole new source (listed in the config by its id).
`Recruiting.wallet(Wallet)` lets hiring and wages be priced in an economy mod's currency. WFLib supplies the menu, the payment and
the arrival.

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
* The management screen has not been run on a client either, and it has no rename, reorder or per-soldier kit editing (re-kit is
  `/squad kit`).
* A garrison keeps its ground loaded (its soldiers are players) and never stands down when nobody is near; a garrison of hundreds
  is a server-load decision for the map-maker.
* Voice packs themselves: the installed packs were converted before this and have none of the new lines. Re-run `convert.py` (it
  now maps the STALKER `talk/`, `reactions/`, `states/meet`, `states/loot`, `fight/post_combat_wait`, `alife/` folders and the Trepang2
  `Spawn/` and `SusMode/` banter) to new pack ids, then install them next to the old ones.
