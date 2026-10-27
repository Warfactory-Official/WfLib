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
    private static final ResourceLocation BODY =
            ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "models/artifact/battery_body.png");
    /** The idle left hand, out of view below. */
    private static final Vector3f OFF_HAND = new Vector3f(-0.30F, -0.85F, -0.05F);
    /** Inventory: longest side of the model, m. */
    private static final float BODY_SIZE = 0.3F;

    private static final Map<ResourceLocation, ModelCache.Handle<GemRenderGltfModel>> MODELS = new HashMap<>();
    private static final Map<Artifact, GemRenderItemRenderer> ITEM_RENDERERS = new HashMap<>();
    /** Inventory, ground, others' hands: body and arcs as they are. */
    private static final TextureSlots SLOTS = new TextureSlots() {
        @Override
        public ResourceLocation texture(String slot) {
            return "lightning".equals(slot) ? LIGHTNING : BODY;
        }

        @Override
        public boolean emissive(String slot) {
            return "lightning".equals(slot);
        }
    };
    /** First person, drawn apart so the arcs can move: the body alone, then the arcs alone. */
    private static final TextureSlots BODY_ONLY = slot -> "lightning".equals(slot) ? null : BODY;
    private static final TextureSlots ARCS_ONLY = new TextureSlots() {
        @Override
        public ResourceLocation texture(String slot) {
            return "lightning".equals(slot) ? LIGHTNING : null;
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
    private static final HeldArtifactPose POSE_NOW = new HeldArtifactPose();
    /** Lift and put-away, s: vanilla swaps items in 3 ticks each way, too quick to see a hand do anything. */
    private static final float LIFT_SECONDS = 0.55F;
    private static final float PUT_AWAY_SECONDS = 0.35F;
    /**
     * The artefact in hand and how far up it is (0..1) as of the last frame drawn; when it began to come up or go
     * away (ns); 0 = not leaving. The next item is held back while it goes.
     */
    @Nullable
    private static ArtifactItem shown;
    private static float shownLift;
    private static long liftStart;
    private static long leaveStart;
    private static long lastDrawn;

    /** Test: draw the artefact in hand at exactly this lift (0..1) whatever the clock; negative = live. */
    private static float forcedLift = -1.0F;

    /** How far up the artefact in hand was last drawn, 0..1 (the hold bench checks it). */
    public static float lift() {
        return shownLift;
    }

    /** Hold bench: pose stills at chosen lifts, independent of frame rate; negative to go live again. */
    public static void forceLift(float lift) {
        forcedLift = lift;
    }
    private static final Vector3f SHOULDER = new Vector3f();
    private static final Vector3f ELBOW = new Vector3f();

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
                                pose.translate(0.0F, 0.22F + HeldArtifactPose.BOB * Mth.sin(t * 2.1F), 0.0F);
                                pose.mulPose(Axis.YP.rotation(t * 0.8F));
                                pose.scale(HeldArtifactPose.SCALE, HeldArtifactPose.SCALE, HeldArtifactPose.SCALE);
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

    /**
     * The arcs alive and always there: crawling along their shells (the texture slides up) and writhing sideways a
     * little (two fast, unrelated sways; far enough to look like a new arc, never off it: the strip is mostly empty).
     */
    private static VariantUv arcs(float t) {
        float writhe = 0.035F * Mth.sin(t * 23.0F) + 0.025F * Mth.sin(t * 37.0F + 2.0F);
        return new VariantUv(writhe, -t * 1.3F % 1.0F);
    }

    /** Arcs surge and dip: a slow swell under a fast crackle, never out. */
    private static int arcTint(float t) {
        float k = 0.7F + 0.15F * Mth.sin(t * 3.1F) + 0.15F * Mth.sin(t * 17.3F + 1.3F * Mth.sin(t * 5.0F));
        int c = Mth.clamp((int) (k * 255.0F), 0, 255);
        return 0xFF000000 | c << 16 | c << 8 | c;
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
        if (player == null || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        long now = System.nanoTime();
        ArtifactItem selected = player.getMainHandItem().getItem() instanceof ArtifactItem a ? a : null;
        if (now - lastDrawn > 1_000_000_000L) {
            // Not drawn for a second (third person, hidden hand): whatever is in hand comes up again. Not shorter: a
            // slow frame must not restart a lift.
            shown = null;
            shownLift = 0.0F;
            leaveStart = 0L;
        }
        float lift;
        if (selected != null) {
            if (shown != selected || leaveStart != 0L) {
                // Coming up, from wherever it was (a put-away turned back picks up there).
                liftStart = now - (long) (shownLift * LIFT_SECONDS * 1.0E9F);
                leaveStart = 0L;
                shown = selected;
            }
            lift = Math.min((now - liftStart) / 1.0E9F / LIFT_SECONDS, 1.0F);
        } else if (shown != null) {
            // Switched away: lower it first, holding the next item back meanwhile.
            if (leaveStart == 0L) {
                leaveStart = now - (long) ((1.0F - shownLift) * PUT_AWAY_SECONDS * 1.0E9F);
            }
            lift = 1.0F - (now - leaveStart) / 1.0E9F / PUT_AWAY_SECONDS;
            if (lift <= 0.0F) {
                shown = null;
                leaveStart = 0L;
                return;
            }
        } else {
            return;
        }
        if (forcedLift >= 0.0F && selected != null) {
            lift = forcedLift;
        }
        ArtifactItem item = shown;
        event.setCanceled(true);
        lastDrawn = now;
        shownLift = lift;
        if (mc.getCameraEntity() != player) {
            return;
        }
        PlayerArms arms = PlayerArms.get();
        if (arms == null) {
            return;
        }
        float t = clock();
        HeldArtifactPose pose = POSE_NOW.pose(lift, t, arms.lengths);
        VIEW.set(event.getPoseStack().last().pose());
        int light = event.getPackedLight();
        GemRenderGltfModel model = model(item.artifact());
        if (model != null) {
            POSE.set(VIEW).translate(pose.artifact).rotateY(t * 0.8F).rotateX(0.25F * Mth.sin(t * 0.6F))
                    .scale(HeldArtifactPose.SCALE);
            DirectRenderer.submit(model, null, 0, POSE, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF,
                    DirectPass.HAND, VariantUv.NONE, BODY_ONLY);
            DirectRenderer.submit(model, null, 0, POSE, light, OverlayTexture.NO_OVERLAY, arcTint(t),
                    DirectPass.HAND, arcs(t), ARCS_ONLY);
        }
        for (int side = 0; side < 2; side++) {
            Quaternionf hand = side == 0 ? pose.hand : null;
            WRIST.set(side == 0 ? pose.fist : OFF_HAND);
            ArmViewPose.shoulder(side == 1, SHOULDER);
            float twist = side == 0
                    ? ArmViewPose.solve(WRIST, hand, SHOULDER, ArmViewPose.RIGHT_POLE, arms.lengths, 0,
                            ArmViewPose.LOCKED, null, 0, UPPER, LOWER, ELBOW)
                    : ArmViewPose.solve(WRIST, null, SHOULDER, ArmViewPose.LEFT_POLE, arms.lengths, UPPER, LOWER, ELBOW);
            arms.pose(side, UPPER, LOWER, twist, ArmViewPose.swing(hand, LOWER),
                    side == 0 ? HeldArtifactPose.TWIST_LIMIT : com.wf.wflib.anim.ArmLimits.DEFAULT.twist());
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
