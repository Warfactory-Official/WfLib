# PDA

Item `wflib:pda` (`pda/`, client `pda/client/`). Replaces `wflib:terminal`, `wflib:camera_tablet` (no migration: old stacks => air).

## 97. Item and focus

| state | input |
|---|---|
| not held | `Pda.carries` (inventory) still gates telemetry, TV keys |
| held (main hand), rest | mouse-look; use => focus; sneak + use => item action (monitor => copy channels) |
| focused | mouse -> cursor (`MixinMouseHandler`: PDA before TV), LMB = pointer, wheel -> screen, WASD walks; use / Esc / slot change / any Screen => rest |
| fullscreen (`PdaClient.fullscreen(feedId)`) | feed letterboxed on the HUD, PDA hidden; mouse pans `CameraLink`, wheel zooms; use / Esc => focused; link lost => focused. Drone/mounted feed: `DroneCamHud` overlay; attack = CONNECT the Drones row showing it. `PdaClient.watch(feedId)` = raise + fullscreen (CA: on leaving a remote UAV) |

- No Screen while focused: `setScreen` releases movement keys + mouse grab.
- `startUseItem` repeats while use held => latch until key up.
- Press + release between two frames => `pressLatch` delivers one down frame (synthetic clicks).
- Crosshair layer hidden while focused.
- Off-hand item hidden while held (both `RenderHandEvent`s cancelled).

## 98. Rendering

- `PdaRenderer` (HAND pass): glTF `wflib:models/pda/pda.glb` (placeholder: MineAssault `tools/ma.py pda-placeholder`), nodes `grip_left` / `grip_right` = wrist goals for `client.view.PlayerArms` + `ArmViewPose` (shared with MineAssault).
- Material `extras.textureSlot: "screen"` <- `UiSurfaces` texture of `PdaShell` (384x240 units, v0 = top; px/unit = integer nearest raised screen's window px, `SCREEN_WIDTH` must match model); `TextureSlots.emissive` => unlit.
- Placeholder 0.232 x 0.148 m, screen 0.224 x 0.14. `VIEW_SCALE` 1.9 over the metric model: raised screen ~half the view wide; arms solve to the scaled grips.
- Elsewhere (`GemRenderItemRenderer`): `screen` slot = `textures/pda/screen_off.png`; 3P carried by top edge, screen out.
- Surface redraw 20 fps at rest, every frame focused. Cursor / hit table 1 frame stale.

## 99. Apps

- `PdaApps.register(PdaApp)` at client setup; sealed at first draw (`build()` once, one `UiInstance`); later => throws.
- App area `PdaShell.APP_WIDTH` x `APP_HEIGHT`, palette `PdaShell.*` (expressions). Subject / signal names global => prefix `pda_`.
- `visible(player)` per tick; open app turning invisible => home.

| app | data |
|---|---|
| Telemetry | event log: `TelemetryLog` (reporter `recordEvent` while `datalink()` non-null; DAMAGED / OFFLOAD / ONLOAD silent; ring 256, not saved) -> carriers per `TelemetryLink.receives`, pushed per server tick; history `TelemetryLogRequestPacket(after seq)` on open. Rows = ALL + reporters (live `TelemetryClient` fix or last event); classified drones => no datalink |
| Cameras | held PDA `CHANNELS` -> `CameraPanelPacket` (`PdaCamerasRequestPacket` on open, `CameraControlPacket` refresh); owns `CameraLink` while open unless a TV round holds it; FULL => fullscreen |
| Drones | held PDA `CHANNELS` drone targets, every mod's bodies -> `DroneStatusRequestPacket` per 10 ticks while open -> `DroneStatusPacket` (status, task, fuel, link, `RemoteBody` cells/ammo/refusal, offered kinds; unloaded => `DroneRecall` + `HostWakeup` record). `DroneActionPacket`: SELECT (unloaded => wake, feed subscribed), CONNECT (`RemoteBody.connect` after wake; PDA lowers), REMOVE. Feed via `CameraLink`, FULL. DISPATCH: selected body with offered kinds => `DroneTasking.assign`, else nearest dispatcher; kind + target (own position / side marker) -> `DroneDispatchPacket`; pad launches -> held PDA `CHANNELS`. Classified => NO LINK: no feed (`CameraNet.entitled` refuses), no connect, no orders |
| Radio | Drones status rows with `radio`: station `<` / `>` (catalogue order, off between last and first), OFF -> `RadioTunePacket` |
| Map | host's fullscreen map (`WFMaps.openFullscreen()`: JourneyMap `UIManager` internal, Xaero `GuiMap`) inside the app area via `EmbeddedScreen`; LMB / drag / wheel / RMB menu forwarded; map closes itself => home; hidden without a host |
| Markers (`MarkerClient` map tool) | map menu add (name prompt) / remove; `MarkerStore` SavedData per side (faction, else player), cap 64, pushed to online side members; layer `wflib:markers`, current dimension |
| Radar | `PdaRadarSubscribePacket(range)` while open -> `PdaRadarPacket` per 20 ticks: own net `TrackPicture` as a `ScopeFrame` about the player (not `ScopeCache`: frames keyed by net, hub frames would overwrite). Sonar = tracks with the SONAR band bit (rings) |
| Satellites | `SatStatusPacket` per 40 ticks to every carrier: own net's birds, empty without uplink; next pass = `OrbitalNet.nextPass` sampled 40 ticks to 20 min. Console: typed `[callsign\|#n\|n] verb args` -> `SatCommandPacket` (own net + held + uplink) -> `OrbitalNet.commandBy`, issuer = player; replies `SatReplyPacket` + changed `reply()` lines. Local: help, sats, select, clear. IMAGERY tab = `SurveyCache` tiles around the player |
| Alerts | `PdaAlerts` history (32), badge = unread |
| Terminal | line -> `TerminalCommandPacket` -> `PdaTerminal.run` (held PDA): `drones` (held `CHANNELS`, loaded), `nodes` / `sats` (own net + uplink), `rename drone\|node\|sat <#n\|name> <new\|->` (name 1-16 `[A-Za-z0-9_-]`, `-` = default). Drone -> `DroneEntity` callsign (datalink label); node -> `NamedNode` block entity (hub, probe, orbital node), label applied at its next registration; sat -> `callsign` command-bus verb |
| Wiki | in-game tutorials, see 104 |
| other mods | bodies: `RemoteBody.register(entity -> body)` at common setup (status, cells, ammo, `connectRefusal`, `connect`). Apps: `PdaApps.register` at client setup; panels `PdaShell.panel`; feed rights for non-`CHANNELS` links: `CameraNet.entitle` |

## 100. Uplink (`pda/uplink/Uplink`)

- Own net (`ReconOwners.netOf`) only; no operator override. GROUND = online `GridNode` of that net within its `linkRange` (3D, node eye); RELAY = `OrbitalNet.overhead` relay (`SatPayloads.RELAY`) `inContact`. Cached 10 ticks.
- Gates: PDA radar frames, `SurveyRequestPacket` (else `mayChooseAnyNet` for the debug survey screen). In-world hub / scope / surveillance frames stay chunk-tracker broadcast (display physically present).
- Gap: relay path unverified (no orbital gametest).

## 101. Embedded screen (`client/screen/EmbeddedScreen`)

- Hosted `Screen` drawn to an FBO of the host's px size; while it runs: `Window` size / GUI scale / scaled size (`MixinWindowEmbedded`), main target, `MouseHandler.xpos/ypos`, `minecraft.screen` = its own.
- GUI scale = largest <= window's leaving >= 320x180 GUI px (vanilla 320x240 => scale 1 on a 16:9 app area).
- `setScreen` inside => captured as next screen; `null` => ends; `Standalone` (`TextPromptScreen`) => real `setScreen` after the run, window-sized; PDA stays raised over it.
- Direct `minecraft.screen` writes (JourneyMap `LayeredScreen` popup display / `popLayer`) => swap.
- `MouseHandler.setIgnoreFirstMove` cancelled while running (JourneyMap drag start => lost PDA cursor delta).
- Gap: Xaero embedding unverified.

## 102. Alerts (`pda/alerts/PdaAlert`, client `PdaAlerts`)

- Carried PDA only: HUD toast top-left (3 newest, 5 s; top-right = minimaps) + `wflib:pda.buzz` (`tools/audio/pda_buzz_synth.py`) + history.
- Server: `PdaAlert.send(player, text, tier)`; recon alerts (track -> ALARM / WEAPONS_RELEASE) of own net within 4096 while uplinked, forwarded once per published picture; first sight of a net skipped (stale).
- Client posts: sat overhead / hunter inbound (status transitions), TV link lost. CombinedAssault: UAV low fuel / charge (< 15 %, re-arm > 25 %), fire request received.

## 103. Dispatch (`drone/dispatch/DroneDispatcher`)

- Register while loaded: `DroneDispatchers.add` / `remove`. Permitted: `side()` null, same side, or WarForge allies; no range limit, no uplink gate.
- `DronePadBlockEntity`: idle non-`DroneEntity` body within 8 blocks flying the kind takes it first (`DroneTasking.assign`, home = pad); else offers LOITER; DELIVER with pad cargo (rides leader); STRIKE / MINELAY when the configured mission slings a warhead / mines. Run = configured mission copy, DIRECT, program = one step, home = pad, operator = dispatching player (hears the datalink without a side).

## 104. Wiki (`wiki/`, client `wiki/client/`)

- Article = client resource `assets/<ns>/wiki/<path>.md` -> id `<ns>:<path>`; `WikiLoader` reload listener replaces all (`Wiki.replace`); packs add / override.
- Markdown: commonmark-java 0.24.0 (jarJar) + tables, strikethrough, task lists, front matter. `TaskListItemMarker` = ListItem child BEFORE the `Paragraph`.
- Front matter, all optional: `title` (= first heading), `summary` (= first paragraph), `icon` (item id | `.png` texture id; = first bound item), `items` (`ns:id` | `#tag`), `keywords`, `priority` (higher first), `hidden` (links + bindings only).
- Links: `http(s)`/`mailto` external; `#a` same article; `ns:path`; `/path` ns root; else relative to folder, `../` ok, `.md` optional; unknown => red. Images: `item:<id>` inline icon; else `Wiki.resolveAsset` (relative to article folder under `wiki/`), scaled down to page width.
- `ArticleLayout`: markdown -> boxes / pics / spans / hit table; unit-testable via `Metrics`; relaid on width change. `WikiApp`: list + search (`Wiki.search`: title prefix > word > substring > keyword > summary > body), article view, history (back, INDEX), scroll kept per history entry.
- Binding: `Wiki.forStack` (highest priority match, cached per `Item`, cleared on `TagsUpdatedEvent` / reload). `ItemTooltipEvent` line `wflib.wiki.tooltip` on bound items.
- F1 (`wflib.wiki` key + `MixinKeyboardHandler.keyPress` HEAD: vanilla F1 hide-GUI is not a key mapping; also `ScreenEvent.KeyPressed.Pre` in containers): `WikiAccess` -> bound stack (hovered slot, else main hand, offhand, hotbar) -> held PDA, else carried PDA moved to hand => `PdaClient.raiseWhenHeld` -> Wiki app on article. No PDA => nothing.
- Writing guide: `assets/wflib/wiki/guides/writing-articles.md`.
- Gap: texture image embed, offhand / container F1 paths unverified in game.

## 105. Music (`pda/music/`, client `pda/music/client/`)

- Sources: SERVER `<world>/pda_music/**`, LOCAL `<gameDir>/pda_music/**`; `.ogg|.oga|.opus`, depth <= 4, no symlinks, <= 64 MiB, <= 512 server / 2000 local entries.
- Playback client-local only: head-locked `MusicSound` (`wflib:pda_music`, source MASTER, `canStartSilent`), nothing broadcast. Stops when PDA leaves inventory / logout.
- `OggAudio.probe` = gate for EVERY byte path (server scan, server serve, local scan, playback); nothing native sees unprobed bytes:
  - single logical stream, per-page CRC, contiguous sequence, monotonic granule, BOS first / EOS last, no trailing data
  - Vorbis: id/comment/setup headers, mono|stereo, 8-192 kHz; Opus: OpusHead family 0, mono|stereo
  - corrupt/truncated/random => `IOException`; tags tolerant (partial kept)
  - Vorbis additionally trial-decoded by STB before play (`PcmSource.Vorbis`)
- Names: `OggAudio.validName` + `resolve().normalize().startsWith(root)` + `NOFOLLOW_LINKS`.
- Server download: `MusicFetchPacket(transfer, name)` -> `MusicChunkPacket` x 60000 B, 4 chunks/tick/player, one transfer per player; `total == 0` = refused. Client holds the file in memory (2 server tracks cached), re-probes.
- Metadata: Vorbis comment / OpusTags `TITLE ARTIST(ALBUMARTIST) ALBUM`; cover = `METADATA_BLOCK_PICTURE` (front cover preferred) or `COVERART`, PNG|JPEG magic, <= 8 MiB, <= 2048 px (STB), letterboxed square, texture `wflib:pda_music/cover`.
- Opus = Concentus via `OggOpus` (shared with radio); Vorbis = LWJGL `STBVorbis` (`NativeBytes` refcount: source + readers).
- Playlists + volume / shuffle / repeat: `config/wflib_pda_music.json`, entries `(src, file)`; missing files shown + skipped.
- Seek: bar drag debounced 6 ticks then decoder restart. Position = samples read - 4 engine buffers (~0.3 s lead).
- Gaps: mono|stereo only; no upload; no gapless; PDA grid 12 tiles (Music = 6th); server list refresh on tab / `R`.
- Tests: `OggAudioTest` (fixtures `src/test/resources/pda_music`). Harness-verified: local + server tabs, Opus + Vorbis audio (wave backend: 330 / 440 Hz), cover, pause / resume / seek, playlist create + add + persist. Unverified: dedicated server serve (SP integrated only), shuffle ordering, JPEG cover.

## 106. Theme (`client/PdaTheme`, `SettingsApp`, `music/BackgroundImage`)
- Settings app: accent / text / panel hex (`#RGB` | `#RRGGBB`, live), panel opacity (0.05-1), background. `config/wflib_pda.json`, saved 0.7 s after last change.
- `PdaShell.BACK|BAR_BACK|PANEL|HOVER|PRESS|EDGE|TEXT|DIM|ACCENT` = `pda.th_*` signals, not literals => every app themed. `PANEL` = opaque button fill; `panel()` = opacity-scaled fill. hover / press / edge / dim / back / bar = mixes of the three colours. `ALERT` fixed.
- Canvas-drawn apps: `PdaTheme.accent()|text()|dim()`. Gap: `TextField` ints + already-logged terminal lines keep build-time colours.
- Background: SERVER `<world>/pda_backgrounds/*.{png,jpg,jpeg}` (flat, <= 64, <= 4 MiB, <= 2048 px). `BackgroundImage.check` = signature + IHDR / SOF dims + IEND / EOI tail. Same fetch/chunk path as music (`MusicFetchPacket.background`, one transfer per (player, kind)); client re-decodes with STB, centre-crop to 384x240, box-shrink to <= 768 px. Saved name re-requested once per world join; missing => status text.
- Tests: `BackgroundImageTest`. Harness-verified: list, fake.png rejected, PNG background, hex accent, opacity, persistence. Unverified: JPEG background, dedicated server.
