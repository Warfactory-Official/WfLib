# Artifact models

STALKER Anomaly's artefact meshes (GSC Game World / Anomaly), converted by devtools/xray-ogf.py; textures in
`textures/artifact/`:

- `battery.glb`: `af_dummy_battery.ogf`; `sopli2.png` (artifact_sopli2), `battery_lightning.png`
  (artifact_anim_lightning).
- `soul.glb`: `af_soul.ogf` as GAMMA ships it ("Fixed Artifact for Anomaly" by Strogglet15), its shell only;
  `sopli2.png`. Its glowing planes (pfx_anomaly_4, `soul_glow.png`) are drawn as a sprite turned to the viewer
  (`core.glb`, one quad, ours); its outer artifact_hell1 shell is a distortion (xdistortcolor), not drawn.

- The 2026-10-28 batch, `<id>.glb` from these meshes (GAMMA's winning copy; "Fixed" = "Fixed Artifact for Anomaly"
  by Strogglet15, else Anomaly's base archives; `artefact_itcher` / `artefact_ring` from the base archives, the mod
  copies use a layout xray-ogf can't read). Textures are the meshes' own `textures/artifact/*.dds`; `*_bloom.png`
  are ours (Gaussian blur). `ring.glb` is scaled x4 and stood upright (a 6 cm finger ring read as a sliver).
  - Fixed: `fireball` (af_fireball), `moonlight` (af_electra_moonlight), `flash_artifact` (af_electra_flash),
    `ice` (af_ice), `stone_flower` (af_cristall_flower), `sponge` (artefact_sponge).
  - Base: `crystal` (af_cristall), `sparkler` (af_electra_sparkler), `glass_beads` (af_dummy_glassbeads), `dummy`
    (af_dummy_dummy), `itcher` (artefact_itcher), `ring` (artefact_ring).
  - Distortion parts (xdistort: Crystal's and Sparkler's outer shells) are not drawn.

Licence unrecorded: for this internal build only; clear or replace before anything ships.

## ../detector/ (models) and textures/detector/

`echo`, `bear`, `veles`: the Echo, Bear and Veles first-person detectors of GAMMA's "Devices of Anomaly Redone"
(BarryBogs): `dev_diolator_detector_1/2/3_hud.ogf`, textures `item_diolator_detector_1/2/3` (brightened: STALKER lights
them with specular) and `item_emissions` (`lights`). Third-party art, internal use; converted with devtools/xray-ogf.py.
`bolt`: S.T.A.L.K.E.R. Anomaly's bolt, GAMMA "Oleh's Bolt Impact Sounds" `dev_bolt.ogf`, texture `wpn_bolt` (base
textures_weapons.db0). Internal use.
