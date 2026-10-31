package com.wf.wflib.view.api;

import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.direct.DirectPass;
import com.wf.gemrender.direct.DirectRenderer;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.texture.VariantUv;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** First-person {@link HandClip} playback: vanilla hands cancelled, {@link PlayerArms} solved onto the fist tracks, props on their tracks. */
public final class HandClipView {
    /** Glb model following the clip's prop track {@code track}. */
    public static final class Prop {
        private final String track;
        private final ResourceLocation model;
        private ModelCache.Handle<GemRenderGltfModel> handle;

        public Prop(String track, ResourceLocation model) {
            this.track = track;
            this.model = model;
        }
    }

    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f POSE = new Matrix4f();
    private static final Matrix4f UPPER = new Matrix4f();
    private static final Matrix4f LOWER = new Matrix4f();
    private static final Vector3f WRIST = new Vector3f();
    private static final Vector3f SHOULDER = new Vector3f();
    private static final Vector3f ELBOW = new Vector3f();
    private static final Vector3f AT = new Vector3f();
    private static final Quaternionf HAND = new Quaternionf();
    private static final float[] CAMERA = new float[3];

    private HandClipView() {
    }

    /** Cancels the hand render and draws {@code clip} at {@code seconds}; main hand event only. */
    public static void render(RenderHandEvent event, LocalPlayer player, HandClip clip, float seconds, Prop... props) {
        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        PlayerArms arms = PlayerArms.get();
        if (arms == null) {
            return;
        }
        int light = event.getPackedLight();
        VIEW.set(event.getPoseStack().last().pose());
        for (int side = 0; side < 2; side++) {
            clip.sample(side == 0 ? clip.right : clip.left, seconds, WRIST, HAND);
            ArmViewPose.shoulder(side == 1, SHOULDER);
            float twist = ArmViewPose.solve(WRIST, HAND, SHOULDER,
                    side == 0 ? ArmViewPose.RIGHT_POLE : ArmViewPose.LEFT_POLE, arms.lengths, UPPER, LOWER, ELBOW);
            arms.pose(side, UPPER, LOWER, twist, ArmViewPose.swing(HAND, LOWER), ArmLimits.DEFAULT.twist());
        }
        arms.submit(VIEW, light, 0xFFFFFFFF, player);
        for (Prop prop : props) {
            if (prop.handle == null) {
                prop.handle = GemRenderModels.handle(prop.model);
            }
            GemRenderGltfModel model = prop.handle.get();
            float[][] track = clip.prop(prop.track);
            float scale = track == null ? 0.0f : clip.scale(track, seconds);
            if (model == null || scale <= 0.0f) {
                continue;
            }
            clip.sample(track, seconds, AT, HAND);
            POSE.set(VIEW).translate(AT).rotate(HAND).scale(scale);
            DirectRenderer.submit(model, null, 0, POSE, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF, DirectPass.HAND,
                    VariantUv.NONE, slot -> null);
        }
    }

    /** Adds the clip's camera effector to {@code event}. */
    public static void camera(ViewportEvent.ComputeCameraAngles event, HandClip clip, float seconds) {
        clip.sampleCamera(seconds, CAMERA);
        event.setYaw(event.getYaw() + CAMERA[0]);
        event.setPitch(event.getPitch() + CAMERA[1]);
        event.setRoll(event.getRoll() + CAMERA[2]);
    }
}
