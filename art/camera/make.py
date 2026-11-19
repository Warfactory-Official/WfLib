"""Rebuild the model, texture it, save camera.blend and export the GLB:  blender -b --python art/camera/make.py"""
import bpy, os
here = os.path.dirname(os.path.abspath(__file__))
exec(open(os.path.join(here, "build.py")).read())
for other in [s for s in bpy.data.scenes if s.name != "WFCamera"]:
    bpy.data.scenes.remove(other)
exec(open(os.path.join(here, "texture.py")).read())
bpy.ops.wm.save_as_mainfile(filepath=os.path.join(here, "camera.blend"))
exec(open(os.path.join(here, "export.py")).read())
