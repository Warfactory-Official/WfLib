package com.wf.wflib.artifact.detect.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wf.wflib.artifact.api.ZoneArtifact;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Quaternionf;

/** An artefact lying in the world, where shown here ({@link ZoneArtifact#shown}): true size, turned as it rolled. */
public class ZoneArtifactRenderer extends EntityRenderer<ZoneArtifact> {
    private final ItemRenderer items;
    private final Quaternionf turn = new Quaternionf();

    public ZoneArtifactRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemRenderer();
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(ZoneArtifact artifact, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light) {
        if (!artifact.shown() || artifact.item().isEmpty()) {
            return;
        }
        pose.pushPose();
        pose.translate(0.0F, artifact.getBbHeight() * 0.5F, 0.0F);
        pose.mulPose(artifact.orientation(partialTick, turn));
        items.renderStatic(artifact.item(), ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                artifact.level(), artifact.getId());
        pose.popPose();
        super.render(artifact, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ZoneArtifact artifact) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
