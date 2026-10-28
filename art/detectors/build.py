"""Build rigged low-poly detectors from the source collections in detectors.blend.

blender -b --factory-startup --python art/detectors/build.py -- [source.blend]
Source collections and BarryBogs item actions are retained in the editable output.
"""
import json
import math
from pathlib import Path
import struct
import sys

import bmesh
import bpy
from mathutils import Matrix, Vector
from mathutils.bvhtree import BVHTree

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / 'art/detectors'
ASSETS = ROOT / 'src/main/resources/assets/wflib'
OUT = ASSETS / 'models/detector/lowpoly'
# Per rigid part: the cage/cover silhouette gets more geometry than a button.
BUDGETS = {
    'echo': {'wpn_body': 800, 'lid': 240, 'button': 48, 'on_switch': 24},
    'bear': {'wpn_body': 650, 'cover': 650, 'tuner_bone': 80, 'on_switch': 24},
    'veles': {'wpn_body': 650, 'cover': 280, 'joint1': 120, 'switch': 24},
}


def triangles(mesh):
    return sum(len(p.vertices) - 2 for p in mesh.polygons)


def material(name, slot):
    mat = bpy.data.materials.new(name + '_' + slot)
    mat.use_nodes = True
    mat['textureSlot'] = slot
    shader = next(n for n in mat.node_tree.nodes if n.type == 'BSDF_PRINCIPLED')
    shader.inputs['Roughness'].default_value = 0.75
    mat.use_backface_culling = slot != 'aref'
    tex = mat.node_tree.nodes.new('ShaderNodeTexImage')
    tex.image = bpy.data.images.load(str(ASSETS / 'textures/detector' /
                                        ('lights.png' if slot == 'lights' else name + '.png')), check_existing=True)
    mat.node_tree.links.new(tex.outputs['Color'], shader.inputs['Base Color'])
    if slot == 'lights':
        mat.node_tree.links.new(tex.outputs['Color'], shader.inputs['Emission Color'])
        shader.inputs['Emission Strength'].default_value = 1.0
    elif slot == 'aref':
        mat.node_tree.links.new(tex.outputs['Alpha'], shader.inputs['Alpha'])
    return mat


def split_part(source, group, collection, arm, slot):
    ob = source.copy()
    ob.data = source.data.copy()
    ob.name = arm.name.removesuffix('_rig') + '_' + slot + '__' + group.name
    collection.objects.link(ob)
    bm = bmesh.new()
    bm.from_mesh(ob.data)
    deform = bm.verts.layers.deform.active
    bmesh.ops.delete(bm, geom=[v for v in bm.verts if v[deform].get(group.index, 0.0) < 0.5], context='VERTS')
    bm.to_mesh(ob.data)
    bm.free()
    ob.vertex_groups.clear()
    vg = ob.vertex_groups.new(name=group.name)
    vg.add(list(range(len(ob.data.vertices))), 1.0, 'REPLACE')
    ob.modifiers.clear()
    ob.parent = arm
    ob.matrix_parent_inverse = Matrix.Identity(4)
    ob.matrix_basis = Matrix.Identity(4)
    return ob


def simplify(ob, target):
    bpy.context.view_layer.objects.active = ob
    ob.select_set(True)
    # Remove triangulation inside flat UV-continuous surfaces before reducing curved parts.
    mod = ob.modifiers.new('Planar cleanup', 'DECIMATE')
    mod.decimate_type = 'DISSOLVE'
    mod.angle_limit = math.radians(1.0)
    mod.delimit = {'UV', 'SHARP', 'MATERIAL'}
    bpy.ops.object.modifier_apply(modifier=mod.name)
    count = triangles(ob.data)
    if count > target:
        if len(ob.data.polygons) <= 3:
            mod = ob.modifiers.new('Triangulate planar faces', 'TRIANGULATE')
            bpy.ops.object.modifier_apply(modifier=mod.name)
        mod = ob.modifiers.new('Low-poly silhouette', 'DECIMATE')
        mod.ratio = target / count
        mod.use_collapse_triangulate = True
        bpy.ops.object.modifier_apply(modifier=mod.name)
    ob.select_set(False)
    assert ob.data.polygons, ob.name + ': simplification removed the part'
    assert all(len(v.groups) == 1 and abs(v.groups[0].weight - 1) < 1e-6 for v in ob.data.vertices), ob.name
    assert ob.data.uv_layers, ob.name + ': missing UVs'


def geometry_error(source, output):
    """Sample source and result against each other's triangles, in bind-pose coordinates."""
    def tree(mesh):
        return BVHTree.FromPolygons([v.co for v in mesh.vertices], [list(p.vertices) for p in mesh.polygons])
    result = 0.0
    for mesh, other in [(source, output), (output, source)]:
        bvh = tree(other)
        for v in mesh.vertices:
            hit = bvh.find_nearest(v.co)
            assert hit[0] is not None
            result = max(result, hit[3])
    return result


def patch_glb(path):
    data = path.read_bytes()
    size = struct.unpack_from('<I', data, 12)[0]
    doc = json.loads(data[20:20 + size])
    binary = data[28 + size:]
    # Resource-pack textures and existing per-draw material slots, like xray-ogf.py.
    for mat in doc['materials']:
        slot = mat['extras']['textureSlot']
        mat['name'] = slot
        mat['extras'] = {'textureSlot': slot}
        mat['pbrMetallicRoughness'] = {'metallicFactor': 0.0, 'roughnessFactor': 0.8}
        mat.pop('emissiveTexture', None)
        mat.pop('emissiveFactor', None)
        mat.pop('extensions', None)
        mat.pop('doubleSided', None)
        if slot == 'lights':
            mat['alphaMode'] = 'BLEND'
        elif slot == 'aref':
            mat.update(alphaMode='MASK', alphaCutoff=0.5, doubleSided=True)
    for key in ['images', 'textures', 'samplers', 'extensionsUsed', 'extensionsRequired']:
        doc.pop(key, None)
    for node in doc['nodes']:
        extras = node.get('extras', {})
        extras.pop('xray', None)
        if not extras:
            node.pop('extras', None)
    doc['asset']['extras'] = {'source': 'GAMMA Devices of Anomaly Redone / BarryBogs & Diolator',
                             'variant': 'lowpoly', 'units': 'metres'}
    encoded = json.dumps(doc, separators=(',', ':')).encode()
    encoded += b' ' * (-len(encoded) % 4)
    path.write_bytes(struct.pack('<III', 0x46546C67, 2, 28 + len(encoded) + len(binary)) +
                     struct.pack('<II', len(encoded), 0x4E4F534A) + encoded +
                     struct.pack('<II', len(binary), 0x004E4942) + binary)
    return doc


def build(source):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.name = 'Low-poly Detector Workshop'
    scene.render.fps = 30
    scene.frame_start, scene.frame_end = 0, 650
    with bpy.data.libraries.load(str(source), link=False) as (available, loaded):
        loaded.collections = [n for n in available.collections if n.endswith('_source')]
        loaded.actions = [n for n in available.actions if n.startswith('barry_')]
    assert len(loaded.collections) == 3
    for col in loaded.collections:
        scene.collection.children.link(col)
    report = {}
    for name, budgets in BUDGETS.items():
        sources = bpy.data.collections[name + '_source']
        src_arm = next(o for o in sources.objects if o.type == 'ARMATURE')
        src_arm.animation_data_clear()
        for bone in src_arm.pose.bones:
            bone.matrix_basis = Matrix.Identity(4)
        meshes = sorted([o for o in sources.objects if o.type == 'MESH'], key=lambda o: o.name)
        points = [v.co for o in meshes for v in o.data.vertices]
        center = Vector([(min(v[i] for v in points) + max(v[i] for v in points)) / 2 for i in range(3)])
        collection = bpy.data.collections.new(name + '_lowpoly')
        scene.collection.children.link(collection)
        arm = src_arm.copy()
        arm.data = src_arm.data.copy()
        arm.name = name + '_rig'
        collection.objects.link(arm)
        arm.location = -center
        arm.show_in_front = True
        for bone in arm.data.bones:
            bone.use_deform = True
        parts = []
        for index, mesh in enumerate(meshes):
            slot = ('body' if index == 0 else 'aref') if index < len(meshes) - 1 else 'lights'
            mat = material(name, slot)
            mesh.data.materials.clear()
            mesh.data.materials.append(mat)
            assert all(len([g for g in v.groups if g.weight > 1e-5]) == 1 for v in mesh.data.vertices), mesh.name
            for group in mesh.vertex_groups:
                if not any(any(g.group == group.index and g.weight > 0.5 for g in v.groups) for v in mesh.data.vertices):
                    continue
                ob = split_part(mesh, group, collection, arm, slot)
                before = ob.data.copy()
                count = triangles(before)
                target = budgets.get(group.name, 64 if count > 100 else 24)
                simplify(ob, target)
                mod = ob.modifiers.new('Rigid part skin', 'ARMATURE')
                mod.object = arm
                error = geometry_error(before, ob.data)
                assert error < 0.004, f'{name}/{group.name}: deviation {error} m'
                parts.append({'object': ob.name, 'bone': group.name, 'slot': slot, 'source_triangles': count,
                              'triangles': triangles(ob.data), 'max_surface_error_mm': round(error * 1000, 3)})
                bpy.data.meshes.remove(before)
        # Keep the original two/three material submissions, rather than a draw for every moving part.
        for slot in sorted({p['slot'] for p in parts}):
            bpy.ops.object.select_all(action='DESELECT')
            objects = [o for o in collection.objects if o.type == 'MESH'
                       and o.data.materials[0]['textureSlot'] == slot]
            for ob in objects:
                ob.select_set(True)
            bpy.context.view_layer.objects.active = objects[0]
            bpy.ops.object.join()
            ob = bpy.context.object
            ob.name = name + '_' + slot
            ob.data.name = ob.name + '_geometry'
            ob['detector_slot'] = slot
            for part in parts:
                if part['slot'] == slot:
                    part['object'] = ob.name
        actions = sorted([a for a in loaded.actions if a.name.startswith('barry_' + name + '_')], key=lambda a: a.name)
        assert len(actions) >= 16, name + ': missing source motions'
        arm.animation_data_create()
        for action in actions:
            action.use_fake_user = True
            track = arm.animation_data.nla_tracks.new()
            track.name = action.name
            strip = track.strips.new(action.name, 0, action)
            strip.action_slot = action.slots[0]
            strip.scale = 1 / action['source_speed']
            track.mute = True
        arm['source_bones'] = json.dumps([b.name for b in src_arm.data.bones])
        assert [b.name for b in arm.data.bones] == [b.name for b in src_arm.data.bones]
        for a, b in zip(arm.data.bones, src_arm.data.bones):
            assert max(abs(v) for row in (a.matrix_local - b.matrix_local) for v in row) < 1e-6
        bpy.ops.object.select_all(action='DESELECT')
        for ob in collection.objects:
            ob.select_set(True)
        bpy.context.view_layer.objects.active = arm
        path = OUT / (name + '.glb')
        bpy.ops.export_scene.gltf(filepath=str(path), use_selection=True, export_format='GLB', export_extras=True,
                                 export_animations=True, export_animation_mode='NLA_TRACKS', export_frame_range=False,
                                 export_force_sampling=True, export_anim_single_armature=False,
                                 export_optimize_animation_size=True, export_def_bones=False,
                                 export_image_format='NONE')
        doc = patch_glb(path)
        joint_names = {doc['nodes'][j]['name'] for s in doc['skins'] for j in s['joints']}
        assert set(b.name for b in arm.data.bones) <= joint_names, name + ': lost joints on export'
        clip_names = {a['name'] for a in doc['animations']}
        assert clip_names == {a.name for a in actions}, (name, clip_names)
        assert all({'JOINTS_0', 'WEIGHTS_0'} <= p['attributes'].keys() for m in doc['meshes'] for p in m['primitives'])
        original = sum(triangles(o.data) for o in meshes)
        reduced = sum(p['triangles'] for p in parts)
        exported = sum(doc['accessors'][p['indices']]['count'] // 3
                       for mesh in doc['meshes'] for p in mesh['primitives'])
        assert 0 < exported <= reduced
        assert reduced < original * 0.35, name + ': insufficient reduction'
        report[name] = {'source_triangles': original, 'triangles': exported, 'blender_triangles': reduced,
                        'bones': [{'name': b.name, 'parent': b.parent.name if b.parent else None,
                                   'head_blender': list(b.head_local)} for b in arm.data.bones],
                        'source_center_blender': list(center), 'parts': parts,
                        'animations': [{'name': a.name, 'source_frames': list(a.frame_range),
                                        'source_fps': a['source_fps'], 'source_speed': a['source_speed']}
                                       for a in actions]}
        sources.hide_viewport = sources.hide_render = True
        arm.animation_data.action = next(a for a in actions if a.name == 'barry_' + name + '_idle')
        arm.animation_data.action_slot = arm.animation_data.action.slots[0]
    (ART / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    for i, name in enumerate(BUDGETS):
        bpy.data.objects[name + '_rig'].location.x += i * 0.16
    scene.frame_set(0)
    bpy.context.view_layer.update()
    depsgraph = bpy.context.evaluated_depsgraph_get()
    points = [o.evaluated_get(depsgraph).matrix_world @ Vector(v)
              for col in loaded.collections for o in bpy.data.collections[col.name.replace('_source', '_lowpoly')].objects
              if o.type == 'MESH' for v in o.evaluated_get(depsgraph).bound_box]
    low = Vector([min(p[i] for p in points) for i in range(3)])
    high = Vector([max(p[i] for p in points) for i in range(3)])
    target = (low + high) / 2
    camera_data = bpy.data.cameras.new('Review camera')
    camera = bpy.data.objects.new('Review camera', camera_data)
    scene.collection.objects.link(camera)
    camera.location = target + Vector((0.12, -0.45, 0.65))
    camera.rotation_euler = (target - camera.location).to_track_quat('-Z', 'Y').to_euler()
    camera_data.type = 'ORTHO'
    camera_data.ortho_scale = max(high - low) * 1.3
    camera_data.clip_start = 0.001
    scene.camera = camera
    for label, offset, power, size in [('Key', (-0.3, -0.3, 0.7), 20, 0.5),
                                       ('Fill', (0.5, 0.1, 0.3), 10, 0.4)]:
        data = bpy.data.lights.new(label, 'AREA')
        data.energy, data.shape, data.size = power, 'DISK', size
        ob = bpy.data.objects.new(label, data)
        scene.collection.objects.link(ob)
        ob.location = target + Vector(offset)
        ob.rotation_euler = (target - ob.location).to_track_quat('-Z', 'Y').to_euler()
    scene.render.resolution_x, scene.render.resolution_y = 1400, 900
    scene.render.resolution_percentage = 100
    for screen in bpy.data.screens:
        for area in screen.areas:
            if area.type == 'VIEW_3D':
                area.spaces.active.shading.type = 'MATERIAL'
                view = area.spaces.active.region_3d
                view.view_location = target
                view.view_rotation = camera.rotation_euler.to_quaternion()
                view.view_distance = camera_data.ortho_scale * 1.3
    for mat in list(bpy.data.materials):
        if mat.users == 0:
            bpy.data.materials.remove(mat)
    for img in list(bpy.data.images):
        if img.users == 0:
            bpy.data.images.remove(img)
        elif img.source == 'FILE' and Path(bpy.path.abspath(img.filepath)).is_file():
            img.pack()
    bpy.context.preferences.filepaths.save_version = 0
    # Blender protects an appended library's path; save beside it and replace only after success.
    pending = ART / 'detectors.pending.blend'
    bpy.ops.wm.save_as_mainfile(filepath=str(pending))
    pending.replace(ART / 'detectors.blend')
    print('LOWPOLY_REPORT', json.dumps(report), flush=True)


if __name__ == '__main__':
    argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
    build(Path(argv[0]) if argv else ART / 'detectors.blend')
