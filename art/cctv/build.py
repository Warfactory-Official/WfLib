"""The wall-mounted CCTV camera, in three parts that move against each other: base (the plate and arm), pan (turns about the
vertical through PAN) and tilt (the housing, turns about the sideways axis through TILT).

    blender -b -P art/cctv/build.py          # model, previews and the three GLBs + atlases in the mod's resources

Blender axes: +Y is the way the lens looks, +Z up, the wall is at y = -0.5. The model's origin is the middle of the block it sits in,
so with the block facing north the lens looks along -Z of the game. Every part is exported with its own pivot at its origin; the game
puts them together (BER/visual: base at the block, pan turned by the pan angle, tilt turned by the tilt angle on top of it).
"""
import json
import math
import os
import struct

import bpy
import mathutils

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "wflib")
PRE = os.path.join(HERE, "previews")
os.makedirs(PRE, exist_ok=True)

PAN = (0.0, -0.25, 0.0)
TILT = (0.0, -0.25, 0.05)
SIZES = {"base": 128, "pan": 64, "tilt": 256, "all": 256}


def mat(name, colour, rough=0.6):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*colour, 1)
    b.inputs["Roughness"].default_value = rough
    return m


def add(part, o, material):
    o.data.materials.append(material)
    o["part"] = part
    return o


def box(part, name, size, loc, material, bevel=0.0, rot=(0, 0, 0)):
    bpy.ops.mesh.primitive_cube_add(size=1, location=loc, rotation=rot)
    o = bpy.context.active_object
    o.name = name
    o.scale = size
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    if bevel:
        mod = o.modifiers.new("bevel", "BEVEL")
        mod.width = bevel
        mod.segments = 3
        mod.limit_method = "ANGLE"
        bpy.ops.object.modifier_apply(modifier="bevel")
    return add(part, o, material)


def cyl(part, name, radius, depth, loc, material, axis="z", verts=20):
    rot = {"z": (0, 0, 0), "y": (math.pi / 2, 0, 0), "x": (0, math.pi / 2, 0)}[axis]
    bpy.ops.mesh.primitive_cylinder_add(vertices=verts, radius=radius, depth=depth, location=loc, rotation=rot)
    o = bpy.context.active_object
    o.name = name
    return add(part, o, material)


def build():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    white = mat("white", (0.84, 0.85, 0.86), 0.45)
    grey = mat("alu", (0.58, 0.60, 0.63), 0.4)
    groove = mat("groove", (0.33, 0.34, 0.37), 0.6)
    black = mat("black", (0.025, 0.025, 0.03), 0.5)
    glass = mat("glass", (0.04, 0.07, 0.13), 0.1)
    chrome = mat("chrome", (0.68, 0.69, 0.71), 0.25)
    led = mat("led", (0.32, 0.07, 0.07), 0.3)

    px, py, pz = TILT
    # --- base: wall plate, four screws, the arm and the pan housing
    box("base", "plate", (0.11, 0.014, 0.17), (0, -0.493, 0.0), white, 0.004)
    for sx in (-1, 1):
        for sz in (-1, 1):
            cyl("base", "screw", 0.0065, 0.004, (sx * 0.036, -0.4835, sz * 0.062), chrome, "y", 12)
            box("base", "slot", (0.009, 0.002, 0.0016), (sx * 0.036, -0.4812, sz * 0.062), black, 0, (0, sx * 0.7, 0))
    box("base", "armroot", (0.064, 0.055, 0.075), (0, -0.4585, -0.005), white, 0.008)
    box("base", "arm", (0.042, 0.2, 0.036), (0, -0.36, -0.024), white, 0.007)
    box("base", "armrib", (0.02, 0.17, 0.012), (0, -0.37, -0.047), white, 0.003)
    cyl("base", "panbase", 0.029, 0.034, (PAN[0], PAN[1], -0.024), white, "z", 28)
    cyl("base", "panring", 0.0315, 0.007, (PAN[0], PAN[1], -0.006), grey, "z", 28)

    # --- pan: the collar on the base and the knuckle the housing tilts on
    cyl("pan", "collar", 0.024, 0.014, (PAN[0], PAN[1], 0.001), white, "z", 28)
    cyl("pan", "neck", 0.017, 0.026, (PAN[0], PAN[1], 0.021), white, "z", 24)
    cyl("pan", "knuckle", 0.017, 0.07, (px, py, pz), white, "x", 24)
    cyl("pan", "knob", 0.011, 0.009, (px + 0.0395, py, pz), grey, "x", 20)
    cyl("pan", "knob", 0.011, 0.009, (px - 0.0395, py, pz), grey, "x", 20)

    # --- tilt: housing, sun shield, front bezel with its lens and infra-red ring, the fins underneath
    cy, cz = py + 0.03, pz + 0.032
    box("tilt", "body", (0.088, 0.31, 0.072), (0, cy, cz), white, 0.013)
    box("tilt", "shield", (0.106, 0.355, 0.013), (0, py + 0.055, pz + 0.077), white, 0.005)
    for sx in (-1, 1):
        box("tilt", "lip", (0.007, 0.33, 0.034), (sx * 0.0495, py + 0.05, pz + 0.062), white, 0.002)
    box("tilt", "fins", (0.078, 0.215, 0.022), (0, py + 0.002, pz - 0.012), grey, 0.004)
    for k in range(5):
        box("tilt", "groove", (0.081, 0.205, 0.0022), (0, py + 0.002, pz - 0.02 + k * 0.0042), groove)
    fy = py + 0.186
    box("tilt", "bezel", (0.08, 0.012, 0.066), (0, fy, cz), white, 0.006)
    box("tilt", "window", (0.062, 0.004, 0.049), (0, fy + 0.007, cz), black, 0.002)
    for k in range(10):
        a = k * math.pi * 2 / 10
        cyl("tilt", "led", 0.0037, 0.003, (math.cos(a) * 0.0235, fy + 0.0095, cz + math.sin(a) * 0.0185), led, "y", 10)
    cyl("tilt", "lens", 0.0165, 0.036, (0, fy + 0.016, cz), black, "y", 24)
    cyl("tilt", "lensring", 0.0185, 0.006, (0, fy + 0.026, cz), groove, "y", 24)
    cyl("tilt", "lensglass", 0.0115, 0.002, (0, fy + 0.0345, cz), glass, "y", 24)
    cyl("tilt", "topscrew", 0.0065, 0.005, (0, py + 0.045, pz + 0.0855), chrome, "z", 12)
    box("tilt", "topslot", (0.009, 0.0016, 0.0016), (0, py + 0.045, pz + 0.0885), black, 0, (0, 0, 0.5))
    box("tilt", "rearcap", (0.078, 0.012, 0.064), (0, py - 0.128, cz), grey, 0.005)
    cyl("tilt", "gland", 0.0085, 0.022, (0, py - 0.146, cz), black, "y", 14)


def preview(name, azimuth, elevation, target=(0, -0.18, 0.04), distance=0.9, pan=0.0, tilt=0.0, parts=None):
    """A 640 px render of the assembled camera, optionally panned and tilted (degrees)."""
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    scene.view_settings.view_transform = "Standard"
    scene.cycles.samples = 40
    scene.cycles.device = "CPU"
    scene.render.resolution_x = scene.render.resolution_y = 640
    scene.render.filepath = os.path.join(PRE, name + ".png")
    scene.world = bpy.data.worlds.new("w")
    scene.world.use_nodes = True
    scene.world.node_tree.nodes["Background"].inputs[0].default_value = (0.5, 0.52, 0.56, 1)
    az, el = math.radians(azimuth), math.radians(elevation)
    cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
    scene.collection.objects.link(cam)
    cam.location = (target[0] + distance * math.cos(el) * math.sin(az), target[1] + distance * math.cos(el) * math.cos(az),
                    target[2] + distance * math.sin(el))
    d = mathutils.Vector(cam.location) - mathutils.Vector(target)
    cam.rotation_euler = (-d).to_track_quat("-Z", "Y").to_euler()
    scene.camera = cam
    for loc, energy in (((0.9, 0.9, 0.9), 14), ((-0.8, 0.4, 0.7), 6)):
        light = bpy.data.objects.new("l", bpy.data.lights.new("l", "AREA"))
        light.data.energy = energy
        light.data.size = 0.8
        light.location = loc
        scene.collection.objects.link(light)
        light.rotation_euler = (-mathutils.Vector(loc)).to_track_quat("-Z", "Y").to_euler()
    # a wall behind the camera, so the mount reads as mounted
    bpy.ops.mesh.primitive_plane_add(size=2.0, location=(0, -0.5005, 0), rotation=(math.pi / 2, 0, 0))
    wall = bpy.context.active_object
    wall.data.materials.append(mat("wall", (0.3, 0.3, 0.32), 0.9))
    moved = []
    for o in bpy.data.objects:
        if o.get("part") == "pan" or o.get("part") == "tilt":
            moved.append(o)
    rot_pan = mathutils.Matrix.Translation(PAN) @ mathutils.Matrix.Rotation(math.radians(pan), 4, "Z") @ mathutils.Matrix.Translation([-c for c in PAN])
    rot_tilt = mathutils.Matrix.Translation(TILT) @ mathutils.Matrix.Rotation(math.radians(-tilt), 4, "X") @ mathutils.Matrix.Translation([-c for c in TILT])
    saved = {o.name: o.matrix_world.copy() for o in moved}
    for o in moved:
        o.matrix_world = rot_pan @ (rot_tilt @ o.matrix_world if o["part"] == "tilt" else o.matrix_world)
    bpy.ops.render.render(write_still=True)
    for o in moved:
        o.matrix_world = saved[o.name]
    bpy.data.objects.remove(wall)


def patch_glb(path, resloc, uri):
    b = open(path, "rb").read()
    jl = struct.unpack_from("<I", b, 12)[0]
    js = json.loads(b[20:20 + jl])
    rest = b[20 + jl:]
    for im in js.get("images", []):
        im.pop("bufferView", None)
        im["uri"] = uri
        im["mimeType"] = "image/png"
        im["extras"] = {"resourceLocation": resloc}
    js["samplers"] = [{"magFilter": 9728, "minFilter": 9728, "wrapS": 33071, "wrapT": 33071}]
    for t in js.get("textures", []):
        t["sampler"] = 0
    for m in js.get("materials", []):
        pbr = m.setdefault("pbrMetallicRoughness", {})
        pbr["metallicFactor"] = 0.0
        pbr["roughnessFactor"] = 0.85
    raw = json.dumps(js, separators=(",", ":")).encode()
    raw += b" " * ((4 - len(raw) % 4) % 4)
    open(path, "wb").write(b"glTF" + struct.pack("<II", 2, 12 + 8 + len(raw) + len(rest)) + struct.pack("<I", len(raw)) + b"JSON" + raw + rest)


def export_part(part, pivot):
    """Bakes the part's materials into its own atlas and writes its GLB, with the pivot moved to the origin."""
    scene = bpy.context.scene
    deps = bpy.context.evaluated_depsgraph_get()
    made = []
    for o in [o for o in bpy.data.objects if o.type == "MESH" and (part == "all" or o.get("part") == part)]:
        me = bpy.data.meshes.new_from_object(o.evaluated_get(deps))
        me.transform(o.matrix_world)
        n = bpy.data.objects.new(o.name + "_b", me)
        scene.collection.objects.link(n)
        made.append(n)
    bpy.ops.object.select_all(action="DESELECT")
    for o in made:
        o.select_set(True)
    bpy.context.view_layer.objects.active = made[0]
    bpy.ops.object.join()
    obj = bpy.context.active_object
    obj.name = "cctv_" + part
    me = obj.data
    if part == "all":                                  # the item: the assembled camera, centred
        lo = mathutils.Vector((min(v.co[i] for v in me.vertices) for i in range(3)))
        hi = mathutils.Vector((max(v.co[i] for v in me.vertices) for i in range(3)))
        pivot = tuple((lo + hi) / 2)
    me.transform(mathutils.Matrix.Translation([-c for c in pivot]))
    me.uv_layers.new(name="UVMap")
    me.uv_layers.active = me.uv_layers["UVMap"]
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.uv.smart_project(angle_limit=math.radians(66), island_margin=0.01)
    bpy.ops.object.mode_set(mode="OBJECT")
    size = SIZES[part]
    image = bpy.data.images.new("atlas_" + part, size, size, alpha=False)
    image.generated_color = (0.02, 0.02, 0.02, 1)
    for m in me.materials:
        node = m.node_tree.nodes.new("ShaderNodeTexImage")
        node.image = image
        m.node_tree.nodes.active = node
        node.select = True
    scene.render.engine = "CYCLES"
    scene.cycles.device = "CPU"
    scene.cycles.samples = 8
    bpy.ops.object.bake(type="DIFFUSE", pass_filter={"COLOR"}, margin=4, use_clear=True)
    atlas_dir = os.path.join(RES, "textures", "block")
    os.makedirs(atlas_dir, exist_ok=True)
    name = "security_camera" if part == "all" else "security_camera_" + part
    atlas = os.path.abspath(os.path.join(atlas_dir, name + ".png"))
    image.filepath_raw = atlas
    image.file_format = "PNG"
    image.save()
    me.materials.clear()
    final = bpy.data.materials.new("atlas_" + part)
    final.use_nodes = True
    tex = final.node_tree.nodes.new("ShaderNodeTexImage")
    tex.image = bpy.data.images.load(atlas)
    tex.interpolation = "Closest"
    final.node_tree.links.new(tex.outputs["Color"], final.node_tree.nodes["Principled BSDF"].inputs["Base Color"])
    me.materials.append(final)
    glb = os.path.abspath(os.path.join(RES, "models", "block", name + ".glb"))
    os.makedirs(os.path.dirname(glb), exist_ok=True)
    bpy.ops.object.select_all(action="DESELECT")
    obj.select_set(True)
    bpy.ops.export_scene.gltf(filepath=glb, export_format="GLB", use_selection=True, export_yup=True, export_apply=False,
                              export_image_format="AUTO", export_extras=False, export_texcoords=True, export_normals=True)
    patch_glb(glb, "wflib:textures/block/%s.png" % name, "../../textures/block/%s.png" % name)
    bpy.data.objects.remove(obj, do_unlink=True)
    print("EXPORTED", name)


if __name__ == "__main__":
    build()
    preview("front", 30, 18)
    preview("side", 100, 12)
    preview("panned_tilted", 30, 20, pan=50, tilt=-30)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.join(HERE, "cctv.blend"))
    for part, pivot in (("base", (0, 0, 0)), ("pan", PAN), ("tilt", TILT), ("all", None)):
        export_part(part, pivot)
