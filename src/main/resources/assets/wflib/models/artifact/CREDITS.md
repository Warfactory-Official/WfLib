# Artifact models

STALKER Anomaly's artefact meshes (GSC Game World / Anomaly), converted by devtools/xray-ogf.py; textures in
`textures/artifact/`:

- `battery.glb`: `af_dummy_battery.ogf`; `sopli2.png` (artifact_sopli2), `battery_lightning.png`
  (artifact_anim_lightning).
- `soul.glb`: `af_soul.ogf` as GAMMA ships it ("Fixed Artifact for Anomaly" by Strogglet15), its shell only;
  `sopli2.png`. Its glowing planes (pfx_anomaly_4, `soul_glow.png`) are drawn as a sprite turned to the viewer
  (`core.glb`, one quad, ours); its outer artifact_hell1 shell is a distortion (xdistortcolor), not drawn.

- The 2026-10-28 batch, `<id>.glb` from these meshes (GAMMA's winning copy; "Fixed" = "Fixed Artifact for Anomaly"
  by Strogglet15, else Anomaly's base archives; `artefact_itcher` / `artefact_ring` from the base archives). Textures are the meshes' own `textures/artifact/*.dds`; `*_bloom.png`
  are ours (Gaussian blur). `ring.glb` is scaled x4 and stood upright (a 6 cm finger ring read as a sliver).
  - Fixed: `fireball` (af_fireball), `moonlight` (af_electra_moonlight), `flash_artifact` (af_electra_flash),
    `ice` (af_ice), `stone_flower` (af_cristall_flower), `sponge` (artefact_sponge).
  - Base: `crystal` (af_cristall), `sparkler` (af_electra_sparkler), `glass_beads` (af_dummy_glassbeads), `dummy`
    (af_dummy_dummy), `itcher` (artefact_itcher), `ring` (artefact_ring).
  - Distortion parts (xdistort: Crystal's and Sparkler's outer shells) are not drawn.
- The 2026-10-29 batch, same sources; `pfx_anomaly_*` textures from `textures/pfx/`.
  - Fixed: `stone_blood` (af_blood), `lobster_eyes` (artefact_lobster_eyes), `jellyfish` (af_medusa), `meat_chunk`
    (af_mincer_meat), `pin` (artefact_pin), `black_spray` (artefact_black_spray), `bracelet` (artefact_bracelet),
    `flame` (af_fire), `kolobok` (af_fuzz_kolobok), `firefly` (af_glass), `wrenched` (af_vyvert).
  - Base: `bubble` (af_baloon), `eye` (af_eye), `pellicle` (artefact_plenka_coc), `oasis_heart` (af_cristall_plant).
  - Not drawn: Bubble's and Wrenched's xdistort shells, Heart of the Oasis' `pautina` copy of its glow card.
- 2026-10-29 batch 3, same sources; `sap/*` and `amk/arts/*` textures from GAMMA's mods or Anomaly's base archives.
  - Base: `droplets` (artefact_kaply), `crystal_thorn` (artefact_needles2), `sea_urchin` (af_rusty_sea_urchin),
    `thorn` (artefact_needles1), `spring` (artefact_pustishka).
  - "New Storylines (DLTX minimodpack)" by Demonized: `lamp` (af_fonar).
  - Fixed, the rest: `compass`, `death_lamp` (artefact_death_lamp), `empty` (artefact_empty), `full_empty`
    (artefact_full_empty), `goldfish` (af_gold_fish), `gravi`, `volat_emerald` (af_monolith), `nightstar`
    (af_night_star); Perk-Based Artefacts' `ball`, `bat`, `black_angel`, `cell`, `chain` (af_chelust), `cocoon`,
    `dragon_eye`, `electron` (af_elektron), `fountain`, `generator`, `claw` (af_kogot), `beacon` (af_lighthouse),
    `medal` (af_medallion), `moss` (af_moh), `moon` (af_peas), `thistle` (af_repei), `sandstone`, `seraphim`
    (af_serofim), `misers_skull` (af_skull_miser), `spike` (af_spaika), `phantom_star` (af_star_phantom), `sun`,
    `tapeworm`, `scar` (af_zhelch).
  - Electron's, Lamp's and Goldfish's xdistort shells drawn as veils (nothing else solid); Black Angel's not drawn.
  - `*_solid.png`: a body's opaque copy of a texture another part draws blended.

Licence unrecorded: for this internal build only; clear or replace before anything ships.

## ../detector/ (models) and textures/detector/

`echo`, `bear`, `veles`: the Echo, Bear and Veles first-person detectors of GAMMA's "Devices of Anomaly Redone"
(BarryBogs): `dev_diolator_detector_1/2/3_hud.ogf`, textures `item_diolator_detector_1/2/3` (brightened: STALKER lights
them with specular) and `item_emissions` (`lights`). Third-party art, internal use; converted with devtools/xray-ogf.py.
`textures/item/bolt.png`: GAMMA Icons `ui_icon_equipment` cell (9, 23) (`m_bolt.ltx` inv_grid), 32 px. Impact sounds
`sounds/xray/bolt/*`: GAMMA "Oleh's Bolt Impact Sounds" `material/bolt/collide`, downmixed to mono. Internal use.
