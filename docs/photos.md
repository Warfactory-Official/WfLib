# Photos

System `photo` (`photo/`, client `photo/client/`). Requires `pda` (gallery app) and `cam` (the shot is an `OffscreenView`). Items `wflib:camera`, `wflib:photo`. Config `wflib-photo.toml` (COMMON).

## Flow

| step | where | what |
|---|---|---|
| use | server | `CameraItem.use` => `PhotoServer.shutter`: refuses at `maxPerPlayer` photos or without `filmItem`; item cooldown `cooldownTicks`; token = random long, kept 400 ticks; posts `PhotoTakenEvent` |
| shutter | S2C | `PhotoShutterPacket(token, eye, yaw, pitch)`: the pose the server holds |
| render | client | `PhotoCapture.onFrame` (`RenderFrameEvent.Pre`): `OffscreenView.sharedGrid()` into a 960x540 target at `PhotoLimits.FOV` (70), roll 0; no HUD, ignores the player's FOV setting; `false` (grid rebuilding) => retry up to 30 frames |
| encode | client, worker | `Screenshot.takeScreenshot` => RGBA => STB JPEG, quality 82, then 65/48/32 until <= `MAX_BYTES` |
| upload | C2S | `PhotoUploadPacket(token, index, total, bytes)`, `UP_CHUNK` 30000 (a client payload is capped near 32 KiB), 3 per tick |
| store | server | `Upload` reassembles; `Jpeg.size` checks the header (never decoded): 64..1280 x 64..720, <= 250000 bytes; file `<world>/wflib_photos/<uuid>.jpg`; entry in `PhotoBook`; posts `PhotoStoredEvent`; pushes the list |

A shot is accepted only for the sender's pending token, within 400 ticks. A repeated or out-of-range fragment index, a wrong `total`, an oversized fragment or a running total over the cap kills the upload (`Upload.dead`) and the shot is lost.

## Gallery and viewing

- `PhotoListRequestPacket` => `PhotoListPacket` (own photos, newest first, <= 512 rows; 500 ms between requests).
- Fetch: `PhotoFetchPacket(transfer, id)` => `PhotoChunkPacket`s (`DOWN_CHUNK` 60000, 4 per tick per player, same shape as the music transfer); `total == 0` = refused (unknown id, not the owner and not printed, or too many loads in flight). One transfer per player at a time; files are re-checked as JPEG before they are served.
- Client `PhotoImages`: decode on a worker (STB), `DynamicTexture` per photo, LRU 24.
- `PhotoApp` (PDA app `wflib:photos`): list left, selected photo right (16:9), VIEW opens `PhotoScreen`, PRINT, DELETE (second click within 3 s confirms). The selected photo is shown through the fixed texture `wflib:photo/pda_shown`, an alias that adopts the current photo texture's GL id.
- `PhotoScreen`: fit-to-screen viewer; own photos also get Print and Delete.

## Printing

`PhotoActionPacket(PRINT, id)` (owner only): consumes `printItem` (default paper; creative free), marks the photo `shared`, gives a `wflib:photo` carrying `wflib:photo` = `PhotoRef(id, author)`. Using the item opens the viewer. Anyone may fetch a shared photo by id; unprinted photos are served to the owner only. `DELETE` removes the index entry and the file.

## API (`photo.api`)

| event | when | carries |
|---|---|---|
| `PhotoTakenEvent` | shutter fires, before any image | player, level, eye, yaw, pitch, fov: the pose the picture is rendered from; use it for "was X in frame" checks |
| `PhotoStoredEvent` | image saved | owner, photo id |

## Limits

Shared in `PhotoLimits`. Quota refuses new shots rather than evicting old ones. Orphaned files from a failed delete stay on disk but are not indexed, so never served.

## Not done

Wall frames; remote-camera and thermal/night photos (the `OffscreenView` supports them, the shutter does not request them); per-photo comments; sharing a photo with a specific player without printing.
