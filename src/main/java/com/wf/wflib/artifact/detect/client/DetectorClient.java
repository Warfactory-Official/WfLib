package com.wf.wflib.artifact.detect.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.direct.GemRenderItemRenderer;
import com.wf.gemrender.direct.ItemAppearance;
import com.wf.gemrender.direct.TextureSlots;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.light.Lights;
import com.wf.wflib.WFLib;
import com.wf.wflib.WFSounds;
import com.wf.wflib.artifact.ArtifactItem;
import com.wf.wflib.artifact.detect.ArtifactEntities;
import com.wf.wflib.artifact.detect.Detection;
import com.wf.wflib.artifact.detect.DetectorItem;
import com.wf.wflib.artifact.detect.DetectorTier;
import com.wf.wflib.artifact.detect.ZoneArtifact;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The detector in the hand, client side: it listens for the artefacts its rank finds, beeps at STALKER's rate for the
 * nearest (and blinks its lights with each beep), and lets the local player see the artefacts it reveals. Its face
 * (LED, direction, screen) is drawn on the HUD ({@link DetectorHud}) until a viewmodel draws it on the device.
 */
@EventBusSubscriber(modid = WFLib.MODID, value = Dist.CLIENT)
public final class DetectorClient {
    private DetectorClient() {
    }

    /** The detector held now, or null. */
    static DetectorTier held;
    /** 1 on a beep, fading: the lights and the HUD LED. */
    static float flash;
    /** Artefacts heard, nearest first, and their positions. */
    static final List<Vec3> heard = new ArrayList<>();
    private static double untilBeep;
    /** When a viewmodel last drew the detector's face on the device itself (the HUD stand-in then hides). */
    private static long deviceFaceNanos;

    // --- for viewmodels (MineAssault draws the detector and bolts in hand) ------------------------------------------

    /** The detector out, or null. */
    public static DetectorTier held() {
        return held;
    }

    /** 1 on a beep, fading over a few ticks. */
    public static float flash() {
        return flash;
    }

    /** Where the artefacts the detector hears are, nearest first. */
    public static List<Vec3> heard() {
        return heard;
    }

    /** A viewmodel drew the face on the device this frame: the HUD stand-in steps aside. */
    public static void faceDrawnOnDevice() {
        deviceFaceNanos = System.nanoTime();
    }

    static boolean faceOnDevice() {
        return System.nanoTime() - deviceFaceNanos < 250_000_000L;
    }

    public static GemRenderGltfModel model(DetectorTier tier) {
        return MODELS.computeIfAbsent(tier.model(), GemRenderModels::handle).get();
    }

    /** Body textured, its lights shown (emissive) when {@code lit}. */
    public static TextureSlots slots(DetectorTier tier, boolean lit) {
        return new TextureSlots() {
            @Override
            public ResourceLocation texture(String slot) {
                return "lights".equals(slot) ? (lit ? LIGHTS : null) : tier.texture();
            }

            @Override
            public boolean emissive(String slot) {
                return "lights".equals(slot);
            }
        };
    }

    public static final ResourceLocation BOLT_MODEL = ResourceLocation.fromNamespaceAndPath(WFLib.MODID,
            "models/detector/bolt.glb");
    private static final ResourceLocation BOLT_TEXTURE = ResourceLocation.fromNamespaceAndPath(WFLib.MODID,
            "textures/detector/bolt.png");

    public static GemRenderGltfModel boltModel() {
        return MODELS.computeIfAbsent(BOLT_MODEL, GemRenderModels::handle).get();
    }

    public static TextureSlots boltSlots() {
        return slot -> BOLT_TEXTURE;
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ZoneArtifact.shownHere = a -> {
            LocalPlayer p = Minecraft.getInstance().player;
            return p != null && Detection.revealed(p, a);
        };
        event.enqueueWork(DetectorClient::registerLights);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ArtifactEntities.ZONE_ARTIFACT.get(), ZoneArtifactRenderer::new);
        event.registerEntityRenderer(ArtifactEntities.BOLT.get(), ThrownItemRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "detector"),
                new DetectorHud());
    }

    @SubscribeEvent
    public static void onClientExtensions(RegisterClientExtensionsEvent event) {
        for (var holder : List.of(ArtifactEntities.DETECTOR_ECHO, ArtifactEntities.DETECTOR_BEAR,
                ArtifactEntities.DETECTOR_VELES)) {
            DetectorTier tier = ((DetectorItem) holder.get()).tier();
            GemRenderItemRenderer renderer = new GemRenderItemRenderer(appearance(tier));
            event.registerItem(new IClientItemExtensions() {
                @Override
                public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    return renderer;
                }
            }, holder.get());
        }
        GemRenderItemRenderer bolt = new GemRenderItemRenderer(new ItemAppearance() {
            @Override
            public GemRenderGltfModel model(ItemStack stack, ItemDisplayContext context) {
                return boltModel();
            }

            @Override
            public void transform(ItemStack stack, ItemDisplayContext context, PoseStack pose) {
                float s = context == ItemDisplayContext.GUI ? 9.0F : 5.0F;
                pose.scale(s, s, s);
                pose.mulPose(Axis.ZP.rotationDegrees(45.0F));
            }

            @Override
            public TextureSlots slots(ItemStack stack, ItemDisplayContext context) {
                return boltSlots();
            }
        });
        event.registerItem(new IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return bolt;
            }
        }, ArtifactEntities.BOLT_ITEM.get());
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            held = null;
            heard.clear();
            return;
        }
        DetectorTier now = Detection.held(player);
        if (now != held) {
            if (now != null || held != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(now != null ? WFSounds.DETECTOR_DRAW.get()
                        : WFSounds.DETECTOR_HOLSTER.get(), 1.0F, 0.6F));
            }
            held = now;
            untilBeep = 0.5D;
        }
        flash *= 0.55F;
        heard.clear();
        if (held == null) {
            return;
        }
        List<ZoneArtifact> found = Detection.heard(player, held);
        found.forEach(a -> heard.add(a.position()));
        if (found.isEmpty()) {
            return;
        }
        untilBeep -= 0.05D;
        if (untilBeep <= 0.0D) {
            double d = found.get(0).distanceTo(player);
            untilBeep = held.beepInterval(d);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(held == DetectorTier.ECHO
                    ? WFSounds.DETECTOR_ECHO_BEEP.get() : WFSounds.DETECTOR_BEAR_BEEP.get(), 1.0F, 0.5F));
            flash = 1.0F;
        }
    }

    // --- the detector item ------------------------------------------------------------------------------------------

    private static final Map<ResourceLocation, ModelCache.Handle<GemRenderGltfModel>> MODELS = new HashMap<>();
    private static final ResourceLocation LIGHTS = ResourceLocation.fromNamespaceAndPath(WFLib.MODID,
            "textures/detector/lights.png");
    /** Model metres to the item's unit cube, by context. */
    private static final Map<ItemDisplayContext, Float> SCALE = new EnumMap<>(ItemDisplayContext.class);

    static {
        SCALE.put(ItemDisplayContext.GUI, 5.5F);
        SCALE.put(ItemDisplayContext.FIXED, 5.0F);
        SCALE.put(ItemDisplayContext.GROUND, 3.0F);
        // shortcut: a plain held pose until MineAssault's arms hold it (left hand, face up toward the eye).
        SCALE.put(ItemDisplayContext.FIRST_PERSON_LEFT_HAND, 1.6F);
        SCALE.put(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, 1.6F);
    }

    private static ItemAppearance appearance(DetectorTier tier) {
        return new ItemAppearance() {
            @Override
            public GemRenderGltfModel model(ItemStack stack, ItemDisplayContext context) {
                return DetectorClient.model(tier);
            }

            @Override
            public void transform(ItemStack stack, ItemDisplayContext context, PoseStack pose) {
                float s = SCALE.getOrDefault(context, 2.6F);
                pose.scale(s, s, s);
                if (context.firstPerson()) {
                    pose.translate(0.0F, 0.15F, 0.0F);
                    pose.mulPose(Axis.XP.rotationDegrees(60.0F));
                }
                if (context == ItemDisplayContext.GUI) {
                    // Face up toward the viewer, long side across.
                    pose.mulPose(Axis.XP.rotationDegrees(70.0F));
                    pose.mulPose(Axis.ZP.rotationDegrees(90.0F));
                }
            }

            @Override
            public TextureSlots slots(ItemStack stack, ItemDisplayContext context) {
                boolean mine = held == tier && Minecraft.getInstance().player != null
                        && (Minecraft.getInstance().player.getMainHandItem() == stack
                        || Minecraft.getInstance().player.getOffhandItem() == stack);
                // Its lights lit with each beep, while it is the one out.
                return DetectorClient.slots(tier, mine && flash > 0.3F);
            }
        };
    }

    /** A revealed artefact glows as it does held: its light at it, for the player whose detector shows it. */
    private static void registerLights() {
        Lights.register((sink, partialTick) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null || held == null) {
                return;
            }
            for (ZoneArtifact a : mc.level.getEntitiesOfClass(ZoneArtifact.class, mc.player.getBoundingBox()
                    .inflate(held.reveal + 1.0D), ZoneArtifact.shownHere)) {
                if (a.item().getItem() instanceof ArtifactItem item) {
                    var art = item.artifact();
                    Vec3 p = a.getPosition(partialTick).add(0.0D, ZoneArtifactRenderer.LIFT, 0.0D);
                    sink.point(p.x, p.y, p.z, art.lightRange(), art.lightRgb(),
                            art.lightRange() * art.lightRange() * 0.35F, false);
                }
            }
        });
    }
}
