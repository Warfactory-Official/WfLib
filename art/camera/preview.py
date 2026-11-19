"""Textured preview renders of camera.blend:  blender -b art/camera/camera.blend --python art/camera/preview.py -- <outdir>"""
import bpy, sys, math
from mathutils import Vector
out = sys.argv[sys.argv.index("--") + 1] if "--" in sys.argv else "."
sc = bpy.data.scenes["WFCamera"]
sc.render.engine = 'BLENDER_EEVEE'
sc.render.resolution_x, sc.render.resolution_y = 1200, 800
sc.view_settings.view_transform = 'Standard'
co = sc.camera
for o in sc.objects:
    if o.type == 'LIGHT':
        o.data.energy *= 0.5
def shot(name, loc, target):
    co.location = loc
    co.rotation_euler = (Vector(target) - co.location).to_track_quat('-Z', 'Y').to_euler()
    sc.render.filepath = f"{out}/tex_{name}.png"
    bpy.ops.render.render(write_still=True)
shot("front34", (-0.20, -0.30, 0.12), (0, -0.012, 0))
shot("back", (0.12, 0.34, 0.10), (0, 0, 0))
shot("front", (0.0, -0.42, 0.0), (0, -0.012, 0))
