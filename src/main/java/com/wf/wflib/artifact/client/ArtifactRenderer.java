package com.wf.wflib.artifact.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.direct.DirectPass;
import com.wf.gemrender.direct.DirectRenderer;
import com.wf.gemrender.direct.GemRenderItemRenderer;
import com.wf.gemrender.direct.ItemAppearance;
import com.wf.gemrender.direct.TextureSlots;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.light.Lights;
import com.wf.gemrender.texture.VariantUv;
import com.wf.wflib.WFLib;
import com.wf.wflib.artifact.Artifact;
import com.wf.wflib.artifact.ArtifactItem;
import com.wf.wflib.client.view.ArmViewPose;
import com.wf.wflib.client.view.PlayerArms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Artefacts as seen: held, the right hand is held out palm up and the artefact floats a hand's breadth over it,
 * bobbing and turning (WFLib's first-person arms, as MineAssault's viewmodels use them); in the inventory, on the
 * ground and in others' hands, its GemRender model, floating over the hand. Its lightning layer is unlit and
 * flickers; held, it lights its surroundings faintly ({@code trail_light}).
 */
@EventBusSubscriber(modid = WFLib.MODID, value = Dist.CLIENT)
public final class ArtifactRenderer {
    private static final ResourceLocation LIGHTNING =
            ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "textures/artifact/battery_lightning.png");
    /** View space, m: where the fist is held out (right of centre, low, at arm's length). */
    private static final Vector3f HAND = new Vector3f(0.13F, -0.17F, -0.45F);
    /** The idle left hand, out of view below. */
    private static final Vector3f OFF_HAND = new Vector3f(-0.30F, -0.85F, -0.05F);
    /** Rad about the forearm: palm up. */
    private static final float PALM_UP = (float) Math.toRadians(Float.parseFloat(
            System.getProperty("wflib.artifact.palmRoll", "-90")));
    /** Artefact centre over the fist, m, and its bob. */
    private static final Vector3f FLOAT = new Vector3f(0.0F, 0.085F, -0.02F);
    private static final float BOB = 0.012F;
    /** First-person size over the model's metres. */
    private static final float VIEW_SCALE = 0.4F;
    /** Inventory: longest side of the model, m. */
    private static final float BODY_SIZE = 0.3F;

    private static final Map<ResourceLocation, ModelCache.Handle<GemRenderGltfModel>> MODELS = new HashMap<>();
    private static final Map<Artifact, GemRenderItemRenderer> ITEM_RENDERERS = new HashMap<>();
    private static final TextureSlots SLOTS = new TextureSlots() {
        @Override
        public ResourceLocation texture(String slot) {
            // Arcs come and go: hidden about a third of the time, in 50 ms steps.
            long step = System.nanoTime() / 50_000_000L;
            return (step * 0x9E3779B97F4A7C15L >>> 61) < 5 ? LIGHTNING : null;
        }

        @Override
        public boolean emissive(String slot) {
            return true;
        }
    };

    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f POSE = new Matrix4f();
    private static final Matrix4f UPPER = new Matrix4f();
    private static final Matrix4f LOWER = new Matrix4f();
    private static final Vector3f WRIST = new Vector3f();
    private static final Vector3f SHOULDER = new Vector3f();
    private static final Vector3f ELBOW = new Vector3f();
    private static final Quaternionf PALM = new Quaternionf();

    private ArtifactRenderer() {
    }

    @Nullable
    static GemRenderGltfModel model(Artifact artifact) {
        return MODELS.computeIfAbsent(artifact.model(), GemRenderModels::handle).get();
    }

    public static GemRenderItemRenderer itemRenderer(ArtifactItem item) {
        return ITEM_RENDERERS.computeIfAbsent(item.artifact(), artifact -> new GemRenderItemRenderer(
                new ItemAppearance() {
                    @Override
                    public GemRenderGltfModel model(ItemStack stack, ItemDisplayContext context) {
                        return ArtifactRenderer.model(artifact);
                    }

                    @Override
                    public void transform(ItemStack stack, ItemDisplayContext context, PoseStack pose) {
                        float t = clock();
                        switch (context) {
                            case GUI, FIXED -> {
                                pose.scale(0.9F / BODY_SIZE, 0.9F / BODY_SIZE, 0.9F / BODY_SIZE);
                                pose.mulPose(Axis.XP.rotationDegrees(15));
                            }
                            case GROUND -> pose.mulPose(Axis.YP.rotation(t * 0.8F));
                            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> {
                                // Floating over the hand, as held in first person.
                                pose.translate(0.0F, 0.22F + BOB * Mth.sin(t * 2.1F), 0.0F);
                                pose.mulPose(Axis.YP.rotation(t * 0.8F));
                                pose.scale(VIEW_SCALE, VIEW_SCALE, VIEW_SCALE);
                            }
                            default -> {
                            }
                        }
                    }

                    @Override
                    public TextureSlots slots(ItemStack stack, ItemDisplayContext context) {
                        return SLOTS;
                    }
                }));
    }

    private static float clock() {
        return (System.nanoTime() % 3_600_000_000_000L) / 1.0E9F;
    }

    @Nullable
    private static ArtifactItem held(Player player) {
        return player.getMainHandItem().getItem() instanceof ArtifactItem item ? item : null;
    }

    @SubscribeEvent
    static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ArtifactItem item = player == null ? null : held(player);
        if (item == null) {
            return;
        }
        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND || mc.getCameraEntity() != player) {
            return;
        }
        GemRenderGltfModel model = model(item.artifact());
        float t = clock();
        VIEW.set(event.getPoseStack().last().pose());
        int light = event.getPackedLight();
        if (model != null) {
            POSE.set(VIEW).translate(HAND).translate(FLOAT.x, FLOAT.y + BOB * Mth.sin(t * 2.1F), FLOAT.z)
                    .rotateY(t * 0.8F).rotateX(0.25F * Mth.sin(t * 0.6F)).scale(VIEW_SCALE);
            DirectRenderer.submit(model, null, 0, POSE, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF,
                    DirectPass.HAND, VariantUv.NONE, SLOTS);
        }
        PlayerArms arms = PlayerArms.get();
        if (arms == null) {
            return;
        }
        for (int side = 0; side < 2; side++) {
            WRIST.set(side == 0 ? HAND : OFF_HAND);
            ArmViewPose.shoulder(side == 1, SHOULDER);
            Quaternionf hand = null;
            if (side == 0) {
                // Forearm toward the fist, then rolled about it so the palm faces up under the artefact.
                hand = ArmViewPose.towardWrist(WRIST, SHOULDER, PALM).rotateY(PALM_UP);
            }
            float twist = ArmViewPose.solve(WRIST, hand, SHOULDER,
                    side == 0 ? ArmViewPose.RIGHT_POLE : ArmViewPose.LEFT_POLE, arms.lengths, UPPER, LOWER, ELBOW);
            arms.pose(side, UPPER, LOWER, twist, ArmViewPose.swing(hand, LOWER),
                    com.wf.wflib.anim.ArmLimits.DEFAULT.twist());
        }
        arms.submit(VIEW, light, 0xFFFFFFFF, player);
    }

    /** Client setup: a held artefact's faint glow. */
    public static void register() {
        Lights.register((sink, partialTick) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                return;
            }
            Vec3 eye = mc.player.getEyePosition(partialTick);
            for (Player player : mc.level.players()) {
                ArtifactItem item = held(player);
                if (item == null || player.getEyePosition(partialTick).distanceToSqr(eye) > 64.0D * 64.0D) {
                    continue;
                }
                // About where it floats: in front of the chest, a little right.
                Vec3 look = player.getViewVector(partialTick);
                Vec3 at = player.getEyePosition(partialTick).add(look.scale(0.5D)).add(0.0D, -0.25D, 0.0D);
                Artifact a = item.artifact();
                sink.point(at.x, at.y, at.z, a.lightRange(), a.lightRgb(), a.lightRange() * a.lightRange() * 0.5F,
                        false);
            }
        });
    }
}
