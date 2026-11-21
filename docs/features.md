# Features: the master switchboard

One file and one screen decide what of wflib is on.

* **Screen:** Mods > WFLib > Config, or `/wflibfeatures` in game. A big checkmark per mainline feature (a *group*) and smaller ones
  under it for the parts. Ticking a box ticks what it needs; unticking one unticks what needs it. A box a mod needs shows locked.
  *Reset to defaults* and *Open config file* are at the bottom. Systems are read at launch, so a change applies after a restart
  (the screen says so).
* **File:** `config/wflib-features.toml`, a `[group]` section per group with `enabled` (the master switch: off turns everything in
  the group off, the picks below are kept) and one boolean per feature, each with a comment saying what it is and what PDA app it
  hides. Replaces `wflib-systems.toml` for wflib's own systems (other mods keep their `<modid>-systems.toml`).
* **PDA:** a feature that is off hides its PDA apps. A system that is off never registers its apps; an app-only feature (radar,
  map, cameras...) hides the app of a system that stays on for other reasons.
* **Mods that need a system** (an installed mod calls `WFSystems.require`) turn it on without a loading warning, only a log line;
  the screen shows it locked. A warning is for a config that contradicts itself.

## How it is split

`core/features/FeatureCatalogue.java` is the list, and the one place defaults live. Three kinds of feature:

| Kind | What it switches | Needs a restart |
|---|---|---|
| system | a whole wflib system: its registries, items, blocks, tick hooks | yes |
| flag | a part of a system, gated where it runs (`Features.on("contract_heat")`) | yes (read at launch) |
| app | PDA apps only (`apps` list) | yes |

Groups: Private Military, Weapons and Optics, Demolition and Mines, Armor and Carry, Bodies and Physics, Sensors and Surveillance,
Hazards and Anomalies, Industry and Construction, PDA and Information, Tooling. Mandatory systems (the engine: ammo, round, damage,
explosion, fx, sim, ui, pda, map, ...) have no box; their PDA apps do.

`FeatureCatalogueTest` fails if a switchable system has no feature or two, if a feature names a missing system, or if an app is
claimed twice, so a new system cannot be added without deciding where it goes.

## Adding one

* A system: add it to `WFLibMod.SYSTEMS` and a `system(...)` line in a group.
* Part of a system: a `flag(id, title, blurb, needsSystem)` line, and `if (Features.on("id"))` at the entry point (tick, command,
  event handler).
* A PDA app: `app(id, title, blurb, "wflib:appid")`, or add the app id to the owning system's `apps`.
* Defaults: the `defaultOn` of the line (`off(...)` for default off). The defaults are a placeholder until decided.
