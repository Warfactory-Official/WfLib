# Echo, Bear and Veles: rigged low-poly variants

`detectors.blend` is the editable project, with packed WFLib PNG textures, hidden original/source collections,
three low-poly rigs, all 50 device actions, and a review camera. The original scene and detector assets were
not replaced. Runtime assets are `src/main/resources/assets/wflib/models/detector/lowpoly/{echo,bear,veles}.glb`.

| Detector | Original triangles | Variant triangles | Reduction | Bones | Device clips |
|---|---:|---:|---:|---:|---:|
| Echo | 11,038 | 1,270 | 88.5% | 7 | 16 |
| Bear | 14,638 | 1,506 | 89.7% | 9 | 18 |
| Veles | 5,205 | 1,239 | 76.2% | 14 | 16 |

Counts above describe the GLBs. Blender may count extra degenerate triangles that its glTF exporter drops;
`blender_triangles` records that separately.

## Source study

The existing WFLib GLBs contain only static meshes and material slots: no joints, skin weights or animations.
They are centred bind-pose conversions of the first-person models from GAMMA's
`416- Devices of Anomaly Redone - BarryBogs`, by BarryBogs and Diolator. WFLib's
`tools/gamma/gamma_find.py` confirmed that mod supplies the winning OGF and OMF files for all three detectors
in the installed MO2 profile. The original assets, not a video or guessed hinges, are the rig reference.

- `source-study.json`: original geometry/material counts, bone parents, pivots/rest matrices, clip ranges,
  FPS/speed, source paths relative to gamedata, and SHA-256 hashes.
- `states.json`: DAR's HUD animation overrides, paired hand/item clip names, display attachment and HUD transforms.
  All 64 explicitly overridden states resolve to retained device clips. Walking and sprinting multipliers remain
  in this manifest (1.15 and 1.4); the GLB carries the device clip at its own OMF speed.
- `report.json`: generated part budgets, vertex-to-surface deviation measurements, resulting skeleton and clips.
- `validation.json`: reimport/animation check results.

OGF/OMF import used [PavelBlend/blender-xray](https://github.com/PavelBlend/blender-xray), version 2.47.0.
Its temporary checkout needed `cls.__annotations__` in place of `cls.__dict__['__annotations__']` for Blender
5.2's Python. That importer is not a runtime dependency; rebuilding from the packed project needs only Blender.

## What is rigged

Bone names, hierarchy, rest transforms and rigid weights are retained from each source detector.
No bone was discarded just because it has no mesh weights: animation/engine attachment bones still export.

**Echo — 7 bones**

- `wpn_body`: instrument housing and its whole-device motion.
- `lid`: hinged aerial/grid cover; its alpha-cutout insert follows the same bone.
- `button`, `on_switch`: independently weighted controls.
- `light_bone_1`, `light_bone_2`: body indicators.
- `light_bone_3`: cover indicator, parented to `lid` so it follows the hinge.

**Bear — 9 bones**

- `wpn_body`: housing and circular receiver.
- `cover`: hinged wire cage. Its rounded outline and radial ribs receive a separate geometry budget.
- `tuner_bone`, `on_switch`: rotary tuning and power controls.
- `toggle`: original attachment/control bone, retained although the source assigns it no vertices.
- `screen_bone`, `screen_bone_1`, `light_bone_1`, `light_bone_2`: independently driven face/indicator parts.

**Veles — 14 bones**

- `wpn_body`: housing.
- `cover`: folding display housing.
- `cover_power`: display/power surface, a child of `cover`. DAR explicitly names this as `detector_ui_bone`.
  Attach a dynamic display to this bone, not the stationary housing.
- `joint1`: lower hinged fitting/latch.
- `switch`: side control.
- `light_bone_1` through `light_bone_9`: separate indicators retained with their original weights and pivots.

## Animations and scope

The variants include the actual device actions for draw, holster, idle, walk, sprint, aim transitions,
quick draw/hide and bolt-throw transitions. Bear also keeps its failed/dead-detector draw. The extra source
Echo zoom draw/holster clips remain even when DAR aliases an engine state to another clip.
Original `barry_<detector>_*` names are retained. Source FPS is 30; Echo's sprint OMF speed is 0.6 and is
baked into the export. Fractional-speed endpoints are sampled to the 30 Hz export grid.

These are **device rigs and device animations**. GAMMA animates the player's arms/fingers with a separate
hand skeleton and separate hand clips; those are named in `states.json`, not replaced with fabricated bones
on the detector. The item motions retain their source HUD-space movement. Integrating/retargeting player
hands and driving the clips from Minecraft gameplay remains a viewmodel task.

The default `DetectorTier.model()` paths are unchanged; these are separate variants, not an automatic switch
of the current game renderer. GLBs retain WFLib's `body`, `aref` (Echo only) and emissive `lights` texture slots.
They use metres and the existing centred bind-pose convention. The GLBs deliberately carry resource-pack
material slots rather than embedding PNGs, just like the current detector assets; use the packed `.blend`
for a textured preview outside GemRender. Joining by material preserves two meshes/draws for Bear/Veles and
three for Echo, while all rigid parts keep their own bone weights.

## Rebuild and validate

From the WFLib repository root:

```sh
blender -b --factory-startup --python-exit-code 1 --python art/detectors/build.py
blender -b --factory-startup --python-exit-code 1 --python art/detectors/validate.py
```

The builder splits by rigid bone before planar cleanup and reduction, so collapsing edges never joins two
moving pieces. It checks UVs, unit weights, source/rest matrices, joint/clip preservation and reduction, then
exports and checks the GLBs. Surface deviations measure removed bevels/small details too; the largest measured
bind-pose vertex-to-surface deviation is below 2.5 mm. These are surface samples, not a continuous error bound.

The validator reimports the GLBs and compares the deformed surfaces at the start, midpoint and end of every
clip (150 poses total), checks bone names/parents and material-mesh count, and requires less than 1 mm
round-trip discrepancy. This validates the asset exports, not Minecraft/GemRender runtime playback.

## Credits and use

These are reductions/rig-preserving derivatives of the existing third-party GAMMA detector art and animations,
not newly licensed original models. The same internal-use / unrecorded-licence limitation recorded in
`src/main/resources/assets/wflib/models/artifact/CREDITS.md` still applies. Source game/mod art was not edited.
