package com.wf.wflib.contract.item.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.direct.GemRenderItemRenderer;
import com.wf.gemrender.direct.ItemAppearance;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.wflib.contract.item.PmcItems;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * The contract items drawn from their Blender models ({@code models/item/<name>.glb}, source in {@code art/}): real size in metres,
 * so each is scaled to fit the item cell and tilted to show its face. {@code flat} ones lie on their back (a drive, a clipboard, a brick).
 */
public final class PmcItemModels {
    private PmcItemModels() {
    }

    /** Largest side in metres, and whether the model lies flat. */
    private record Shape(double longest, boolean flat) {}

    /** Where an item sits when held: offset, turn (degrees) and the length its longest side is drawn at (metres). */
    public record Pose(float tx, float ty, float tz, float rx, float ry, float rz, float len) {}

    /** In the first-person hand and in the third-person hand, by item name; {@code /pmcpose} edits them live while the poses are tuned. */
    public static final java.util.Map<String, Pose> FIRST = new java.util.concurrent.ConcurrentHashMap<>(), THIRD = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<Item, String> NAMES = new java.util.HashMap<>();

    public static void register(RegisterClientExtensionsEvent event) {
        add(event, PmcItems.DATA_DRIVE.get(), "data_drive", new Shape(0.0781, true));
        add(event, PmcItems.INTEL_DRIVE.get(), "intel_drive", new Shape(0.1508, true));
        add(event, PmcItems.LAPTOP.get(), "laptop", new Shape(0.335, false));
        add(event, PmcItems.NARCOTICS_BRICK.get(), "narcotics_brick", new Shape(0.2002, true));
        add(event, PmcItems.CACHE_MANIFEST.get(), "cache_manifest", new Shape(0.325, true));
    }

    private static void add(RegisterClientExtensionsEvent event, Item item, String name, Shape shape) {
        NAMES.put(item, name);
        ModelCache.Handle<GemRenderGltfModel> model = GemRenderModels.handle(
                ResourceLocation.fromNamespaceAndPath(com.wf.wflib.core.api.WFLib.MODID, "models/item/" + name + ".glb"));
        GemRenderItemRenderer renderer = new GemRenderItemRenderer(new ItemAppearance() {
            @Override
            public GemRenderGltfModel model(ItemStack stack, ItemDisplayContext context) {
                return model.get();
            }

            @Override
            public void transform(ItemStack stack, ItemDisplayContext context, PoseStack pose) {
                PmcItemModels.pose(context, pose, shape, name);
            }
        });
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return renderer;
            }
        }, item);
    }

    /** The model's centre is the cell's centre (see {@link ItemAppearance#transform}); metres become a fraction of the cell. */
    static void pose(ItemDisplayContext context, PoseStack pose, Shape shape, String name) {
        float fit = (float) (0.9 / shape.longest());
        switch (context) {
            case GUI -> {
                pose.scale(fit, fit, fit);
                pose.mulPose(Axis.XP.rotationDegrees(shape.flat() ? 55.0f : 22.0f));
                pose.mulPose(Axis.YP.rotationDegrees(-35.0f));
            }
            case FIXED -> {
                pose.scale(fit, fit, fit);
                if (shape.flat()) pose.mulPose(Axis.XP.rotationDegrees(-90.0f));
                pose.mulPose(Axis.YP.rotationDegrees(180.0f));
            }
            case GROUND -> {
                float s = (float) (0.55 / shape.longest());
                pose.scale(s, s, s);
                pose.translate(0.0f, 0.02f, 0.0f);
            }
            case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND -> apply(pose, FIRST.get(name), shape);
            default -> apply(pose, THIRD.get(name), shape);
        }
    }

    private static void apply(PoseStack pose, Pose p, Shape shape) {
        if (p == null) return;
        float s = (float) (p.len() / shape.longest());
        pose.translate(p.tx(), p.ty(), p.tz());
        pose.scale(s, s, s);
        pose.mulPose(Axis.XP.rotationDegrees(p.rx()));
        pose.mulPose(Axis.YP.rotationDegrees(p.ry()));
        pose.mulPose(Axis.ZP.rotationDegrees(p.rz()));
    }

    static {
        // tuned live with /pmcpose: lower right of the view like any held item, each at about its real size
        FIRST.put("data_drive", new Pose(-0.04f, 0.14f, -0.1f, 62, 20, 0, 0.13f));
        FIRST.put("intel_drive", new Pose(-0.04f, 0.14f, -0.1f, 62, 20, 0, 0.21f));
        FIRST.put("laptop", new Pose(-0.04f, 0.14f, -0.12f, 72, -12, 4, 0.30f));
        FIRST.put("narcotics_brick", new Pose(-0.04f, 0.14f, -0.1f, 62, 20, 0, 0.20f));
        FIRST.put("cache_manifest", new Pose(-0.04f, 0.14f, -0.1f, 62, 20, 0, 0.30f));
        THIRD.put("data_drive", new Pose(0, 0, 0, -60, 180, 0, 0.14f));
        THIRD.put("intel_drive", new Pose(0, 0, 0, -60, 180, 0, 0.22f));
        THIRD.put("laptop", new Pose(0, 0, 0, 0, 180, 0, 0.45f));
        THIRD.put("narcotics_brick", new Pose(0, 0, 0, -60, 180, 0, 0.22f));
        THIRD.put("cache_manifest", new Pose(0, 0, 0, -60, 180, 0, 0.35f));
    }
}
