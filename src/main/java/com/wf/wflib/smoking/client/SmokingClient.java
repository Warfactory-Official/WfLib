package com.wf.wflib.smoking.client;

import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.direct.DirectPass;
import com.wf.gemrender.direct.DirectRenderer;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.texture.VariantUv;
import com.wf.wflib.WFLib;
import com.wf.wflib.anim.ArmLimits;
import com.wf.wflib.client.view.ArmViewPose;
import com.wf.wflib.client.view.PlayerArms;
import com.wf.wflib.smoking.CigaretteItem;
import com.wf.wflib.smoking.SmokingTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** First person while a {@link CigaretteItem} is in use: GAMMA's hand, pack and cigarette tracks replace the vanilla hands. */
@EventBusSubscriber(modid = WFLib.MODID, value = Dist.CLIENT)
public final class SmokingClient {
    private static final ResourceLocation CIGARETTE = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "models/smoking/cigarette.glb");
    private static final ResourceLocation PACK = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "models/smoking/pack.glb");

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

    @Nullable
    private static SmokingClip clip;
    private static ModelCache.Handle<GemRenderGltfModel> cigarette, pack;

    private SmokingClient() {
    }

    private static boolean smoking(@Nullable LocalPlayer p) {
        return p != null && p.isUsingItem() && p.getUseItem().getItem() instanceof CigaretteItem;
    }

    private static float seconds(LocalPlayer p, float partial) {
        return (SmokingTimeline.TICKS - p.getUseItemRemainingTicks() + partial) / 20.0f;
    }

    private static SmokingClip clip() {
        if (clip == null) {
            clip = SmokingClip.load(Minecraft.getInstance().getResourceManager());
        }
        return clip;
    }

    @SubscribeEvent
    static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!smoking(player) || mc.getCameraEntity() != player) {
            return;
        }
        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        PlayerArms arms = PlayerArms.get();
        if (arms == null) {
            return;
        }
        SmokingClip clip = clip();
        float t = seconds(player, event.getPartialTick());
        int light = event.getPackedLight();
        VIEW.set(event.getPoseStack().last().pose());
        Quaternionf hand = HAND;
        for (int side = 0; side < 2; side++) {
            clip.sample(side == 0 ? clip.right : clip.left, t, WRIST, hand);
            ArmViewPose.shoulder(side == 1, SHOULDER);
            float twist = ArmViewPose.solve(WRIST, hand, SHOULDER,
                    side == 0 ? ArmViewPose.RIGHT_POLE : ArmViewPose.LEFT_POLE, arms.lengths, UPPER, LOWER, ELBOW);
            arms.pose(side, UPPER, LOWER, twist, ArmViewPose.swing(hand, LOWER), ArmLimits.DEFAULT.twist());
        }
        arms.submit(VIEW, light, 0xFFFFFFFF, player);
        if (pack == null) {
            pack = GemRenderModels.handle(PACK);
            cigarette = GemRenderModels.handle(CIGARETTE);
        }
        prop(pack.get(), clip, clip.pack, t, light);
        prop(cigarette.get(), clip, clip.cig, t, light);
    }

    private static void prop(@Nullable GemRenderGltfModel model, SmokingClip clip, float[][] track, float t,
                             int light) {
        if (model == null) {
            return;
        }
        clip.sample(track, t, AT, HAND);
        POSE.set(VIEW).translate(AT).rotate(HAND);
        DirectRenderer.submit(model, null, 0, POSE, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF, DirectPass.HAND,
                VariantUv.NONE, slot -> null);
    }

    @SubscribeEvent
    static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (!smoking(mc.player) || !mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        clip().sampleCamera(seconds(mc.player, (float) event.getPartialTick()), CAMERA);
        event.setYaw(event.getYaw() + CAMERA[0]);
        event.setPitch(event.getPitch() + CAMERA[1]);
        event.setRoll(event.getRoll() + CAMERA[2]);
    }
}
