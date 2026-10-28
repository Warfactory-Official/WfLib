"""Round-trip the shipped GLBs and compare every device clip against the editable rig.

blender -b --factory-startup --python-exit-code 1 --python art/detectors/validate.py
"""
import json
from pathlib import Path

import bpy
from mathutils.bvhtree import BVHTree

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / 'art/detectors'
ASSETS = ROOT / 'src/main/resources/assets/wflib'
report = json.loads((ART / 'report.json').read_text())
bpy.ops.wm.open_mainfile(filepath=str(ART / 'detectors.blend'))
scene = bpy.context.scene
results = {}


def action_on(arm, action):
    arm.animation_data_create()
    arm.animation_data.use_nla = False
    arm.animation_data.action = action
    arm.animation_data.action_slot = action.slots[0]


def vertices(ob):
    evaluated = ob.evaluated_get(bpy.context.evaluated_depsgraph_get())
    mesh = evaluated.to_mesh()
    points = [evaluated.matrix_world @ v.co for v in mesh.vertices]
    faces = [list(p.vertices) for p in mesh.polygons]
    evaluated.to_mesh_clear()
    return points, faces


for index, (name, info) in enumerate(report.items()):
    reference = bpy.data.objects[name + '_rig']
    reference.location.x -= index * 0.16
    ref_meshes = {o['detector_slot']: o for o in bpy.data.collections[name + '_lowpoly'].objects
                  if o.type == 'MESH'}
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=str(ASSETS / 'models/detector/lowpoly' / (name + '.glb')))
    imported = set(bpy.data.objects) - before
    arm = next(o for o in imported if o.type == 'ARMATURE')
    actual_meshes = {o['detector_slot']: o for o in imported if o.type == 'MESH' and 'detector_slot' in o}
    assert actual_meshes.keys() == ref_meshes.keys()
    assert set(b.name for b in arm.data.bones) == set(b.name for b in reference.data.bones)
    for bone in arm.data.bones:
        expected = reference.data.bones[bone.name]
        assert (bone.parent.name if bone.parent else None) == (expected.parent.name if expected.parent else None)
    clips = {t.name: t.strips[0].action for t in arm.animation_data.nla_tracks}
    assert clips.keys() == {a['name'] for a in info['animations']}, (name, clips.keys())
    max_error = 0.0
    poses = 0
    for clip in info['animations']:
        source = bpy.data.actions[clip['name']]
        action_on(reference, source)
        action_on(arm, clips[clip['name']])
        lo, hi = clip['source_frames']
        for fraction in (0.0, 0.5, 1.0):
            frame = lo + (hi - lo) * fraction
            scene.frame_set(int(frame), subframe=frame % 1)
            expected = {slot: vertices(ob) for slot, ob in ref_meshes.items()}
            export_frame = frame / clip['source_speed']
            scene.frame_set(int(export_frame), subframe=export_frame % 1)
            bpy.context.view_layer.update()
            for slot, ob in actual_meshes.items():
                points, faces = vertices(ob)
                expected_points, expected_faces = expected[slot]
                expected_tree = BVHTree.FromPolygons(expected_points, expected_faces)
                actual_tree = BVHTree.FromPolygons(points, faces)
                deviation = max([expected_tree.find_nearest(v)[3] for v in points] +
                                [actual_tree.find_nearest(v)[3] for v in expected_points])
                max_error = max(max_error, deviation)
                # Sampling fractional source-speed frames can interpolate across one 30 Hz sample.
                assert deviation < 0.001, (name, clip['name'], fraction, slot, deviation)
            poses += 1
    results[name] = {'animations': len(clips), 'poses_checked': poses,
                     'maximum_roundtrip_error_mm': round(max_error * 1000, 6)}
(ART / 'validation.json').write_text(json.dumps(results, indent=2) + '\n')
print('VALIDATED', json.dumps(results), flush=True)
