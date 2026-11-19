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
    "screen": ("screen",),
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


def live_screen_uv(mesh):
    """Back face (+Y) fills UV 0..1 (u = -x so it reads from behind). v runs top-down: the live feed is a render target
    (bottom-up rows), the still fallback PNG is saved flipped to match. Every other face collapses to 0."""
    import bmesh as _bm
    b = _bm.new(); b.from_mesh(mesh)
    uv = b.loops.layers.uv.get("UVMap") or b.loops.layers.uv.new("UVMap")
    pts = [v.co.copy() for v in b.verts]            # already in world space
    minu, maxu = min(-p.x for p in pts), max(-p.x for p in pts)
    minv, maxv = min(p.z for p in pts), max(p.z for p in pts)
    for f in b.faces:
        n = f.normal
        for l in f.loops:
            p = l.vert.co
            l[uv].uv = (((-p.x - minu) / (maxu - minu), (maxv - p.z) / (maxv - minv)) if n.y > 0.9 else (0.0, 0.0))
    b.to_mesh(mesh); b.free()


parts = {}
for o in sc.objects:
    if o.type == 'MESH':
        parts.setdefault(node_of(o.name), []).append(o)

for group in parts.values():          # free the names the joined nodes will take
    for o in group:
        o.name = o.name + "_src"
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
        if o.name == "screen_src":
            live_screen_uv(me)
        bm.from_mesh(me)
        bpy.data.meshes.remove(me)
    mesh = bpy.data.meshes.new(name)
    bm.to_mesh(mesh); bm.free()
    if name == "screen":
        sm = bpy.data.materials.get("screen") or bpy.data.materials.new("screen")
        sm.use_nodes = True
        sm.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (0.03, 0.05, 0.09, 1.0)
        mesh.materials.append(sm)
    else:
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
# MineAssault semantic rig: the model root and the two hand frames (+Y = forearm -> fist, pointing along the lens)
if os.environ.get("CAMERA_RIG", "1") == "1":
    q = [0.70710678, 0.0, 0.0, 0.70710678]          # local +Y -> +Z (the lens direction)
    names = [n.get("name") for n in js["nodes"]]
    root = js["nodes"][names.index("camera")]
    root["extras"] = {"mineassaultRole": "root"}
    for role, pos in (("grip_right", [-0.0720, -0.0320, 0.0100]), ("grip_left", [0.0650, -0.0400, -0.0020])):
        js["nodes"].append({"name": role, "translation": pos, "rotation": q, "extras": {"mineassaultRole": role}})
        root.setdefault("children", []).append(len(js["nodes"]) - 1)
js["samplers"] = [{"magFilter": 9728, "minFilter": 9728, "wrapS": 33071, "wrapT": 33071}]
for t in js.get("textures", []):
    t["sampler"] = 0
for m in js.get("materials", []):
    if m.get("name") == "screen":
        m["extras"] = {"textureSlot": "screen"}      # bound per draw: the live viewfinder feed
    pbr = m.setdefault("pbrMetallicRoughness", {})
    pbr["metallicFactor"] = 0.0
    pbr["roughnessFactor"] = 1.0
raw = json.dumps(js, separators=(",", ":")).encode()
raw += b" " * ((4 - len(raw) % 4) % 4)
out = b"glTF" + struct.pack('<II', 2, 12 + 8 + len(raw) + len(rest)) + struct.pack('<I', len(raw)) + b"JSON" + raw + rest
open(OUT, 'wb').write(out)
print("EXPORTED", os.path.abspath(OUT), "nodes", sorted(parts), "polygons", sum(len(j.data.polygons) for j in made))
