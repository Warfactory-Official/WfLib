import bpy, bmesh, math
from mathutils import Vector

# ---------------------------------------------------------------- scene
if "WFCamera" in bpy.data.scenes:
    sc = bpy.data.scenes["WFCamera"]
    for o in list(sc.objects):
        bpy.data.objects.remove(o, do_unlink=True)
else:
    sc = bpy.data.scenes.new("WFCamera")
if bpy.context.window:
    bpy.context.window.scene = sc
col = sc.collection

def mat(name, rgb, metal=0.0, rough=0.5):
    m = bpy.data.materials.get(name) or bpy.data.materials.new(name)
    m.use_nodes = True
    b = m.node_tree.nodes.get("Principled BSDF")
    b.inputs["Base Color"].default_value = (*rgb, 1.0)
    b.inputs["Metallic"].default_value = metal
    b.inputs["Roughness"].default_value = rough
    m.diffuse_color = (*rgb, 1.0)
    return m

M = {
    "body":    mat("cam_body",    (0.022, 0.022, 0.026), 0.0, 0.45),
    "top":     mat("cam_top",     (0.030, 0.030, 0.034), 0.1, 0.35),
    "rubber":  mat("cam_rubber",  (0.010, 0.010, 0.012), 0.0, 0.95),
    "grip":    mat("cam_grip",    (0.018, 0.018, 0.020), 0.0, 0.9),
    "ring":    mat("cam_ring",    (0.060, 0.060, 0.068), 0.7, 0.30),
    "barrel":  mat("cam_barrel",  (0.012, 0.012, 0.015), 0.4, 0.40),
    "glass":   mat("cam_glass",   (0.020, 0.045, 0.085), 0.2, 0.05),
    "inner":   mat("cam_inner",   (0.035, 0.085, 0.055),  0.4, 0.05),
    "red":     mat("cam_red",     (0.70, 0.04, 0.03),    0.0, 0.40),
    "button":  mat("cam_button",  (0.085, 0.085, 0.095), 0.5, 0.35),
    "lamp":    mat("cam_lamp",    (0.10, 0.10, 0.12),    0.2, 0.10),
    "screen":  mat("cam_screen",  (0.012, 0.020, 0.030), 0.1, 0.06),
    "port":    mat("cam_port",    (0.006, 0.006, 0.008), 0.0, 0.80),
}

objs = []
def finish(o, m, bevel=0.0, segs=2, smooth=True):
    o.data.materials.clear()
    o.data.materials.append(M[m])
    if bevel > 0:
        bv = o.modifiers.new("bev", 'BEVEL')
        bv.width = bevel; bv.segments = segs; bv.limit_method = 'ANGLE'
        bv.angle_limit = math.radians(35)
    if smooth:
        for p in o.data.polygons: p.use_smooth = True
    bpy.context.view_layer.objects.active = o
    o.select_set(True)
    if o.modifiers:
        for md in list(o.modifiers):
            bpy.ops.object.modifier_apply(modifier=md.name)
    o.select_set(False)
    objs.append(o)
    return o

def link(o):
    col.objects.link(o)
    bpy.context.view_layer.objects.active = o
    return o

def box(name, size, loc, m, bevel=0.0, rot=(0, 0, 0), segs=2):
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    bmesh.ops.create_cube(bm, size=1.0)
    for v in bm.verts:
        v.co.x *= size[0]; v.co.y *= size[1]; v.co.z *= size[2]
    bm.to_mesh(me); bm.free()
    o = bpy.data.objects.new(name, me)
    o.location = loc
    o.rotation_euler = rot
    link(o)
    return finish(o, m, bevel, segs)

def cyl(name, r, h, loc, m, axis='Z', verts=32, bevel=0.0, r2=None, segs=2):
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    bmesh.ops.create_cone(bm, cap_ends=True, segments=verts,
                          radius1=r, radius2=(r if r2 is None else r2), depth=h)
    bm.to_mesh(me); bm.free()
    o = bpy.data.objects.new(name, me)
    o.location = loc
    if axis == 'Y':   # barrel axis along Y (lens points -Y)
        o.rotation_euler = (math.radians(90), 0, 0)
    elif axis == 'X':
        o.rotation_euler = (0, math.radians(90), 0)
    link(o)
    return finish(o, m, bevel, segs)


def squircle(name, rx, rz, depth, loc, m, n=4.0, verts=64):
    """Rounded-square prism, axis along Y (front is -Y); |x/rx|^n + |z/rz|^n = 1."""
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    a, b = [], []
    for i in range(verts):
        t = 2 * math.pi * i / verts
        c, s_ = math.cos(t), math.sin(t)
        x = rx * math.copysign(abs(c) ** (2.0 / n), c)
        z = rz * math.copysign(abs(s_) ** (2.0 / n), s_)
        a.append(bm.verts.new((x, -depth / 2, z)))
        b.append(bm.verts.new((x, depth / 2, z)))
    for i in range(verts):
        j = (i + 1) % verts
        bm.faces.new((a[i], a[j], b[j], b[i]))
    bm.faces.new(a[::-1]); bm.faces.new(b)
    bm.to_mesh(me); bm.free()
    o = bpy.data.objects.new(name, me)
    o.location = loc
    link(o)
    return finish(o, m)

# ---------------------------------------------------------------- dimensions (metres)
W, D, H = 0.1096, 0.0262, 0.0638      # body width, depth (Y), height (Z); lens points -Y
Y_FRONT = -D / 2

# main body, rounded
body = box("body", (W, D, H), (0, 0, 0), "body", bevel=0.0035, segs=3)

# top plate: slightly lighter, a little proud, shorter than body
top = box("top_plate", (W - 0.004, D - 0.003, 0.0035), (0, 0.0004, H / 2 + 0.0008), "top", bevel=0.0010)

# flash housing (closed pop-up) on the right of the top plate, with a slit
flash = box("flash_housing", (0.0285, D - 0.007, 0.0045), (0.0335, 0.0010, H / 2 + 0.0037), "top", bevel=0.0010)
box("flash_slit", (0.0150, 0.0016, 0.0006), (0.0335, -0.0040, H / 2 + 0.0061), "port")

# ---------------------------------------------------------------- grip (left when viewed from the front)
gx = -W / 2 + 0.0105
grip = box("grip_bulge", (0.0235, 0.0105, H - 0.010), (gx, Y_FRONT - 0.0045, -0.001), "body", bevel=0.0040, segs=3)
pad = box("grip_pad", (0.0165, 0.0016, H - 0.0225), (gx - 0.0005, Y_FRONT - 0.0098, -0.0015), "rubber", bevel=0.0012)
# diagonal ridges on the pad
for i in range(9):
    z = -0.019 + i * 0.0042
    r = box(f"grip_ridge{i}", (0.0128, 0.0006, 0.0011), (gx - 0.0005, Y_FRONT - 0.0108, z), "grip",
            rot=(0, math.radians(32), 0))
    r.rotation_euler[1] = math.radians(32)

# ---------------------------------------------------------------- lens barrel (telescoped out)
LX, LZ = 0.0040, -0.0020
stages = [
    # name, radius, length, y centre (front is -Y)
    ("lens_base",  0.0252, 0.0062, Y_FRONT - 0.0030, "barrel"),
    ("lens_ring1", 0.0236, 0.0120, Y_FRONT - 0.0105, "barrel"),
    ("lens_ring2", 0.0212, 0.0110, Y_FRONT - 0.0200, "ring"),
    ("lens_ring3", 0.0186, 0.0100, Y_FRONT - 0.0285, "barrel"),
]
for n, r, ln, y, m in stages:
    cyl(n, r, ln, (LX, y, LZ), m, axis='Y', verts=48, bevel=0.0007)
# decorative grooves on the barrel
for i, y in enumerate((Y_FRONT - 0.0070, Y_FRONT - 0.0145, Y_FRONT - 0.0245)):
    cyl(f"lens_groove{i}", 0.0243 - i * 0.0024, 0.0010, (LX, y, LZ), "port", axis='Y', verts=48)
# front bezel: wide dark ring, then a recessed rounded-square glass window
bez_y = Y_FRONT - 0.0345
cyl("lens_bezel", 0.0204, 0.0045, (LX, bez_y, LZ), "barrel", axis='Y', verts=48, bevel=0.0008)
cyl("lens_bezel_lip", 0.0176, 0.0010, (LX, bez_y - 0.0025, LZ), "ring", axis='Y', verts=48)
# glass window: cylinder body whose front disc is the glass (square-ish via box)
squircle("lens_window_rim", 0.0150, 0.0142, 0.0022, (LX, bez_y - 0.0028, LZ), "barrel")
squircle("lens_window", 0.0132, 0.0124, 0.0014, (LX, bez_y - 0.0040, LZ), "glass")
cyl("lens_inner", 0.0098, 0.0016, (LX, bez_y - 0.0052, LZ), "inner", axis='Y', verts=40, bevel=0.0004)
cyl("lens_mid", 0.0066, 0.0014, (LX, bez_y - 0.0062, LZ), "glass", axis='Y', verts=32)
cyl("lens_core", 0.0030, 0.0012, (LX, bez_y - 0.0070, LZ), "port", axis='Y', verts=24)

# AF assist lamp, upper left of the lens
cyl("af_lamp", 0.0028, 0.0020, (-0.0185, Y_FRONT - 0.0008, 0.0185), "lamp", axis='Y', verts=20)

# ---------------------------------------------------------------- top controls
TZ = H / 2 + 0.0026                      # top of the top plate
# zoom collar around the shutter button, with the lever tab
cyl("zoom_collar", 0.0112, 0.0030, (-0.0230, 0.0002, TZ + 0.0016), "ring", verts=40, bevel=0.0005)
box("zoom_lever", (0.0090, 0.0050, 0.0030), (-0.0300, -0.0070, TZ + 0.0016), "ring", bevel=0.0010)
cyl("shutter", 0.0072, 0.0042, (-0.0230, 0.0002, TZ + 0.0049), "button", verts=32, bevel=0.0008, r2=0.0068)
cyl("shutter_cap", 0.0046, 0.0012, (-0.0230, 0.0002, TZ + 0.0076), "top", verts=24)

# mode dial: knurled
dial = bpy.data.meshes.new("mode_dial")
bm = bmesh.new()
n = 36
ring_top, ring_bot = [], []
for i in range(n):
    a = 2 * math.pi * i / n
    r = 0.0098 if i % 2 == 0 else 0.0090
    ring_top.append(bm.verts.new((r * math.cos(a), r * math.sin(a), 0.0024)))
    ring_bot.append(bm.verts.new((r * math.cos(a), r * math.sin(a), -0.0024)))
for i in range(n):
    j = (i + 1) % n
    bm.faces.new((ring_bot[i], ring_bot[j], ring_top[j], ring_top[i]))
bm.faces.new(ring_top[::-1]); bm.faces.new(ring_bot)
bm.to_mesh(dial); bm.free()
do = bpy.data.objects.new("mode_dial", dial)
do.location = (-0.0420, 0.0000, TZ + 0.0010)
link(do); finish(do, "ring", bevel=0.0004)
cyl("dial_disc", 0.0070, 0.0012, (-0.0420, 0.0000, TZ + 0.0040), "body", verts=28)
box("dial_mark", (0.0016, 0.0050, 0.0006), (-0.0420, -0.0012, TZ + 0.0049), "button")

# movie (red) and on/off buttons, small
cyl("movie_btn", 0.0046, 0.0022, (-0.0485, -0.0058, TZ + 0.0004), "red", verts=24, bevel=0.0005)
cyl("power_btn", 0.0052, 0.0024, (0.0020, 0.0008, TZ + 0.0006), "button", verts=24, bevel=0.0006)

# ---------------------------------------------------------------- right side: port cover + strap lug
box("port_cover", (0.0014, 0.0120, 0.0160), (W / 2 + 0.0004, 0.0010, -0.0060), "port", bevel=0.0004)
box("strap_lug", (0.0030, 0.0050, 0.0050), (W / 2 + 0.0010, 0.0000, 0.0215), "ring", bevel=0.0008)
box("strap_lug_L", (0.0030, 0.0050, 0.0050), (-W / 2 - 0.0010, 0.0000, 0.0215), "ring", bevel=0.0008)

# ---------------------------------------------------------------- back: screen, thumb rest, buttons
BY = D / 2
box("screen_frame", (0.0820, 0.0012, 0.0535), (-0.0100, BY + 0.0003, 0.0000), "port", bevel=0.0010)
box("screen", (0.0765, 0.0008, 0.0485), (-0.0100, BY + 0.0010, 0.0000), "screen", bevel=0.0006)
box("thumb_rest", (0.0100, 0.0040, 0.0150), (0.0462, BY + 0.0010, 0.0140), "rubber", bevel=0.0016, segs=3)
cyl("back_btn0", 0.0036, 0.0020, (0.0345, BY + 0.0007, 0.0225), "button", axis='Y', verts=20, bevel=0.0004)
cyl("back_wheel", 0.0098, 0.0018, (0.0425, BY + 0.0006, -0.0085), "ring", axis='Y', verts=40, bevel=0.0004)
cyl("back_wheel_hub", 0.0052, 0.0016, (0.0425, BY + 0.0017, -0.0085), "button", axis='Y', verts=28, bevel=0.0004)
cyl("back_btn1", 0.0036, 0.0020, (0.0360, BY + 0.0007, -0.0255), "button", axis='Y', verts=20, bevel=0.0004)
cyl("back_btn2", 0.0036, 0.0020, (0.0490, BY + 0.0007, -0.0255), "button", axis='Y', verts=20, bevel=0.0004)

# ---------------------------------------------------------------- bottom: tripod socket + hatch line
cyl("tripod_socket", 0.0035, 0.0010, (0.0120, 0.0000, -H / 2 - 0.0002), "ring", verts=20)

# ---------------------------------------------------------------- group under one parent
root = bpy.data.objects.new("camera_src", None)
col.objects.link(root)
for o in objs:
    o.parent = root
root.rotation_euler = (0, 0, 0)

# ---------------------------------------------------------------- preview rig
w = sc.world or bpy.data.worlds.new("w")
sc.world = w
w.use_nodes = True
w.node_tree.nodes["Background"].inputs[0].default_value = (0.35, 0.38, 0.44, 1)
w.node_tree.nodes["Background"].inputs[1].default_value = 0.6
for nm, loc, e in (("key", (0.25, -0.35, 0.3), 14), ("fill", (-0.3, -0.2, 0.1), 5), ("back", (0.0, 0.4, 0.25), 8)):
    ld = bpy.data.lights.new(nm, 'AREA'); ld.energy = e; ld.size = 0.25
    lo = bpy.data.objects.new(nm, ld); lo.location = loc
    col.objects.link(lo)
    d = (Vector((0, 0, 0)) - lo.location)
    lo.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()

cam = bpy.data.cameras.new("preview"); cam.lens = 85
co = bpy.data.objects.new("preview_cam", cam); col.objects.link(co)
sc.camera = co
sc.render.engine = 'BLENDER_EEVEE' if 'BLENDER_EEVEE' in [e.identifier for e in bpy.types.RenderSettings.bl_rna.properties['engine'].enum_items] else 'BLENDER_EEVEE_NEXT'
sc.render.resolution_x, sc.render.resolution_y = 1200, 800
sc.view_settings.view_transform = 'Standard'
sc.view_settings.look = 'None'
print("objects:", len(objs), "tris:", sum(len(o.data.polygons) for o in objs))
