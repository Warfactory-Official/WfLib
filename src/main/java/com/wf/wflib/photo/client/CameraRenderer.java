package com.wf.wflib.photo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.direct.GemRenderItemRenderer;
import com.wf.gemrender.direct.ItemAppearance;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.wflib.core.api.WFLib;
import com.wf.gemrender.direct.TextureSlots;
import com.wf.wflib.photo.PhotoLimits;
import com.wf.wflib.photo.PhotoSystem;
import com.wf.wflib.photo.api.client.CameraScreen;
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

    private static final ResourceLocation STILL = ResourceLocation.fromNamespaceAndPath(WFLib.MODID,
            "textures/item/camera_screen.png");

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

            @Override
            public TextureSlots slots(ItemStack stack, ItemDisplayContext context) {
                // The back screen shows what the lens sees while the camera is in the player's hands, a still frame otherwise.
                boolean held = context.firstPerson();
                if (held) {
                    CameraScreen.request(PhotoLimits.FOV);
                    CameraScreen.readout(1.0f, 4.0f);
                }
                ResourceLocation screen = held && CameraScreen.live() ? CameraScreen.texture() : STILL;
                return new TextureSlots() {
                    @Override
                    public ResourceLocation texture(String slot) {
                        return "screen".equals(slot) ? screen : null;
                    }

                    @Override
                    public boolean emissive(String slot) {
                        return "screen".equals(slot);
                    }
                };
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
                // The hand sits low and to the right of the view: bring it toward the middle, screen toward the eye.
                pose.translate(-0.30f, 0.29f, 0.05f);
                pose.scale(3.4f, 3.4f, 3.4f);
                pose.mulPose(Axis.XP.rotationDegrees(-8.0f));
                pose.mulPose(Axis.YP.rotationDegrees(192.0f));
            }
            default -> { // third person
                pose.scale(4.5f, 4.5f, 4.5f);
                pose.mulPose(Axis.YP.rotationDegrees(180.0f));
            }
        }
    }
}
