# NPC voice-pack handoff — 2026-11-07

User requested external, importable NPC combat callouts for both sides, server-side installation and
client delivery, a converter, and real STALKER faction examples. Target: 1.21.1 NeoForge. Keep all
future test clients silent; the tests in this task were headless servers and offline encoders.

## Source and integration

Worktree: `/home/overlord/Codebases/npc-voice-packs/WFLib`.
Branch: `feat/npc-voice-packs`, based on `WFLib-soldier` commit `318cce3`.
The soldier AI is on that separate branch, not WFLib-main. Do not drop the soldier work while merging.
No MineAssault source changes were required: its NPCs and the filming harness already use this AI.

- `VoicePack`: bounded external ZIP/manifest validation, speaker/event/variant definitions.
- `SoldierVoices`: catalog, persistent team assignments, deterministic speaker selection, positional
  WFLib audio playback, squad cadence and anti-repetition, generated audio-only resource bundle.
- `VoiceCadence`: actor and squad speech/event cooldowns, urgent squad warnings.
- `VoiceDelivery`: optional bounded HTTP server, vanilla resource-pack offers/hash caching, refresh
  on reload, close at shutdown; only audio is served. Delivery defaults off until configured.
- `VoiceResources`: always-enabled local generated resource pack.
- `VoiceCommands`: list/reload/team/preview commands under `/soldiers voices`.
- `SoldierAi`: real perception/grenade/weapon/medical/death hooks; per-NPC `voice` order and telemetry.
- `SoldierSystem`: lifecycle/config/listeners; declares existing audio system dependency.
- `SystemGameTests` / `VoiceGameTest`: native NPC emission/cadence/unconsciousness check.
- `tools/voices/convert.py`: custom-folder/STALKER conversion, normalization, reports, no-overwrite.
  This repository ignores `tools` broadly; these two new Python files are deliberately tracked.
- `VOICE_PACKS.md`: installation, server port/URL setup, commands, format and event table.

The STALKER presets follow local `m_stalker.ltx` sound prefixes and X-Ray's `LoadSounds` mappings.
In particular, `grenade_ready` is an outgoing throw; `grenade_<number>` is incoming danger.
Do not map all wounded folders: some contain execution dialogue. Missing medical/reload events are
intentionally silent, with custom recording support; the sample recordings do not invent those lines.

## Generated audio and installation

Local input: `/home/overlord/Games/GAMMA/gamma/mods/305- Dux Characters Kit Voices Pack - Demonized/gamedata/sounds/characters_voice/human`.
Generated examples: `/home/overlord/Codebases/npc-voice-packs/generated`.
Six packs: Bandit (196 clips), Monolith (214), Military (144), Duty (224), Freedom (154), Loner (228).
Total: 1,160 clips, 16 speaker profiles. Every OGG was checked as mono 44.1 kHz Vorbis; durations were
measured after encoding. Conversion reports preserve source filenames and missing/unmapped events.

The final installation receipt identifies the patched jar, exact hashes, changed class entries and
backup. Only relevant compiled classes are merged into the existing `wflib-1.8.39.jar`, preserving
all other installed entries, including the Configured change and other agent's registry/UI/replay
fixes. The original installed SoldierAi/SoldierCommands/SoldierSystem/SystemGameTests classes and
nested classes were byte-compared to the baseline before installation. Restart normally to activate.

The Prism instance is `/home/overlord/.local/share/PrismLauncher/instances/Warforge Harness Dev/minecraft`.
Voice ZIPs go in `wflib/voices/packs`; initial teams are friendly=Military and enemy=Monolith.
No filming world, live player, or user audio setting is changed. No second Prism instance is created.
A future rebuild from another branch will overwrite the feature unless this source commit is merged.

Archive copies, tools, docs, proof JSON, native log and original-jar backup are under:
`/run/media/overlord/External/The Beginning/Rommie Gear Video Folder/Warforge Archive/Voice Packs`.
Audio remains outside source control and all mod jars. Original audio/add-on ownership and distribution
terms remain unchanged; local availability does not by itself grant public redistribution permission.

## Validation

- 41 selected Java tests pass, including all six architecture rules and existing bot/brain tests.
- Four Python tests pass, including actual stereo WAV -> mono Vorbis conversion and no-overwrite.
- Compile and `jar` pass using Java 21 and current local dependencies.
- An isolated dedicated server loaded all six real packs and both team assignments.
- HTTP GET returned a byte-identical 20,562,012-byte resource pack with its correct SHA-1; HEAD passed;
  traversal/unknown files returned 404 and POST returned 405.
- Native `/test run externalpackemitsonceandunconscioussoldiersstaysilent` ran a real spawned soldier,
  asserted emission/telemetry, no repeat, unconscious silence and a conscious control. `test runfailed`
  returned `No tests found`. The server then shut down normally. No test client/audio was launched.

Build helper `/tmp/wf-voices-local.gradle` substitutes the current local WarForge jar with AlterKind /
kill-attributor APIs and installed wfweight jar with StaminaMath.drained; stale published snapshots
failed compilation before this. A copy accompanies the archive for this workstation only.
Native Zig generation was skipped (`-x buildNatives`) because its existing compiler mismatch is
unrelated. The distributable jar preserves the original installed native binaries. Do not replace it
with the raw worktree jar without rebuilding/restoring natives and the separate uncommitted fixes.

## Remaining checks and limits

- Client playback, resource-pack prompt acceptance, LAN joining, and Flashback replay/export still
  need a real client check. Headless tests do not establish perceived loudness or callout quality.
- Audio uses existing one-shots at the NPC's position when emitted. It does not follow the speaker
  afterward; current actor speech finishes before that NPC can speak again. A grenade warning may
  overlap another squad member's ordinary chatter, but cannot interrupt its own long line.
- Event attempts that hit cooldowns are dropped, not queued for later stale playback. Missing events
  never substitute another faction/speaker. `flank` is an explicit-preview event; AI has no distinct
  flank maneuver trigger yet. The sample set contains no medic/reload/retreat dialogue.
- Imports/reloads are synchronous administrator actions; the 20 MB six-pack library takes about a
  second to validate/rebuild, not every tick. Keep limits if extending the importer. HTTP is opt-in,
  bounded to two worker threads and a 16-task queue; this was not load-tested across public servers.
- Retain the ZIPs alongside Flashback recordings for offline replay/export; don't depend on a live
  server's pack URL after filming. Do not add proprietary voice recordings to a mod artifact/commit.

Continue with an actual muted client verification first, then assess long-line timing and faction
semantics while staging a squad fight. User can unmute their own client when they want to listen.
