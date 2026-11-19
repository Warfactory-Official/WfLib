package com.wf.wflib.photo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.direct.GemRenderItemRenderer;
import com.wf.gemrender.direct.ItemAppearance;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.photo.PhotoSystem;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * The camera item drawn from {@code models/item/camera.glb} (source: {@code art/camera/}). The model is in metres,
 * lens toward +Z and the screen toward -Z; held, the screen faces the player and the lens points away.
 */
final class CameraRenderer {
    private static final ModelCache.Handle<GemRenderGltfModel> MODEL = GemRenderModels.handle(
            ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "models/item/camera.glb"));

    private CameraRenderer() {
    }

    static void register(RegisterClientExtensionsEvent event) {
        GemRenderItemRenderer renderer = new GemRenderItemRenderer(new ItemAppearance() {
            @Override
            public GemRenderGltfModel model(ItemStack stack, ItemDisplayContext context) {
                return MODEL.get();
            }

            @Override
            public void transform(ItemStack stack, ItemDisplayContext context, PoseStack pose) {
                pose(context, pose);
            }
        });
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return renderer;
            }
        }, PhotoSystem.CAMERA_ITEM.get());
    }

    /** Model metres to the item's unit cube, then the pose for each place the item is drawn. */
    static void pose(ItemDisplayContext context, PoseStack pose) {
        switch (context) {
            case GUI -> {
                pose.scale(7.0f, 7.0f, 7.0f);
                pose.mulPose(Axis.XP.rotationDegrees(12.0f));
                pose.mulPose(Axis.YP.rotationDegrees(-32.0f));
            }
            case FIXED -> {
                pose.scale(6.0f, 6.0f, 6.0f);
                pose.mulPose(Axis.YP.rotationDegrees(180.0f));
            }
            case GROUND -> {
                pose.scale(4.0f, 4.0f, 4.0f);
                pose.translate(0.0f, 0.04f, 0.0f);
            }
            case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND -> {
                pose.translate(0.0f, -0.02f, 0.0f);
                pose.scale(5.0f, 5.0f, 5.0f);
                pose.mulPose(Axis.YP.rotationDegrees(180.0f));
            }
            default -> { // third person
                pose.scale(4.5f, 4.5f, 4.5f);
                pose.mulPose(Axis.YP.rotationDegrees(180.0f));
            }
        }
    }
}
