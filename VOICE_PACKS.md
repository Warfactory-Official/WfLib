# External NPC voice packs — Minecraft 1.21.1 NeoForge

The soldier AI in WFLib loads **external audio-only ZIPs**. No voice recordings are bundled into WFLib,
MineAssault, or another mod jar. This uses the existing positional WFLib audio packet and mixer.

## Local modpack

1. Put the generated faction ZIPs in `<minecraft>/wflib/voices/packs/`.
2. Assign voices using `<minecraft>/wflib/voices/teams.json`, or these operator commands:
   ```mcfunction
   /soldiers voices
   /soldiers voices team friendly stalker_military
   /soldiers voices team enemy stalker_monolith
   /soldiers voices reload
   ```
3. Restart after the initial installation. After changing packs while playing, run the reload command
   and use **F3+T** locally to refresh the client sound definitions.

`friendly` and `enemy` are MineAssault's spawn-egg team names. Filming squads can use any team name.
Any faction's voice can be assigned to either side. Unknown/missing packs stay silent. Assignments are
saved atomically, and a malformed pack rejects the reload while preserving the last good library.
To silence one team, remove its binding from `teams.json` and reload.

The existing harness order API accepts `"voice": "stalker_bandit"` for an individual NPC. An empty
string restores its team's assignment. Telemetry includes resolved `voice`, `last_callout`, and `callouts`.
Preview an installed line at a live NPC with `/soldiers voices preview Ally_1 contact`; normal cadence
and unconsciousness restrictions still apply. Operator permission level 2 is required for commands.

## Server-only installation and delivery

Install the same ZIPs and `teams.json` on the **server**, without requiring players to install them.
In `config/wflib-voices.toml` set, for example:

```toml
resourceBaseUrl = "http://YOUR_SERVER_ADDRESS:25586"
httpPort = 25586
```

Restart the server. Forward this additional TCP port if players connect over the internet. The game
port is unchanged. The listener serves only the current, generated audio ZIP at
`/wflib-voices/<sha1>.zip`; it cannot serve the server's files or directory listings. It is disabled
by default (`httpPort = 0`, blank URL), and both delivery settings require a restart.

Players receive Minecraft's normal resource-pack offer, with a hash for caching. They must accept it
to hear the imported recordings. It is an optional additional pack; existing server packs remain in
place. Reconnecting uses the cache. A successful `/soldiers voices reload` builds and offers the new
bundle to connected remote clients. Keep WFLib's soldier and audio systems enabled on both sides;
no new custom network protocol or client/server content-pack hash requirement is introduced.

For an existing HTTPS host or reverse proxy, keep `httpPort = 0`, set `resourceBaseUrl`, and publish
`wflib/voices/generated/voices-resources.zip` at `<base>/wflib-voices/<sha1>.zip`. Calculate SHA-1 from
the exact generated ZIP bytes, publish before the offer/reconnect, and update hosting after reloads.
The server logs its local bundle path; `sha1sum` prints the needed URL filename.

An integrated host uses its locally loaded resources. LAN guests can receive the same URL offer
when the host configures delivery with a client-reachable address, or install the packs locally. Downloads are not stored inside Flashback recordings: retain the voice ZIPs with the filming
modpack/archive for later replay and export.

## Convert STALKER audio

Requires Python 3.10+ and `ffmpeg` with `ffprobe`. The converter reads extracted local game/add-on
files; it does not download recordings or unpack `.db` archives. Your installed GAMMA add-on already
contains extracted faction voice folders.

```sh
python3 tools/voices/convert.py /path/to/characters_voice/human \
  --preset stalker --factions bandit monolith military duty freedom loner \
  --output /path/to/minecraft/wflib/voices/packs --normalize
```

It keeps speaker identities separate, converts to mono 44.1 kHz Vorbis, removes source metadata,
records actual clip durations, and emits `sounds.json` plus `voices.json`. Six variants per event
and speaker are selected by default; `--max-per-event 0` includes all, up to 128 variants per event.
Use `--inspect` first to see mapping counts without writing or encoding. Each ZIP has an adjacent
`.report.json` listing selected sources, unmapped clips, missing events and timings.

Presets: Bandit, Monolith, Military, Duty (`dolg`), Freedom, Loner (`stalker`), Mercenary (`killer`).
Incoming `grenade_…` and outgoing `grenade_ready_…` map separately, following `m_stalker.ltx`.
PDA-filtered duplicates and execution dialogue are excluded. Unmapped clips are reported; their
meaning is not guessed. The tool refuses to overwrite existing ZIPs and rejects overly long clips
(30 seconds), invalid identifiers, and packs above 64 MiB. Conversion never plays sound aloud.
`--normalize` enables FFmpeg loudness normalization at -18 LUFS / -2 dB true peak.

## Your own recordings, including medic lines

Arrange files as `<source>/<speaker>/<event>/<clip>.wav` (or OGG/FLAC/MP3/M4A/Opus), then:

```sh
python3 tools/voices/convert.py ./my-recordings --preset folders --id my_squad \
  --output ./packs --normalize --notice "Recorded by our squad"
```

Events supported by the pack format:

| Event | Automatic trigger |
|---|---|
| `contact` | A visually detected enemy becomes an acquired contact |
| `lost_contact` | A previously engaged target is no longer acquired |
| `search` | Hunting a remembered contact |
| `attack`, `covering` | Starting a fired burst against a target / remembered position |
| `grenade` | Actually reacting to a seen or heard grenade |
| `throw_grenade` | Beginning a real grenade pin-pull |
| `reload` | A real reload successfully starts |
| `wounded` | Incoming damage |
| `help` | Wounded with disabled legs, awaiting a medic |
| `treat`, `revive`, `revived` | Treatment starts, CPR starts, successful revival (rescuer speaks) |
| `enemy_down` | Visually confirmed downed enemy |
| `retreat` | Heavily suppressed |
| `idle`, `death` | No threats / actual death |
| `flank` | Available for explicit preview; the current AI has no distinct flank order |

Missing events stay silent. The sample STALKER packs do **not** invent medic/reload lines; supply
those in custom packs. Each soldier selects a stable speaker from its pack using its UUID. Playback
is audible within 40 blocks using WFLib's radio audio volume slider. Variants don't immediately
repeat. Actor speech cannot overlap itself. Squads share a speech gate; an urgent grenade warning
can cut across another member's ordinary chatter, with a shared cooldown preventing a chorus.
Dead/unconscious NPCs cannot issue callouts (a death sound is allowed at death). These calls report
AI decisions; audio does not grant the AI new knowledge or change its movement.

## Format and checks

A ZIP contains `voices.json`, optional `pack.mcmeta`/`NOTICE.txt`, and only `assets/<pack_id>/sounds.json`
and `assets/<pack_id>/sounds/**/*.ogg`. An example manifest:

```json
{
  "format": 1,
  "id": "my_squad",
  "speakers": {
    "speaker_one": {
      "contact": [{"sound": "my_squad:voice/speaker_one/contact/clip_one", "ticks": 43}]
    }
  }
}
```

Each sound event resolves to its own single OGG file. The server chooses variants, so listeners hear
the same line. IDs use lowercase letters, digits and underscores. Import validation rejects path
traversal, duplicate ZIP entries/IDs, cross-namespace resources, unknown events, missing audio,
oversized manifests, invalid durations, and oversized ZIPs/libraries. Limits: 32 packs, 64 speakers
per pack, 128 variants per event, 4 MiB per audio clip, 64 MiB per expanded pack, 256 MiB per library.

Checks:

```sh
python3 -m unittest discover -s tools/voices -v
./gradlew test --tests '*VoicePackTest' --tests '*VoiceCadenceTest' --tests '*Bot*Test' --tests '*SoldierBrainTest'
```

The native `VoiceGameTest.externalPackEmitsOnceAndUnconsciousSoldiersStaySilent` creates an isolated
fixture ZIP, spawns/removes a real soldier, verifies emission/cadence/unconsciousness, and restores
the external library. On a development server run:
`/test run externalpackemitsonceandunconscioussoldiersstaysilent`.

Original audio ownership and redistribution terms remain unchanged. Keep the local converted
STALKER examples outside source control/mod artifacts; use recordings you may distribute for a
public pack or a server download. Sources for the mapping and audio layout:
[NeoForge 1.21.1 sounds](https://docs.neoforged.net/docs/1.21.1/resources/client/sounds/),
[installed Dux voice add-on](https://www.moddb.com/mods/stalker-anomaly/addons/duxs-innumerable-character-kit-voices-pack),
and your local X-Ray source `src/xrGame/ai/stalker/ai_stalker.cpp: CAI_Stalker::LoadSounds`, plus
GAMMA's `gamedata/configs/creatures/m_stalker.ltx`.
