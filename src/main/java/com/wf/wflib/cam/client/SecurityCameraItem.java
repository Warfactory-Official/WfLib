package com.wf.wflib.cam.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.direct.GemRenderItemRenderer;
import com.wf.gemrender.direct.ItemAppearance;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.wflib.core.api.WFLib;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/** The security camera item: the assembled Blender model, centred, turned to show its lens. */
public final class SecurityCameraItem {
    private static final ModelCache.Handle<GemRenderGltfModel> MODEL =
            GemRenderModels.handle(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "models/block/security_camera.glb"));
    /** Longest side of the model in metres (the housing's length plus the bracket). */
    private static final float LONGEST = 0.54f;

    private SecurityCameraItem() {
    }

    public static IClientItemExtensions extensions() {
        GemRenderItemRenderer renderer = new GemRenderItemRenderer(new ItemAppearance() {
            @Override
            public GemRenderGltfModel model(ItemStack stack, ItemDisplayContext context) {
                return MODEL.get();
            }

            @Override
            public void transform(ItemStack stack, ItemDisplayContext context, PoseStack pose) {
                SecurityCameraItem.pose(context, pose);
            }
        });
        return new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return renderer;
            }
        };
    }

    static void pose(ItemDisplayContext context, PoseStack pose) {
        switch (context) {
            case GUI -> {
                float s = 0.95f / LONGEST;
                pose.scale(s, s, s);
                pose.mulPose(Axis.XP.rotationDegrees(20.0f));
                pose.mulPose(Axis.YP.rotationDegrees(215.0f));
            }
            case GROUND, FIXED -> {
                float s = 0.5f / LONGEST;
                pose.scale(s, s, s);
                pose.mulPose(Axis.YP.rotationDegrees(180.0f));
            }
            case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND -> {
                float s = 0.3f / LONGEST;
                pose.translate(-0.04f, 0.14f, -0.1f);                 // lower right of the view, lens pointing away
                pose.scale(s, s, s);
                pose.mulPose(Axis.XP.rotationDegrees(8.0f));
                pose.mulPose(Axis.YP.rotationDegrees(-28.0f));
            }
            default -> {
                float s = 0.55f / LONGEST;
                pose.scale(s, s, s);
                pose.mulPose(Axis.YP.rotationDegrees(180.0f));
            }
        }
    }
}
