"""Pixel-art texture for the camera: UV-map every part into one atlas, bake painted-style shading, pixelate.

Run after build.py (headless):  blender -b --python art/camera/make.py

1. Planar / cylindrical UVs per part, packed into ATLAS x ATLAS at the largest uniform texel density that fits.
2. Each source material becomes an emission "paint" shader (colour, fine noise, ambient occlusion, bevel edge
   highlights, a fixed top-left light, per-material patterns). Cycles bakes them into one image at BAKE px.
3. The bake is box-downsampled to ATLAS px, quantised to a small palette (hard pixel steps, no dithering), and the
   screen UI / dial marks are painted on afterwards (no text).
Outputs: camera.png (atlas), uv_layout.json (island rectangles), the objects keep the UVs and get material 'camera'.
"""
import bpy, bmesh, math, json, os
from mathutils import Vector

HERE = os.path.dirname(os.path.abspath(__file__))
ATLAS = int(os.environ.get("CAMERA_ATLAS", "256"))
BAKE = ATLAS * 4
PALETTE = int(os.environ.get("CAMERA_PALETTE", "56"))
BRIGHT = float(os.environ.get("CAMERA_BRIGHT", "1.7"))

sc = bpy.data.scenes["WFCamera"]
if bpy.context.window:
    bpy.context.window.scene = sc
objs = [o for o in sc.objects if o.type == 'MESH']

# ------------------------------------------------------------------ 1. UV layout
CYL_Y = ("lens_base", "lens_ring", "lens_groove", "lens_bezel", "lens_inner", "lens_mid", "lens_core", "af_lamp",
         "back_btn", "back_wheel")
CYL_Z = ("zoom_collar", "shutter", "mode_dial", "dial_disc", "movie_btn", "power_btn", "tripod_socket")


def cyl_axis(name):
    if name.startswith(CYL_Y):
        return 1
    if name.startswith(CYL_Z):
        return 2
    return None


islands = []          # dict(obj, key, faces[(face_index, [(co2d...)])], w, h)
for o in objs:
    me = o.data
    bm = bmesh.new(); bm.from_mesh(me)
    bm.faces.ensure_lookup_table()
    mw = o.matrix_world
    nm = mw.to_3x3()
    axis = cyl_axis(o.name)
    centre = mw.translation.copy()
    groups = {}
    for f in bm.faces:
        n = (nm @ f.normal).normalized()
        pts = [mw @ l.vert.co for l in f.loops]
        if axis is not None and abs(n[axis]) < 0.75:
            # wall: unwrap the angle about the axis
            ax2 = [i for i in range(3) if i != axis]
            if axis == 1:
                ax2 = [2, 0]          # Z -> X is counter-clockwise about +Y
            ang = []
            ref = None
            for p in pts:
                a = math.atan2(p[ax2[1]] - centre[ax2[1]], p[ax2[0]] - centre[ax2[0]])
                if ref is None:
                    ref = a
                while a - ref > math.pi: a -= 2 * math.pi
                while a - ref < -math.pi: a += 2 * math.pi
                ang.append(a)
            rad = sum(math.hypot(p[ax2[0]] - centre[ax2[0]], p[ax2[1]] - centre[ax2[1]]) for p in pts) / len(pts)
            co = [(a * rad * 1000.0, p[axis] * 1000.0) for a, p in zip(ang, pts)]
            key = "wall"
        else:
            k = max(range(3), key=lambda i: abs(n[i])) if axis is None else axis
            sign = 1 if n[k] >= 0 else -1
            ax2 = [i for i in range(3) if i != k]
            # u x v = outward normal, so a face read from outside is never mirrored (Y is the odd axis): negate u
            neg = (sign < 0) != (k == 1)
            co = [((-1 if neg else 1) * p[ax2[0]] * 1000.0, p[ax2[1]] * 1000.0) for p in pts]
            key = f"{'xyz'[k]}{'+' if sign > 0 else '-'}"
        groups.setdefault(key, []).append((f.index, co))
    for key, faces in groups.items():
        us = [c[0] for _, co in faces for c in co]; vs = [c[1] for _, co in faces for c in co]
        islands.append(dict(obj=o, key=key, faces=faces, minu=min(us), minv=min(vs),
                            w=max(us) - min(us), h=max(vs) - min(vs)))
    bm.free()

PAD = 1.5   # texels around each island
# Parts that carry detail get more texels per mm (mixed density, as in game textures); the rest share what is left.
BOOST = {"lens_window": 2.2, "lens_window_rim": 1.4, "lens_inner": 2.2, "lens_mid": 2.2, "lens_core": 2.2,
         "screen": 1.35, "dial_disc": 1.6, "af_lamp": 1.6}


def pack(density):
    """Shelf pack; returns placements or None."""
    order = sorted(islands, key=lambda i: -i["h"] * BOOST.get(i["obj"].name, 1.0))
    x = y = shelf = 0.0
    place = {}
    for isl in order:
        dens = density * BOOST.get(isl["obj"].name, 1.0)
        w = isl["w"] * dens + 2 * PAD
        h = isl["h"] * dens + 2 * PAD
        if w > ATLAS or h > ATLAS:
            return None
        if x + w > ATLAS:
            x = 0.0; y += shelf; shelf = 0.0
        if y + h > ATLAS:
            return None
        place[id(isl)] = (x, y)
        x += w; shelf = max(shelf, h)
    return place


lo, hi = 0.05, 8.0
best = None
for _ in range(30):
    mid = (lo + hi) / 2
    p = pack(mid)
    if p:
        best, lo = (mid, p), mid
    else:
        hi = mid
density, placement = best
density *= 0.985
placement = pack(density)
print(f"atlas {ATLAS}px, density {density:.3f} texel/mm = {1 / density:.2f} mm per texel, islands {len(islands)}")

layout = {}
for o in objs:
    me = o.data
    bm = bmesh.new(); bm.from_mesh(me)
    bm.faces.ensure_lookup_table()
    uv = bm.loops.layers.uv.get("UVMap") or bm.loops.layers.uv.new("UVMap")
    for isl in [i for i in islands if i["obj"] is o]:
        ox, oy = placement[id(isl)]
        dens = density * BOOST.get(o.name, 1.0)
        layout[f"{o.name}:{isl['key']}"] = [ox, oy, isl["w"] * dens + 2 * PAD, isl["h"] * dens + 2 * PAD]
        for fi, co in isl["faces"]:
            f = bm.faces[fi]
            for loop, (u, v) in zip(f.loops, co):
                px = ox + PAD + (u - isl["minu"]) * dens
                py = oy + PAD + (v - isl["minv"]) * dens
                loop[uv].uv = (px / ATLAS, py / ATLAS)
    bm.to_mesh(me); bm.free()
json.dump(dict(atlas=ATLAS, density=density, islands=layout), open(os.path.join(HERE, "uv_layout.json"), "w"), indent=1)


# ------------------------------------------------------------------ 2. paint shaders
def lin(c):
    f = lambda x: x / 12.92 if x <= 0.04045 else ((x + 0.055) / 1.055) ** 2.4
    return tuple(f(x) for x in c)


img = bpy.data.images.get("camera_atlas") or bpy.data.images.new("camera_atlas", BAKE, BAKE, alpha=False)
img.scale(BAKE, BAKE)
img.colorspace_settings.name = 'sRGB'
img.generated_color = (0, 0, 0, 1)

LIGHT = Vector((-0.30, -0.50, 0.80)).normalized()


def paint_material(name, base, noise=0.12, noise_scale=900, edge=0.35, ao_dark=0.72, extra=None):
    base = tuple(min(1.0, c * BRIGHT) for c in base)
    m = bpy.data.materials.new("paint_" + name)
    m.use_nodes = True
    nt = m.node_tree
    nt.nodes.clear()
    L = nt.links
    N = nt.nodes.new

    def node(t, **kw):
        n = N(t)
        for k, v in kw.items():
            setattr(n, k, v)
        return n

    def mathn(op, a=None, b=None, clamp=False):
        n = N('ShaderNodeMath'); n.operation = op; n.use_clamp = clamp
        for i, v in enumerate((a, b)):
            if v is None:
                continue
            if isinstance(v, (int, float)):
                n.inputs[i].default_value = v
            else:
                L.new(v, n.inputs[i])
        return n.outputs[0]

    def vmath(op, a, b=None):
        n = N('ShaderNodeVectorMath'); n.operation = op
        for i, v in enumerate((a, b)):
            if v is None:
                continue
            if isinstance(v, (tuple, Vector)):
                n.inputs[i].default_value = v
            else:
                L.new(v, n.inputs[i])
        return n

    def mix(fac, a, b):
        n = N('ShaderNodeMix'); n.data_type = 'RGBA'
        L.new(fac, n.inputs[0]) if not isinstance(fac, (int, float)) else setattr(n.inputs[0], 'default_value', fac)
        for i, v in ((6, a), (7, b)):
            if isinstance(v, tuple):
                n.inputs[i].default_value = (*v, 1.0)
            else:
                L.new(v, n.inputs[i])
        return n.outputs[2]

    tc = N('ShaderNodeTexCoord')
    obj = tc.outputs['Object']
    base_l = lin(base)
    col = None
    noise_node = node('ShaderNodeTexNoise', noise_dimensions='3D')
    noise_node.inputs['Scale'].default_value = noise_scale
    noise_node.inputs['Detail'].default_value = 2.0
    L.new(obj, noise_node.inputs['Vector'])
    nfac = mathn('SUBTRACT', noise_node.outputs['Fac'], 0.5)
    nmul = mathn('MULTIPLY', nfac, noise * 2.0)
    nadd = mathn('ADD', nmul, 1.0)
    colour = extra(nt, obj, base_l, nadd, mathn, vmath, mix, node, L) if extra else None
    if colour is None:
        rgb = N('ShaderNodeRGB'); rgb.outputs[0].default_value = (*base_l, 1.0)
        mulc = N('ShaderNodeMix'); mulc.data_type = 'RGBA'; mulc.blend_type = 'MULTIPLY'
        mulc.inputs[0].default_value = 1.0
        L.new(rgb.outputs[0], mulc.inputs[6])
        # noise factor into a grey colour
        g = N('ShaderNodeCombineColor')
        for ch in ('Red', 'Green', 'Blue'):
            L.new(nadd, g.inputs[ch])
        L.new(g.outputs[0], mulc.inputs[7])
        colour = mulc.outputs[2]
    # ambient occlusion + fixed top-left light + bevel edge highlight
    ao = N('ShaderNodeAmbientOcclusion'); ao.samples = 12
    ao.inputs['Distance'].default_value = 0.014
    geo = N('ShaderNodeNewGeometry')
    ndotl = vmath('DOT_PRODUCT', geo.outputs['Normal'], tuple(LIGHT))
    light = mathn('ADD', mathn('MULTIPLY', ndotl.outputs['Value'], 0.30), 0.84)
    shade = mathn('ADD', mathn('MULTIPLY', ao.outputs['AO'], 1.0 - ao_dark), ao_dark)
    bev = N('ShaderNodeBevel'); bev.samples = 10; bev.inputs['Radius'].default_value = 0.0007
    d = vmath('DOT_PRODUCT', bev.outputs['Normal'], geo.outputs['Normal'])
    e = mathn('MULTIPLY', mathn('SUBTRACT', 1.0, d.outputs['Value']), 9.0, clamp=True)
    mulA = N('ShaderNodeMix'); mulA.data_type = 'RGBA'; mulA.blend_type = 'MULTIPLY'; mulA.inputs[0].default_value = 1.0
    L.new(colour, mulA.inputs[6])
    g2 = N('ShaderNodeCombineColor')
    s_l = mathn('MULTIPLY', shade, light)
    for ch in ('Red', 'Green', 'Blue'):
        L.new(s_l, g2.inputs[ch])
    L.new(g2.outputs[0], mulA.inputs[7])
    hi = tuple(min(1.0, c * 3.2 + 0.03) for c in base_l)
    final = mix(mathn('MULTIPLY', e, edge), mulA.outputs[2], hi)
    em = N('ShaderNodeEmission'); em.inputs['Strength'].default_value = 1.0
    L.new(final, em.inputs['Color'])
    outn = N('ShaderNodeOutputMaterial')
    L.new(em.outputs[0], outn.inputs['Surface'])
    # bake target
    it = N('ShaderNodeTexImage'); it.image = img; it.interpolation = 'Closest'
    nt.nodes.active = it
    return m


def radial(nt, obj, base_l, nadd, mathn, vmath, mix, node, L, stops, hl=None):
    """Colour by distance from the lens axis (object space, axis = Y), with an optional specular blob."""
    # world position relative to the lens axis (the barrel parts are rotated, so object space would not be concentric)
    geo_p = node('ShaderNodeNewGeometry')
    rel = vmath('SUBTRACT', geo_p.outputs['Position'], (0.0040, 0.0, -0.0020))
    sep = node('ShaderNodeSeparateXYZ'); L.new(rel.outputs[0], sep.inputs[0])
    x2 = mathn('MULTIPLY', sep.outputs['X'], sep.outputs['X'])
    z2 = mathn('MULTIPLY', sep.outputs['Z'], sep.outputs['Z'])
    r = mathn('SQRT', mathn('ADD', x2, z2))
    ramp = node('ShaderNodeValToRGB'); ramp.color_ramp.interpolation = 'CONSTANT'
    L.new(r, ramp.inputs['Fac'])
    els = ramp.color_ramp.elements
    while len(els) < len(stops):
        els.new(0.5)
    for el, (pos, colr) in zip(els, stops):
        el.position = pos; el.color = (*lin(colr), 1.0)
    out = ramp.outputs['Color']
    if hl:
        cx, cz, rad, gain = hl
        dx = mathn('SUBTRACT', sep.outputs['X'], cx); dz = mathn('SUBTRACT', sep.outputs['Z'], cz)
        dd = mathn('SQRT', mathn('ADD', mathn('MULTIPLY', dx, dx), mathn('MULTIPLY', dz, dz)))
        mr = node('ShaderNodeMapRange'); mr.clamp = True
        mr.inputs['From Min'].default_value = rad * 0.55; mr.inputs['From Max'].default_value = rad
        mr.inputs['To Min'].default_value = gain; mr.inputs['To Max'].default_value = 0.0
        L.new(dd, mr.inputs['Value'])
        out = mix(mr.outputs[0], out, (1.0, 1.0, 1.0))
    return out


def diamonds(nt, obj, base_l, nadd, mathn, vmath, mix, node, L, a, b, scale):
    """Diamond knurl on the XZ plane."""
    mp = node('ShaderNodeMapping'); mp.vector_type = 'POINT'
    mp.inputs['Rotation'].default_value = (0, math.radians(45), 0)
    mp.inputs['Scale'].default_value = (scale, scale, scale)
    L.new(obj, mp.inputs['Vector'])
    ck = node('ShaderNodeTexChecker'); ck.inputs['Scale'].default_value = 1.0
    L.new(mp.outputs[0], ck.inputs['Vector'])
    return mix(ck.outputs['Fac'], lin(a), lin(b))


def streaks(nt, obj, base_l, nadd, mathn, vmath, mix, node, L, a, b, scale):
    """Brushed metal: noise squashed along the circumference."""
    mp = node('ShaderNodeMapping'); mp.vector_type = 'POINT'
    mp.inputs['Scale'].default_value = (scale * 0.04, scale, scale * 0.04)
    L.new(obj, mp.inputs['Vector'])
    nz = node('ShaderNodeTexNoise', noise_dimensions='3D'); nz.inputs['Detail'].default_value = 1.0
    nz.inputs['Scale'].default_value = 1.0
    L.new(mp.outputs[0], nz.inputs['Vector'])
    return mix(nz.outputs['Fac'], lin(a), lin(b))


SPEC = {
    "cam_body":   dict(base=(0.125, 0.130, 0.145), noise=0.07, noise_scale=700, edge=0.55),
    "cam_top":    dict(base=(0.175, 0.180, 0.200), noise=0.06, noise_scale=700, edge=0.65),
    "cam_barrel": dict(base=(0.095, 0.100, 0.115), noise=0.10, noise_scale=1600, edge=0.50,
                       extra=lambda *a: streaks(*a, (0.08, 0.085, 0.10), (0.15, 0.155, 0.175), 700)),
    "cam_ring":   dict(base=(0.22, 0.23, 0.25), noise=0.06, edge=0.30,
                       extra=lambda *a: streaks(*a, (0.14, 0.145, 0.16), (0.27, 0.28, 0.31), 900)),
    "cam_rubber": dict(base=(0.055, 0.055, 0.065), noise=0.12, noise_scale=900, edge=0.25, ao_dark=0.6),
    "cam_grip":   dict(base=(0.085, 0.085, 0.095), noise=0.14, edge=0.55),
    "cam_button": dict(base=(0.20, 0.21, 0.23), noise=0.05, edge=0.35),
    "cam_red":    dict(base=(0.80, 0.10, 0.08), noise=0.06, edge=0.35),
    "cam_lamp":   dict(base=(0.18, 0.20, 0.23), noise=0.04, edge=0.40),
    "cam_port":   dict(base=(0.030, 0.030, 0.038), noise=0.20, noise_scale=1500, edge=0.25),
    "cam_screen": dict(base=(0.040, 0.062, 0.095), noise=0.06, edge=0.20),
    "cam_glass":  dict(base=(0.05, 0.10, 0.20), noise=0.0, edge=0.10,
                       extra=lambda *a: radial(*a, [(0.0, (0.03, 0.05, 0.10)), (0.0040, (0.07, 0.17, 0.30)),
                                                    (0.0075, (0.04, 0.10, 0.22)), (0.0105, (0.03, 0.06, 0.13)),
                                                    (0.0125, (0.015, 0.02, 0.04))],
                                               hl=(-0.0042, 0.0042, 0.0042, 0.62))),
    "cam_inner":  dict(base=(0.05, 0.12, 0.08), noise=0.0, edge=0.10,
                       extra=lambda *a: radial(*a, [(0.0, (0.02, 0.03, 0.04)), (0.0035, (0.10, 0.24, 0.16)),
                                                    (0.0065, (0.34, 0.26, 0.10)), (0.0082, (0.10, 0.20, 0.30)),
                                                    (0.0098, (0.04, 0.07, 0.12))],
                                               hl=(0.0030, 0.0030, 0.0030, 0.45))),
}
# the grip pad gets the diamond knurl through its own material variant
SPEC["cam_rubber"]["extra"] = lambda *a: diamonds(*a, (0.030, 0.030, 0.038), (0.090, 0.090, 0.105), 260)

for o in objs:
    src = o.data.materials[0].name
    if src not in SPEC:
        raise SystemExit(f"no paint spec for {src}")

paints = {}
for name, spec in SPEC.items():
    paints[name] = paint_material(name, **spec)
for o in objs:
    src = o.data.materials[0].name
    o.data.materials.clear()
    o.data.materials.append(paints[src])

# ------------------------------------------------------------------ 3. bake
sc.render.engine = 'CYCLES'
sc.cycles.device = 'CPU'
sc.cycles.samples = 12
sc.cycles.use_denoising = False
sc.render.bake.margin = 3
sc.render.bake.use_clear = True
sc.render.bake.target = 'IMAGE_TEXTURES'
vl = sc.view_layers[0]
for o in vl.objects:
    o.select_set(False)
for o in objs:
    o.select_set(True)
vl.objects.active = objs[0]
bpy.ops.object.bake(type='EMIT')
raw = os.path.join(HERE, "camera_bake.png")
img.filepath_raw = raw
img.file_format = 'PNG'
img.save()
print("baked", raw)

# ------------------------------------------------------------------ 4. pixelate + paint UI
from PIL import Image, ImageDraw

pic = Image.open(raw).convert("RGB").resize((ATLAS, ATLAS), Image.BOX)
pic = pic.quantize(colors=PALETTE, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).convert("RGB")
d = ImageDraw.Draw(pic)


def island(name):
    x, y, w, h = layout[name]
    # atlas Y is bottom-up in Blender; PIL is top-down
    return x + PAD, ATLAS - (y + h - PAD), w - 2 * PAD, h - 2 * PAD


# screen UI (back face of the screen: normal +Y => key y+)
for key in layout:
    if key == "screen:y+":
        x0, y0, w, h = island(key)
        x1, y1 = x0 + w, y0 + h
        bg = (10, 24, 40)
        d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=bg)
        # scene-ish gradient (sky -> ground) so the screen reads as a live view
        for yy in range(int(y0), int(y1)):
            t = (yy - y0) / max(1, h)
            col = (int(30 + 40 * (1 - t)), int(60 + 50 * (1 - t)), int(96 + 70 * (1 - t))) if t < 0.58 else \
                  (int(34 + 20 * t), int(60 + 30 * t), int(34 + 6 * t))
            d.line([x0 + 1, yy, x1 - 2, yy], fill=col)
        ui = (150, 230, 255)
        m = 3
        d.rectangle([x0, y0, x1 - 1, y0 + 4], fill=(8, 14, 24))                      # top bar
        d.rectangle([x1 - 16, y0 + 1, x1 - 5, y0 + 3], outline=ui)                    # battery
        d.rectangle([x1 - 14, y0 + 2, x1 - 9, y0 + 2], fill=ui)
        for i in range(3):
            d.rectangle([x0 + 4 + i * 5, y0 + 1, x0 + 6 + i * 5, y0 + 3], fill=ui)     # mode pips
        cx, cy = x0 + w // 2, y0 + h // 2
        for sx, sy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):                           # focus brackets
            bx, by = cx + sx * 11, cy + sy * 7
            d.line([bx, by, bx - sx * 4, by], fill=ui); d.line([bx, by, bx, by - sy * 3], fill=ui)
        d.point([cx, cy], fill=(255, 255, 255))
        d.rectangle([x0, y1 - 5, x1 - 1, y1 - 1], fill=(8, 14, 24))                   # bottom bar
        for i in range(5):
            d.rectangle([x0 + 4 + i * 6, y1 - 4, x0 + 7 + i * 6, y1 - 2], fill=ui if i < 3 else (60, 90, 110))

# mode dial: tick marks on its top disc (cap: normal +Z => key z+)
for key in layout:
    if key == "dial_disc:z+":
        x0, y0, w, h = island(key)
        cx, cy, r = x0 + w / 2, y0 + h / 2, min(w, h) / 2 - 1.5
        for i in range(10):
            a = math.radians(-120 + i * 26.7)
            d.point([int(cx + math.cos(a) * r), int(cy + math.sin(a) * r)], fill=(220, 220, 230))

pic.save(os.path.join(HERE, "camera.png"))
print("texture", os.path.join(HERE, "camera.png"), pic.size)

# ------------------------------------------------------------------ 5. final material for export
final = bpy.data.materials.new("camera")
final.use_nodes = True
b = final.node_tree.nodes["Principled BSDF"]
tex = final.node_tree.nodes.new('ShaderNodeTexImage')
out_img = bpy.data.images.load(os.path.join(HERE, "camera.png"), check_existing=False)
out_img.colorspace_settings.name = 'sRGB'
tex.image = out_img; tex.interpolation = 'Closest'
final.node_tree.links.new(tex.outputs['Color'], b.inputs['Base Color'])
b.inputs['Roughness'].default_value = 1.0
b.inputs['Metallic'].default_value = 0.0
for o in objs:
    o.data.materials.clear()
    o.data.materials.append(final)
