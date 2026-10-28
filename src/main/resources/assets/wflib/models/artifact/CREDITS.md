# Artifact models

STALKER Anomaly's artefact meshes (GSC Game World / Anomaly), converted by tools/xray-ogf.py; textures in
`textures/artifact/`:

- `battery.glb`: `af_dummy_battery.ogf`; `sopli2.png` (artifact_sopli2), `battery_lightning.png`
  (artifact_anim_lightning).
- `soul.glb`: `af_soul.ogf` as GAMMA ships it ("Fixed Artifact for Anomaly" by Strogglet15), its shell only;
  `sopli2.png`. Its glowing planes (pfx_anomaly_4, `soul_glow.png`) are drawn as a sprite turned to the viewer
  (`core.glb`, one quad, ours); its outer artifact_hell1 shell is a distortion (xdistortcolor), not drawn.

Licence unrecorded: for this internal build only; clear or replace before anything ships.

## ../detector/ (models) and textures/detector/

`echo`, `bear`, `veles`: the Echo, Bear and Veles first-person detectors of GAMMA's "Devices of Anomaly Redone"
(BarryBogs): `dev_diolator_detector_1/2/3_hud.ogf`, textures `item_diolator_detector_1/2/3` (brightened: STALKER lights
them with specular) and `item_emissions` (`lights`). Third-party art, internal use; converted with tools/xray-ogf.py.
