"""Builds the PMCLib prop models in Blender, previews them and exports GLB.

    blender -b -P art/build_models.py            # all models
    blender -b -P art/build_models.py -- usb     # one

Modelled from reference photos, with our own generic labels (no manufacturer marks). Units are metres at real size; the
GLB is what a model pack scales down to item size.
"""
import math
import os
import sys

import bpy

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(HERE, "textures")
OUT = os.path.join(HERE, "models")
PRE = os.path.join(HERE, "previews")
os.makedirs(OUT, exist_ok=True)
os.makedirs(PRE, exist_ok=True)


def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)


def mat(name, colour, metallic=0.0, rough=0.5, image=None, alpha_image=False, emission=None):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    n = m.node_tree.nodes
    b = n["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*colour, 1)
    b.inputs["Metallic"].default_value = metallic
    b.inputs["Roughness"].default_value = rough
    if image:
        t = n.new("ShaderNodeTexImage")
        t.image = bpy.data.images.load(os.path.join(TEX, image))
        m.node_tree.links.new(t.outputs["Color"], b.inputs["Base Color"])
        if alpha_image:
            m.node_tree.links.new(t.outputs["Alpha"], b.inputs["Alpha"])
            m.blend_method = "BLEND"
    return m


def box(name, size, loc, material, bevel=0.0, rot=(0, 0, 0), parent=None):
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
    o.data.materials.append(material)
    if parent:
        o.parent = parent
    return o


def cyl(name, radius, depth, loc, material, rot=(0, 0, 0), verts=24, parent=None):
    bpy.ops.mesh.primitive_cylinder_add(radius=radius, depth=depth, location=loc, rotation=rot, vertices=verts)
    o = bpy.context.active_object
    o.name = name
    o.data.materials.append(material)
    if parent:
        o.parent = parent
    return o


def plane_label(name, size, loc, material, rot=(0, 0, 0), parent=None):
    bpy.ops.mesh.primitive_plane_add(size=1, location=loc, rotation=rot)
    o = bpy.context.active_object
    o.name = name
    o.scale = (size[0], size[1], 1)
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    o.data.materials.append(material)
    if parent:
        o.parent = parent
    return o


def lift_to_floor(dz):
    """Everything up by dz so the lowest point rests on z = 0 (the floor)."""
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.transform.translate(value=(0, 0, dz))
    bpy.ops.object.select_all(action="DESELECT")


def preview(name, distance, target=(0, 0, 0), azimuth=35, elevation=28):
    """Renders a 768 px preview with a key light, a rim light and a plain floor."""
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    scene.view_settings.view_transform = "Standard"
    scene.view_settings.look = "None"
    scene.cycles.samples = 48
    scene.cycles.device = "CPU"
    scene.render.resolution_x = scene.render.resolution_y = 768
    scene.render.filepath = os.path.join(PRE, name + ".png")
    scene.world = bpy.data.worlds.new("w")
    scene.world.use_nodes = True
    scene.world.node_tree.nodes["Background"].inputs[0].default_value = (0.16, 0.17, 0.2, 1)
    az, el = math.radians(azimuth), math.radians(elevation)
    cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
    scene.collection.objects.link(cam)
    cam.location = (target[0] + distance * math.cos(el) * math.sin(az), target[1] - distance * math.cos(el) * math.cos(az),
                    target[2] + distance * math.sin(el))
    d = (cam.location[0] - target[0], cam.location[1] - target[1], cam.location[2] - target[2])
    import mathutils
    cam.rotation_euler = mathutils.Vector((-d[0], -d[1], -d[2])).to_track_quat("-Z", "Y").to_euler()
    scene.camera = cam
    for loc, energy, size in (((distance, -distance, distance * 1.2), 55 * distance * distance, distance * 0.8), ((-distance, distance * 0.6, distance * 0.8), 22 * distance * distance, distance)):
        light = bpy.data.objects.new("l", bpy.data.lights.new("l", "AREA"))
        light.data.energy = energy
        light.data.size = size
        light.location = loc
        scene.collection.objects.link(light)
        d = mathutils.Vector((-loc[0], -loc[1], -loc[2] + target[2]))
        light.rotation_euler = d.to_track_quat("-Z", "Y").to_euler()
    bpy.ops.mesh.primitive_plane_add(size=distance * 6, location=(target[0], target[1], -0.0005))
    floor = bpy.context.active_object
    floor.name = "floor"
    floor.data.materials.append(mat("floor", (0.06, 0.06, 0.07), 0.0, 0.85))
    bpy.ops.render.render(write_still=True)
    bpy.data.objects.remove(floor)
    # the same view on a transparent background, small: the item icon
    os.makedirs(os.path.join(HERE, "icons"), exist_ok=True)
    scene.render.film_transparent = True
    scene.render.resolution_x = scene.render.resolution_y = 256
    scene.cycles.samples = 32
    scene.render.filepath = os.path.join(HERE, "icons", name + ".png")
    bpy.ops.render.render(write_still=True)


RES = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "wflib")


def export(name, item=None, size=512):
    """Saves the .blend, then bakes every material into one atlas and writes a GemRender-ready GLB (one node, one material, an
    external nearest-filtered PNG) for {@code item} into the mod's resources, the way WFLib's camera is made."""
    for o in [o for o in bpy.data.objects if o.type in ("CAMERA", "LIGHT")]:
        bpy.data.objects.remove(o)
    bpy.ops.wm.save_as_mainfile(filepath=os.path.join(OUT, name + ".blend"))
    if item is None:
        return
    import bmesh
    import mathutils
    scene = bpy.context.scene
    deps = bpy.context.evaluated_depsgraph_get()
    sources = [o for o in bpy.data.objects if o.type == "MESH"]
    made = []
    for o in sources:
        me = bpy.data.meshes.new_from_object(o.evaluated_get(deps))
        me.transform(o.matrix_world)
        n = bpy.data.objects.new(o.name + "_b", me)
        scene.collection.objects.link(n)
        made.append(n)
    for o in sources:
        bpy.data.objects.remove(o, do_unlink=True)
    bpy.ops.object.select_all(action="DESELECT")
    for o in made:
        o.select_set(True)
    bpy.context.view_layer.objects.active = made[0]
    bpy.ops.object.join()
    obj = bpy.context.active_object
    obj.name = item
    me = obj.data
    if "UVMap" not in me.uv_layers:
        me.uv_layers.new(name="UVMap")
    bake_uv = me.uv_layers.new(name="bake")
    me.uv_layers.active = bake_uv
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.uv.smart_project(angle_limit=math.radians(66), island_margin=0.004)
    bpy.ops.object.mode_set(mode="OBJECT")
    me.uv_layers["UVMap"].active_render = True       # the labels sample the original unwrap; the bake writes the new one
    me.uv_layers["bake"].active_render = False
    image = bpy.data.images.new("atlas", size, size, alpha=False)
    image.generated_color = (0.02, 0.02, 0.02, 1)
    for m in me.materials:
        if not m or not m.use_nodes:
            continue
        b = m.node_tree.nodes.get("Principled BSDF")
        if b:
            b.inputs["Metallic"].default_value = 0.0                  # a metal bakes black as a diffuse
        node = m.node_tree.nodes.new("ShaderNodeTexImage")
        node.image = image
        m.node_tree.nodes.active = node
        node.select = True
    scene.render.engine = "CYCLES"
    scene.cycles.device = "CPU"
    scene.cycles.samples = 8
    bpy.ops.object.bake(type="DIFFUSE", pass_filter={"COLOR"}, margin=6, use_clear=True, uv_layer="bake")
    atlas_dir = os.path.join(RES, "textures", "item")
    os.makedirs(atlas_dir, exist_ok=True)
    atlas_path = os.path.abspath(os.path.join(atlas_dir, item + "_atlas.png"))
    image.filepath_raw = atlas_path
    image.file_format = "PNG"
    image.save()
    # the export mesh: the bake unwrap only, one material on the atlas, centred on its bounds
    me.uv_layers.remove(me.uv_layers["UVMap"])
    me.materials.clear()
    final = bpy.data.materials.new("atlas")
    final.use_nodes = True
    tex = final.node_tree.nodes.new("ShaderNodeTexImage")
    tex.image = bpy.data.images.load(atlas_path)
    tex.interpolation = "Closest"
    final.node_tree.links.new(tex.outputs["Color"], final.node_tree.nodes["Principled BSDF"].inputs["Base Color"])
    me.materials.append(final)
    lo = mathutils.Vector((min(v.co[i] for v in me.vertices) for i in range(3)))
    hi = mathutils.Vector((max(v.co[i] for v in me.vertices) for i in range(3)))
    centre = (lo + hi) / 2
    me.transform(mathutils.Matrix.Translation(-centre))
    dims = hi - lo
    glb = os.path.abspath(os.path.join(RES, "models", "item", item + ".glb"))
    os.makedirs(os.path.dirname(glb), exist_ok=True)
    bpy.ops.object.select_all(action="DESELECT")
    obj.select_set(True)
    bpy.ops.export_scene.gltf(filepath=glb, export_format="GLB", use_selection=True, export_yup=True, export_apply=False,
                              export_image_format="AUTO", export_extras=False, export_texcoords=True, export_normals=True)
    patch_glb(glb, "wflib:textures/item/%s_atlas.png" % item, "../../textures/item/%s_atlas.png" % item)
    print("EXPORTED %s dims(x,y,z m, blender axes) %.4f %.4f %.4f" % (item, dims.x, dims.y, dims.z))


def patch_glb(path, resloc, uri):
    """The embedded image becomes an external one with a resource location, nearest sampling, matte: what GemRender reads."""
    import json
    import struct
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


# ---------------------------------------------------------------------------------------------------------------- usb

def usb():
    """A slide-out USB-A stick: black body with a red slider on a rail, chrome plug and cap collar."""
    reset()
    black = mat("body", (0.012, 0.012, 0.014), 0.1, 0.35)
    gloss = mat("gloss", (0.004, 0.004, 0.005), 0.3, 0.12)
    red = mat("slider", (0.62, 0.02, 0.05), 0.0, 0.25)
    chrome = mat("plug", (0.72, 0.74, 0.78), 1.0, 0.22)
    inner = mat("contacts", (0.7, 0.12, 0.05), 0.0, 0.4)
    print_m = mat("print", (1, 1, 1), 0, 0.4, image="usb_print_opaque.png")
    L, W, T = 0.060, 0.021, 0.0105
    box("body", (W, L, T), (0, 0, 0), black, bevel=0.0035)
    box("face", (W * 0.82, L * 0.96, 0.0006), (0, -0.0006, T / 2 + 0.0002), gloss, bevel=0.0002)       # glossy inlay
    box("rail", (W * 0.34, L * 0.52, 0.0008), (0, -0.002, T / 2 + 0.0006), gloss, bevel=0.0002)
    box("slider", (W * 0.36, 0.0125, 0.0038), (0, 0.0035, T / 2 + 0.0018), red, bevel=0.0012)
    for k in range(3):                                                         # grip ribs on the slider
        box("rib%d" % k, (W * 0.3, 0.0005, 0.0006), (0, 0.0005 + k * 0.0035, T / 2 + 0.0039), red)
    plane_label("print", (0.020, 0.005), (0, 0.0225, T / 2 + 0.00075), print_m)
    box("collar", (W * 0.92, 0.0055, T * 0.8), (0, L / 2 + 0.0015, 0), gloss, bevel=0.0012)
    box("plug", (0.0120, 0.0140, 0.0045), (0, L / 2 + 0.0095, 0), chrome, bevel=0.0003)
    box("tongue", (0.0105, 0.0125, 0.0014), (0, L / 2 + 0.0104, 0), inner)
    for sx in (-0.0036, 0.0036):
        box("hole", (0.0026, 0.0030, 0.0048), (sx, L / 2 + 0.0145, 0), inner)
    box("loop", (0.0030, 0.0016, 0.0040), (0, -L / 2 - 0.0006, 0), black, bevel=0.0006)
    lift_to_floor(T / 2)
    preview("usb", 0.16, (0, 0.004, 0.004), azimuth=-30, elevation=32)
    export("usb_drive", "data_drive")


# ---------------------------------------------------------------------------------------------------------- hard drive

def hdd():
    """A 3.5-inch NAS drive: cast aluminium frame, top cover with label, screws, SATA connector at the back."""
    reset()
    alu = mat("alu", (0.62, 0.63, 0.65), 1.0, 0.32)
    dark = mat("dark", (0.025, 0.025, 0.03), 0.2, 0.5)
    pcb = mat("pcb", (0.02, 0.1, 0.05), 0.1, 0.5)
    gold = mat("gold", (0.8, 0.6, 0.15), 1.0, 0.3)
    screw = mat("screw", (0.5, 0.5, 0.54), 1.0, 0.4)
    label = mat("label", (1, 1, 1), 0.0, 0.55, image="drive_label.png")
    L, W, T = 0.1470, 0.1016, 0.0261
    box("base", (W, L, T * 0.55), (0, 0, -T * 0.22), alu, bevel=0.0015)
    box("cover", (W * 0.985, L * 0.985, T * 0.5), (0, 0, T * 0.22), alu, bevel=0.0012)
    box("rim", (W, L, 0.0018), (0, 0, 0.0), dark, bevel=0.0004)                            # the seam gasket
    # moulded recesses in the cover: motor ring, actuator slot
    cyl("motor", 0.0185, 0.0007, (0.0, -0.002, T * 0.47), alu, verts=48)
    box("slot", (0.0125, 0.052, 0.0007), (0.0305, 0.0345, T * 0.47), dark, bevel=0.0035)
    plane_label("label", (0.0615, 0.0935), (-0.0, -0.0175, T * 0.475 + 0.0003), label)
    for sx, sy in ((-0.0455, 0.0615), (0.0455, 0.0615), (-0.0455, -0.0615), (0.0455, -0.0615), (0.0, 0.0705)):
        cyl("screw", 0.0028, 0.0012, (sx, sy, T * 0.47 + 0.0003), screw, verts=6)
    for sx in (-0.0475, 0.0475):                                                          # mounting holes in the frame edge
        for sy in (-0.0395, 0.0145, 0.0625):
            cyl("mount", 0.0018, 0.003, (sx * 1.0, sy, -T * 0.32), dark, rot=(0, math.radians(90), 0), verts=10)
    box("board", (W * 0.9, 0.0575, 0.0016), (0, -L * 0.285, -T * 0.51), pcb)             # the circuit board under the frame
    box("conn", (0.0415, 0.0145, 0.0072), (0.0, -L / 2 + 0.0035, -T * 0.34), dark, bevel=0.0004)
    for k in range(7):
        box("pin%d" % k, (0.0034, 0.0016, 0.0010), (-0.0102 + k * 0.0034, -L / 2 + 0.0031, -T * 0.34 + 0.0042), gold)
    for k in range(15):
        box("pwr%d" % k, (0.0016, 0.0016, 0.0010), (0.0020 + k * 0.0015, -L / 2 + 0.0031, -T * 0.34 + 0.0042), gold)
    lift_to_floor(T * 0.56)
    preview("hdd", 0.42, (0, 0, 0.02), azimuth=30, elevation=38)
    export("hard_drive", "intel_drive")


# --------------------------------------------------------------------------------------------------------------- laptop

def laptop():
    """A black business laptop seen from behind, lid open about 25 degrees: lid with a ring mark, hinges, rear ports and vent."""
    reset()
    shell = mat("shell", (0.035, 0.036, 0.04), 0.05, 0.62)
    lid = mat("lid", (0.03, 0.031, 0.034), 0.08, 0.7)
    dark = mat("dark", (0.006, 0.006, 0.007), 0.0, 0.6)
    steel = mat("steel", (0.55, 0.56, 0.6), 1.0, 0.35)
    hinge = mat("hinge", (0.1, 0.1, 0.11), 0.6, 0.35)
    mark = mat("mark", (1, 1, 1), 0.0, 0.3, image="laptop_mark_opaque.png")
    port = mat("port", (0.2, 0.2, 0.22), 0.0, 0.5)
    W, D, T = 0.335, 0.232, 0.0205
    box("base", (W, D, T), (0, 0, T / 2), shell, bevel=0.0035)
    box("foot-l", (0.04, 0.012, 0.004), (-W * 0.38, -D * 0.4, -0.001), dark, bevel=0.001)
    box("foot-r", (0.04, 0.012, 0.004), (W * 0.38, -D * 0.4, -0.001), dark, bevel=0.001)
    # rear edge: ethernet, vent, sim door, hdmi, usb, power jack
    rear = D / 2 + 0.0002
    box("vent-bay", (0.115, 0.002, 0.011), (-0.045, rear, 0.0102), dark, bevel=0.0004)
    for k in range(17):
        box("slat%d" % k, (0.0032, 0.0016, 0.0094), (-0.095 + k * 0.0068, rear + 0.0004, 0.0102), shell)
    box("ethernet", (0.0165, 0.0025, 0.0125), (-0.1465, rear, 0.0102), dark, bevel=0.0006)
    box("sim", (0.017, 0.0016, 0.0066), (0.0245, rear + 0.0003, 0.0096), port, bevel=0.0006)
    box("hdmi", (0.0145, 0.0025, 0.0058), (0.0515, rear + 0.0003, 0.0102), dark, bevel=0.0007)
    box("usb", (0.0145, 0.0025, 0.0062), (0.0775, rear + 0.0003, 0.0102), dark, bevel=0.0004)
    box("usb-tongue", (0.0105, 0.0022, 0.0018), (0.0775, rear + 0.0004, 0.0102), steel)
    cyl("power", 0.0042, 0.0028, (0.1385, rear + 0.0004, 0.0102), dark, rot=(math.radians(90), 0, 0), verts=24)
    cyl("power-pin", 0.0011, 0.003, (0.1385, rear + 0.0006, 0.0102), steel, rot=(math.radians(90), 0, 0), verts=10)
    # hinges: two barrels and the lid root block, pivot along the rear edge at the top of the base
    pivot = (0.0, D / 2 - 0.004, T)
    for sx in (-W * 0.38, W * 0.38):
        box("hinge-base", (0.046, 0.016, 0.0145), (sx, D / 2 - 0.002, T * 0.82), hinge, bevel=0.0012)
        cyl("barrel", 0.0075, 0.046, (sx, D / 2 - 0.0035, T + 0.0045), hinge, rot=(0, math.radians(90), 0), verts=24)
    # the lid: a slab hinged on the pivot, opened 25 degrees, carrying the ring mark on its outer face
    open_deg = 25
    bpy.ops.object.empty_add(location=pivot)
    root = bpy.context.active_object
    root.name = "lid-root"
    root.rotation_euler = (math.radians(-open_deg), 0, 0)         # the free (-y) edge rises
    LD, LT = 0.226, 0.0075
    box("lid", (W * 0.985, LD, LT), (0, -LD / 2, LT / 2), lid, bevel=0.0035, parent=root)
    box("lid-lip", (W * 0.985, 0.006, LT * 1.1), (0, -0.003, LT / 2), shell, bevel=0.002, parent=root)
    plane_label("mark", (0.052, 0.052), (0, -LD / 2 + 0.01, LT + 0.0002), mark, parent=root)
    bpy.ops.object.select_all(action="SELECT")
    preview("laptop", 0.62, (0, 0, 0.09), azimuth=-145, elevation=22)
    export("laptop", "laptop")


# --------------------------------------------------------------------------------------------------------------- brick

def brick_parts(origin, rotate90=False, prefix=""):
    """One wrapped brick: a bevelled white slab under shrink wrap (a crinkled, slightly glossy skin) and a tan tape band."""
    wrap = bpy.data.materials.get("wrap") or mat("wrap", (0.86, 0.85, 0.82), 0.0, 0.18)
    tape = bpy.data.materials.get("tape") or mat("tape", (0.74, 0.5, 0.2), 0.0, 0.3)
    L, W, T = 0.2, 0.1, 0.045
    x, y, z = origin
    objs = []
    body = box(prefix + "brick", (W, L, T), (x, y, z), wrap, bevel=0.0045)
    sub = body.modifiers.new("crinkle", "DISPLACE")
    tex = bpy.data.textures.new("crinkle", "CLOUDS")
    tex.noise_scale = 0.012
    sub.texture = tex
    sub.strength = 0.0006
    objs.append(body)
    band = box(prefix + "tape", (W * 1.012, 0.052, T * 1.025), (x, y, z), tape, bevel=0.003)
    objs.append(band)
    if rotate90:
        for o in objs:
            o.rotation_euler = (0, 0, math.radians(90))
            o.location = (x, y, z)
    return objs


def narcotics():
    """A single wrapped kilo brick, and a block of them stacked with the tape crossing every layer."""
    reset()
    brick_parts((0, 0, 0.0225))
    bpy.ops.object.select_all(action="SELECT")
    preview("narcotics_brick", 0.5, (0, 0, 0.02), azimuth=30, elevation=30)
    export("narcotics_brick", "narcotics_brick")
    reset()
    layers, T = 8, 0.045
    for k in range(layers):
        if k % 2 == 0:       # four bricks lengthwise: 0.2 x 0.4
            spots = [(ix, iy, False) for ix in (-0.05, 0.05) for iy in (-0.1, 0.1)]
        else:                # crosswise, so the tape crosses every layer and the faces read as a check pattern
            spots = [(0, iy, True) for iy in (-0.15, -0.05, 0.05, 0.15)]
        for ix, iy, turned in spots:
            brick_parts((ix, iy, T / 2 + k * T), rotate90=turned, prefix="b%d_" % k)
    bpy.ops.object.select_all(action="SELECT")
    preview("narcotics_stack", 1.7, (0, 0, 0.18), azimuth=35, elevation=25)
    export("narcotics_stack")


def manifest():
    """A clipboard with a typed sheet under the clip: the cache manifest."""
    reset()
    board = mat("board", (0.2, 0.11, 0.05), 0.0, 0.7)
    clip = mat("clip", (0.7, 0.7, 0.74), 1.0, 0.3)
    paper = mat("paper", (1, 1, 1), 0.0, 0.8, image="manifest_paper.png")
    W, H, T = 0.235, 0.325, 0.006
    box("board", (W, H, T), (0, 0, T / 2), board, bevel=0.003)
    plane_label("sheet", (W * 0.86, H * 0.78), (0, -0.012, T + 0.0004), paper)
    box("clip", (0.085, 0.024, 0.009), (0, H / 2 - 0.016, T + 0.0045), clip, bevel=0.002)
    box("clip-jaw", (0.05, 0.014, 0.004), (0, H / 2 - 0.036, T + 0.0025), clip, bevel=0.0015)
    cyl("ring", 0.0045, 0.004, (0, H / 2 - 0.004, T + 0.0032), clip, rot=(math.radians(90), 0, 0), verts=16)
    lift_to_floor(0)
    preview("manifest", 0.55, (0, 0, 0.0), azimuth=25, elevation=45)
    export("cache_manifest", "cache_manifest")


MODELS = {"manifest": manifest, "usb": usb, "hdd": hdd, "laptop": laptop, "narcotics": narcotics}

if __name__ == "__main__":
    want = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else list(MODELS)
    for name in want:
        MODELS[name]()
        print("built", name)
