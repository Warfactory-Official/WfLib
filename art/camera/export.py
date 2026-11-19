"""Export the textured camera to a GLB:  runs inside make.py (or on a saved camera.blend).

One material ('camera', the atlas), nodes: body, shutter, lens_1..3 (the telescoping barrel stages), under 'camera'.
The atlas is referenced as an external PNG: image uri + extras.resourceLocation, nearest sampler, as MineAssault packs
and GemRender expect.  Environment: CAMERA_GLB (output path), CAMERA_RESLOC (resource location of the PNG),
CAMERA_URI (uri written into the GLB).
"""
import bpy, bmesh, os, json, struct

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.environ.get("CAMERA_GLB", os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "wflib",
                                                 "models", "item", "camera.glb"))
RESLOC = os.environ.get("CAMERA_RESLOC", "wflib:textures/item/camera.png")
URI = os.environ.get("CAMERA_URI", "../../textures/item/camera.png")

sc = bpy.data.scenes["WFCamera"]
for other in [s for s in bpy.data.scenes if s is not sc]:
    bpy.data.scenes.remove(other)
vl = sc.view_layers[0]
dg = bpy.context.evaluated_depsgraph_get()

NODES = {
    "shutter": ("shutter", "shutter_cap"),
    "lens_1": ("lens_ring1", "lens_groove0"),
    "lens_2": ("lens_ring2", "lens_groove1"),
    "lens_3": ("lens_ring3", "lens_groove2", "lens_bezel", "lens_bezel_lip", "lens_window_rim", "lens_window",
               "lens_inner", "lens_mid", "lens_core"),
}


def node_of(name):
    for node, members in NODES.items():
        if name in members:
            return node
    return "body"


parts = {}
for o in sc.objects:
    if o.type == 'MESH':
        parts.setdefault(node_of(o.name), []).append(o)

if "camera" in bpy.data.objects:
    bpy.data.objects["camera"].name = "camera_src"
col = bpy.data.collections.new("export")
sc.collection.children.link(col)
root = bpy.data.objects.new("camera", None)
col.objects.link(root)
made = []
for name, group in parts.items():
    bm = bmesh.new()
    for o in group:
        me = bpy.data.meshes.new_from_object(o.evaluated_get(dg))
        me.transform(o.matrix_world)
        bm.from_mesh(me)
        bpy.data.meshes.remove(me)
    mesh = bpy.data.meshes.new(name)
    bm.to_mesh(mesh); bm.free()
    mesh.materials.append(bpy.data.materials["camera"])
    j = bpy.data.objects.new(name, mesh)
    j.parent = root
    col.objects.link(j)
    made.append(j)
for o in vl.objects:
    o.select_set(False)
root.select_set(True)
for j in made:
    j.select_set(True)
vl.objects.active = root

os.makedirs(os.path.dirname(os.path.abspath(OUT)), exist_ok=True)
bpy.ops.export_scene.gltf(filepath=OUT, export_format='GLB', use_selection=True, export_yup=True, export_apply=False,
                          export_materials='EXPORT', export_image_format='AUTO', export_cameras=False,
                          export_lights=False, export_animations=False, export_extras=False, export_texcoords=True,
                          export_normals=True)

# ---- image: embedded -> external uri + resourceLocation, nearest sampler
b = open(OUT, 'rb').read()
jl = struct.unpack_from('<I', b, 12)[0]
js = json.loads(b[20:20 + jl])
bin_off = 20 + jl
rest = b[bin_off:]                    # BIN chunk header + data (kept whole; the image bytes stay as an unused view)
for im in js.get("images", []):
    im.pop("bufferView", None)
    im["uri"] = URI
    im["mimeType"] = "image/png"
    im["extras"] = {"resourceLocation": RESLOC}
js["samplers"] = [{"magFilter": 9728, "minFilter": 9728, "wrapS": 33071, "wrapT": 33071}]
for t in js.get("textures", []):
    t["sampler"] = 0
for m in js.get("materials", []):
    pbr = m.setdefault("pbrMetallicRoughness", {})
    pbr["metallicFactor"] = 0.0
    pbr["roughnessFactor"] = 1.0
raw = json.dumps(js, separators=(",", ":")).encode()
raw += b" " * ((4 - len(raw) % 4) % 4)
out = b"glTF" + struct.pack('<II', 2, 12 + 8 + len(raw) + len(rest)) + struct.pack('<I', len(raw)) + b"JSON" + raw + rest
open(OUT, 'wb').write(out)
print("EXPORTED", os.path.abspath(OUT), "nodes", sorted(parts), "polygons", sum(len(j.data.polygons) for j in made))
