package com.wf.wflib.artifact.detect.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wf.wflib.artifact.api.BoltEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Quaternionf;

/** A thrown bolt as its own item's sprite (a GregTech bolt flies as GregTech's), turned as the bolt is. */
public class BoltRenderer extends EntityRenderer<BoltEntity> {
    /** Sprite edge, blocks. */
    private static final float SIZE = 0.3F;
    private final ItemRenderer items;
    private final Quaternionf turn = new Quaternionf();

    public BoltRenderer(EntityRendererProvider.Context context) {
        super(context);
        items = context.getItemRenderer();
        shadowRadius = 0.05F;
    }

    @Override
    public void render(BoltEntity bolt, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light) {
        pose.pushPose();
        // Lying: just off the ground; flying: about its middle.
        pose.translate(0.0F, bolt.resting() ? 0.01F : bolt.getBbHeight() * 0.5F, 0.0F);
        pose.mulPose(bolt.orientation(partialTick, turn));
        pose.scale(SIZE, SIZE, SIZE);
        items.renderStatic(bolt.item(), ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                bolt.level(), bolt.getId());
        pose.popPose();
        super.render(bolt, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(BoltEntity bolt) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
