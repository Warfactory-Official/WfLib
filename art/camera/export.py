"""Headless export of camera.blend to the item GLB:  blender -b art/camera/camera.blend --python art/camera/export.py

Joins the model's parts into one mesh per material (so the GLB has a node per material, which is how GemRender
textures a model by slot name) and exports only those. Joining is done on mesh data, not with operators.
"""
import bpy, bmesh, os, sys

HERE = os.path.dirname(os.path.abspath(bpy.data.filepath)) if bpy.data.filepath else os.getcwd()
OUT = os.environ.get("CAMERA_GLB", os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "wflib",
                                                 "models", "item", "camera.glb"))

sc = bpy.data.scenes["WFCamera"]
for other in [s for s in bpy.data.scenes if s is not sc]:
    bpy.data.scenes.remove(other)           # the startup scene (and its default cube) must not leak into the export
bpy.context.window.scene = sc if bpy.context.window else None
vl = sc.view_layers[0]
dg = bpy.context.evaluated_depsgraph_get()

parts = {}
for o in sc.objects:
    if o.type == 'MESH' and o.data.materials:
        parts.setdefault(o.data.materials[0].name, []).append(o)

col = bpy.data.collections.new("export")
sc.collection.children.link(col)
if "camera" in bpy.data.objects:
    bpy.data.objects["camera"].name = "camera_src"
root = bpy.data.objects.new("camera", None)
col.objects.link(root)
made = []
for name, group in sorted(parts.items()):
    bm = bmesh.new()
    for o in group:
        me = bpy.data.meshes.new_from_object(o.evaluated_get(dg))
        me.transform(o.matrix_world)
        bm.from_mesh(me)
        bpy.data.meshes.remove(me)
    mesh = bpy.data.meshes.new(name)
    bm.to_mesh(mesh); bm.free()
    mesh.materials.append(bpy.data.materials[name])
    j = bpy.data.objects.new(name, mesh)
    j.parent = root
    col.objects.link(j)
    made.append(j)

# GemRender bakes an untextured material's baseColorFactor as the displayed colour without a linear->sRGB step, so the
# linear values modelled in Blender are written out sRGB-encoded (otherwise near-black plastic renders pure black).
def srgb(c):
    return 1.055 * c ** (1 / 2.4) - 0.055 if c > 0.0031308 else 12.92 * c
if os.environ.get("CAMERA_SRGB", "1") == "1":
    for j in made:
        m = j.data.materials[0].copy()
        b = m.node_tree.nodes["Principled BSDF"]
        r, g, bl, a = b.inputs["Base Color"].default_value
        b.inputs["Base Color"].default_value = (srgb(r), srgb(g), srgb(bl), a)
        m.name = j.name
        j.data.materials[0] = m

for o in vl.objects:
    o.select_set(False)
root.select_set(True)
for j in made:
    j.select_set(True)
vl.objects.active = root

bpy.ops.export_scene.gltf(filepath=OUT, export_format='GLB', use_selection=True, export_yup=True, export_apply=False,
                          export_materials='EXPORT', export_image_format='NONE', export_cameras=False,
                          export_lights=False, export_animations=False, export_extras=False, export_texcoords=False,
                          export_normals=True)
print("EXPORTED", os.path.abspath(OUT), "materials", len(made), "polygons", sum(len(j.data.polygons) for j in made))
