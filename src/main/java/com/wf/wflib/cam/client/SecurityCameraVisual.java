package com.wf.wflib.cam.client;

import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.render.GemRenderInstance;
import com.wf.gemrender.render.GemRenderInstanceTypes;
import com.wf.gemrender.render.PoseCache;
import com.wf.gemrender.render.PoseLod;
import com.wf.wflib.cam.SecurityCameraBlock;
import com.wf.wflib.cam.SecurityCameraBlockEntity;
import com.wf.wflib.core.api.WFLib;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.function.Consumer;

/**
 * Draws a wall camera as three parts that move against each other: the bracket (fixed), the pan head (turns about the vertical
 * through the arm's end) and the housing (tilts on the head). The server sends the angles; this only poses the parts.
 * Model axes: the lens looks along -Z with the block facing north, and the wall is at +Z.
 */
public class SecurityCameraVisual extends AbstractBlockEntityVisual<SecurityCameraBlockEntity> implements SimpleDynamicVisual {
    /** Pivots in the models' axes (Blender's +Y forward / +Z up become glTF's -Z forward / +Y up): the pan axis, and the tilt axis relative to it. */
    private static final float PAN_Z = 0.25f, TILT_Y = 0.05f;
    /** Drawn this much bigger than the model's real size so it reads at Minecraft distances; scaled about the wall it is fixed to. */
    private static final float SCALE = 1.6f;

    private static final ModelCache.Handle<GemRenderGltfModel> BASE = handle("security_camera_base"),
            PAN = handle("security_camera_pan"), TILT = handle("security_camera_tilt");

    private final GemRenderInstance[] parts = new GemRenderInstance[3];
    private final Matrix4f base = new Matrix4f(), pan = new Matrix4f(), tilt = new Matrix4f();
    private float shownPan = Float.NaN, shownTilt = Float.NaN;

    public SecurityCameraVisual(VisualizationContext ctx, SecurityCameraBlockEntity be, float partialTick) {
        super(ctx, be, partialTick);
    }

    private static ModelCache.Handle<GemRenderGltfModel> handle(String name) {
        return GemRenderModels.handle(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "models/block/" + name + ".glb"));
    }

    @Override
    public void beginFrame(Context ctx) {
        ModelCache.Handle<GemRenderGltfModel>[] handles = handles();
        for (int i = 0; i < 3; i++) {                                  // a model still importing is asked again next frame
            if (parts[i] == null) {
                GemRenderGltfModel model = handles[i].get();
                if (model == null) continue;
                parts[i] = instancerProvider().instancer(GemRenderInstanceTypes.SKINNED, model.model()).createInstance();
                parts[i].colorArgb(0xFFFFFFFF);
                relight(parts[i]);
                shownPan = Float.NaN;
            }
        }
        float p = Mth.rotLerp(ctx.partialTick(), blockEntity.prevPan, blockEntity.drawPan);
        float t = Mth.lerp(ctx.partialTick(), blockEntity.prevTilt, blockEntity.drawTilt);
        int lod = PoseLod.getInstance().levelAt(pos.distToCenterSqr(ctx.camera().getPosition()));
        boolean moved = Float.compare(p, shownPan) != 0 || Float.compare(t, shownTilt) != 0;
        shownPan = p;
        shownTilt = t;
        float facing = blockState.getValue(SecurityCameraBlock.FACING).toYRot();
        base.translation(visualPos.getX() + 0.5f, visualPos.getY() + 0.5f, visualPos.getZ() + 0.5f)
                .rotateY((float) Math.toRadians(facing - 180.0f))
                .translate(0, 0, 0.5f).scale(SCALE).translate(0, 0, -0.5f);       // bigger, but still bolted to the wall
        pan.set(base).translate(0, 0, PAN_Z).rotateY((float) Math.toRadians(-p));
        tilt.set(pan).translate(0, TILT_Y, 0).rotateX((float) Math.toRadians(-t));
        Matrix4f[] poses = {base, pan, tilt};
        for (int i = 0; i < 3; i++) {
            GemRenderInstance part = parts[i];
            GemRenderGltfModel model = handles[i].get();
            if (part == null || model == null) continue;
            PoseCache.Pose posed = PoseCache.getInstance().pose(model.layout(), model.bounds(), model.morphs(), (GltfAnimation) null, 0.0f, lod);
            if (moved || part.boneBase != posed.boneBase() || !part.boneSphere.equals(posed.sphere())) {
                part.pose.set(poses[i]);
                part.boneBase = posed.boneBase();
                part.morphBase = posed.morphBase();
                part.boneSphere.set(posed.sphere());
                part.setChanged();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static ModelCache.Handle<GemRenderGltfModel>[] handles() {
        return new ModelCache.Handle[]{BASE, PAN, TILT};
    }

    @Override
    public void updateLight(float partialTick) {
        for (GemRenderInstance part : parts) if (part != null) relight(part);
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        for (GemRenderInstance part : parts) if (part != null) consumer.accept(part);
    }

    @Override
    protected void _delete() {
        for (int i = 0; i < 3; i++) {
            if (parts[i] != null) {
                parts[i].delete();
                parts[i] = null;
            }
        }
    }
}
