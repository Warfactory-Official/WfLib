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
    /** The idle left hand, out of view below. */
    private static final Vector3f OFF_HAND = new Vector3f(-0.30F, -0.85F, -0.05F);
    /** Inventory: longest side of the model, m. */
    private static final float BODY_SIZE = 0.3F;

    private static final Map<ResourceLocation, ModelCache.Handle<GemRenderGltfModel>> MODELS = new HashMap<>();
    private static final Map<Artifact, net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer> ITEM_RENDERERS =
            new HashMap<>();
    /** Bloom round an artefact, white (tinted its light colour), alpha falling off from the middle. */
    private static final ResourceLocation HALO = ResourceLocation.fromNamespaceAndPath(WFLib.MODID,
            "textures/artifact/halo.png");
    private static final TextureSlots HALO_ONLY = new TextureSlots() {
        @Override
        public ResourceLocation texture(String slot) {
            return HALO;
        }

        @Override
        public boolean emissive(String slot) {
            return true;
        }
    };
    /** Bloom across, over the model's size (a 0.3 m artefact gets a ~0.75 m halo). */
    private static final float HALO_SIZE = 0.75F;
    /**
     * Bloom set back from the centre, away from the eye, model metres (about the artefact's radius): behind it, so
     * the body and its core stay in front and the glow spills round the outline.
     */
    private static final float HALO_BACK = 0.16F;
    /** One quad (XY, facing +Z), glow slot: an artefact's core sprite. */
    private static final ResourceLocation CORE = ResourceLocation.fromNamespaceAndPath(WFLib.MODID,
            "models/artifact/core.glb");
    private static final String GLOW = "glow";

    /** Inventory, ground, others' hands: body and glow as they are. */
    private static TextureSlots slots(Artifact artifact) {
        return new TextureSlots() {
            @Override
            public ResourceLocation texture(String slot) {
                return GLOW.equals(slot) ? artifact.glow() : artifact.body();
            }

            @Override
            public boolean emissive(String slot) {
                return GLOW.equals(slot);
            }
        };
    }

    /** First person, drawn apart so the glow can move: the body alone, then the glow alone. */
    private static TextureSlots bodyOnly(Artifact artifact) {
        return slot -> GLOW.equals(slot) ? null : artifact.body();
    }

    private static TextureSlots glowOnly(Artifact artifact) {
        return new TextureSlots() {
            @Override
            public ResourceLocation texture(String slot) {
                return GLOW.equals(slot) ? artifact.glow() : null;
            }

            @Override
            public boolean emissive(String slot) {
                return true;
            }
        };
    }

    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f POSE = new Matrix4f();
    private static final Matrix4f CORE_POSE = new Matrix4f();
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

    public static net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer itemRenderer(ArtifactItem item) {
        return ITEM_RENDERERS.computeIfAbsent(item.artifact(), artifact -> new CoreItemRenderer(artifact,
                new GemRenderItemRenderer(new ItemAppearance() {
                    @Override
                    public GemRenderGltfModel model(ItemStack stack, ItemDisplayContext context) {
                        return ArtifactRenderer.model(artifact);
                    }

                    @Override
                    public void transform(ItemStack stack, ItemDisplayContext context, PoseStack pose) {
                        ArtifactRenderer.transform(context, pose);
                    }

                    @Override
                    public TextureSlots slots(ItemStack stack, ItemDisplayContext context) {
                        return ArtifactRenderer.slots(artifact);
                    }
                })));
    }

    /** The item display transform, as the item renderer applies it, for the core sprite to follow. */
    private static void transform(ItemDisplayContext context, PoseStack pose) {
        float t = clock();
        switch (context) {
            case GUI, FIXED -> {
                pose.scale(0.9F / BODY_SIZE, 0.9F / BODY_SIZE, 0.9F / BODY_SIZE);
                pose.mulPose(Axis.XP.rotationDegrees(15));
            }
            case GROUND -> pose.mulPose(Axis.YP.rotation(t * 0.8F));
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> {
                pose.translate(0.0F, 0.22F + HeldArtifactPose.BOB * Mth.sin(t * 2.1F), 0.0F);
                pose.mulPose(Axis.YP.rotation(t * 0.8F));
                pose.scale(HeldArtifactPose.SCALE, HeldArtifactPose.SCALE, HeldArtifactPose.SCALE);
            }
            default -> {
            }
        }
    }

    /**
     * The GemRender item renderer, plus the core sprite where an artefact has one: at the model's centre, turned to
     * whoever looks (the camera in the level, the screen in the inventory), spinning in its plane.
     */
    private static final class CoreItemRenderer extends net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer {
        private final Artifact artifact;
        private final GemRenderItemRenderer body;
        private final Matrix4f core = new Matrix4f();

        CoreItemRenderer(Artifact artifact, GemRenderItemRenderer body) {
            super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
            this.artifact = artifact;
            this.body = body;
        }

        @Override
        public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                                 net.minecraft.client.renderer.MultiBufferSource buffers, int light, int overlay) {
            body.renderByItem(stack, context, pose, buffers, light, overlay);
            GemRenderGltfModel quad = coreModel();
            if (quad == null) {
                return;
            }
            pose.pushPose();
            pose.translate(0.5F, 0.5F, 0.5F);
            transform(context, pose);
            Matrix4f m = pose.last().pose();
            float unit = m.getColumn(0, new Vector3f()).length();
            DirectPass pass = context == ItemDisplayContext.GUI ? DirectPass.GUI : DirectPass.LEVEL;
            float t = clock();
            // Blended layers write no depth: back to front, the bloom (behind) before the core. The inventory icon
            // stays crisp: no bloom in a 16 px cell.
            if (context != ItemDisplayContext.GUI) {
                face(m, context, core);
                // Turned to the camera, the quad's +z points at it: back is -z.
                core.translate(0.0F, 0.0F, -unit * HALO_BACK).scale(unit * HALO_SIZE);
                DirectRenderer.submit(quad, null, 0, core, light, overlay, haloTint(t, artifact, 1.0F), pass,
                        VariantUv.NONE, HALO_ONLY);
            }
            if (artifact.core() > 0.0F) {
                face(m, context, core);
                core.rotateZ(t * 0.5F).scale(unit * artifact.core());
                DirectRenderer.submit(quad, null, 0, core, light, overlay, glowTint(t, artifact), pass,
                        VariantUv.NONE, glowOnly(artifact));
            }
            pose.popPose();
        }
    }

    /** {@code out}: at {@code m}'s origin, turned to whoever looks (the camera; the screen in the inventory). */
    private static void face(Matrix4f m, ItemDisplayContext context, Matrix4f out) {
        out.translation(m.getTranslation(new Vector3f()));
        if (context != ItemDisplayContext.GUI) {
            out.rotate(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        }
    }

    /**
     * The bloom: its light colour at full brightness (blended, a dimmer colour would darken what is behind), its
     * strength in the alpha (the shader multiplies it in): breathing with its glow, fading with the lift.
     */
    private static int haloTint(float t, Artifact artifact, float lift) {
        float k = ((glowTint(t, artifact) & 0xFF) / 255.0F) * Mth.clamp(lift, 0.0F, 1.0F) * artifact.bloom();
        return (int) (Mth.clamp(k, 0.0F, 1.0F) * 255.0F) << 24 | artifact.lightRgb() & 0xFFFFFF;
    }

    @Nullable
    private static GemRenderGltfModel coreModel() {
        return MODELS.computeIfAbsent(CORE, GemRenderModels::handle).get();
    }

    /**
     * The arcs alive and always there: crawling along their shells (the texture slides up) and writhing sideways a
     * little (two fast, unrelated sways; far enough to look like a new arc, never off it: the strip is mostly empty).
     */
    private static VariantUv arcs(float t) {
        float writhe = 0.035F * Mth.sin(t * 23.0F) + 0.025F * Mth.sin(t * 37.0F + 2.0F);
        return new VariantUv(writhe, -t * 1.3F % 1.0F);
    }

    /** Arcs surge and dip, a slow swell under a fast crackle; any other glow only breathes. Never out. */
    private static int glowTint(float t, Artifact artifact) {
        float k = artifact.arcs()
                ? 0.7F + 0.15F * Mth.sin(t * 3.1F) + 0.15F * Mth.sin(t * 17.3F + 1.3F * Mth.sin(t * 5.0F))
                : 0.8F + 0.2F * Mth.sin(t * 1.4F);
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
            Artifact artifact = item.artifact();
            DirectRenderer.submit(model, null, 0, POSE, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF,
                    DirectPass.HAND, VariantUv.NONE, bodyOnly(artifact));
            // Blended layers write no depth: drawn back to front, the bloom (behind) before the glow.
            GemRenderGltfModel halo = coreModel();
            if (halo != null) {
                // View space: the eye looks down -z, so back is -z.
                CORE_POSE.set(VIEW).translate(pose.artifact).translate(0.0F, 0.0F, -HeldArtifactPose.SCALE * HALO_BACK)
                        .scale(HeldArtifactPose.SCALE * HALO_SIZE);
                DirectRenderer.submit(halo, null, 0, CORE_POSE, light, OverlayTexture.NO_OVERLAY,
                        haloTint(t, artifact, shownLift), DirectPass.HAND, VariantUv.NONE, HALO_ONLY);
            }
            GemRenderGltfModel quad = artifact.core() > 0.0F ? coreModel() : null;
            if (quad != null) {
                // View space: unrotated, the quad already faces the eye; it spins in its plane.
                CORE_POSE.set(VIEW).translate(pose.artifact).rotateZ(t * 0.5F)
                        .scale(HeldArtifactPose.SCALE * artifact.core());
                DirectRenderer.submit(quad, null, 0, CORE_POSE, light, OverlayTexture.NO_OVERLAY, glowTint(t, artifact),
                        DirectPass.HAND, VariantUv.NONE, glowOnly(artifact));
            } else {
                DirectRenderer.submit(model, null, 0, POSE, light, OverlayTexture.NO_OVERLAY, glowTint(t, artifact),
                        DirectPass.HAND, artifact.arcs() ? arcs(t) : VariantUv.NONE, glowOnly(artifact));
            }
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

    /** Client setup: a held artefact's faint glow, lit where it floats. */
    public static void register() {
        Lights.register((sink, partialTick) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                return;
            }
            net.minecraft.client.Camera camera = mc.gameRenderer.getMainCamera();
            Vec3 eye = camera.getPosition();
            for (Player player : mc.level.players()) {
                ArtifactItem item = held(player);
                if (item == null || player.getEyePosition(partialTick).distanceToSqr(eye) > 64.0D * 64.0D) {
                    continue;
                }
                Vector3f at = new Vector3f();
                float strength;
                if (player == mc.player && mc.options.getCameraType().isFirstPerson() && shown != null) {
                    // Ours, in first person: the pose just drawn, view space -> world through the camera.
                    camera.rotation().transform(at.set(POSE_NOW.artifact));
                    at.add((float) eye.x, (float) eye.y, (float) eye.z);
                    strength = shownLift;
                } else {
                    // Anyone else (or us seen from outside): the held pose, from their eyes and view.
                    LIGHT_POSE.pose(1.0F, 0.0F, LIGHT_LENGTHS);
                    new Quaternionf().rotationYXZ((float) Math.PI - player.getViewYRot(partialTick) * Mth.DEG_TO_RAD,
                            -player.getViewXRot(partialTick) * Mth.DEG_TO_RAD, 0.0F).transform(at.set(LIGHT_POSE.artifact));
                    Vec3 from = player.getEyePosition(partialTick);
                    at.add((float) from.x, (float) from.y, (float) from.z);
                    strength = 1.0F;
                }
                // Just behind it from where we look: at its centre it floodlights its own body (a cage glows
                // green from inside); a little past it, what we see of it is backlit and the light still spills
                // round it on the ground and the hand.
                Vector3f away = new Vector3f(at).sub((float) eye.x, (float) eye.y, (float) eye.z);
                if (away.lengthSquared() > 1.0E-6F) {
                    at.add(away.normalize().mul(LIGHT_BEHIND));
                }
                if (player == mc.player) {
                    LAST_LIGHT.set(at);
                }
                Artifact a = item.artifact();
                if (strength > 0.0F) {
                    sink.point(at.x, at.y, at.z, a.lightRange(), a.lightRgb(),
                            a.lightRange() * a.lightRange() * 0.5F * strength, false);
                }
            }
        });
    }

    private static final HeldArtifactPose LIGHT_POSE = new HeldArtifactPose();
    /** How far behind the artefact (from the camera) its light sits, blocks. */
    private static final float LIGHT_BEHIND = 0.35F;
    /** Where our held artefact's light last was, world (the hold bench checks it). */
    public static final Vector3f LAST_LIGHT = new Vector3f();
    /** Arm lengths for others' lights (player_arms.glb, as the pose tests use). */
    private static final PlayerArms.Lengths LIGHT_LENGTHS = new PlayerArms.Lengths(0.26F, 0.33F);
}
