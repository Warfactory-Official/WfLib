package com.wf.wflib.artifact.detect.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.wf.wflib.artifact.detect.ZoneArtifact;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * A Zone artefact, drawn only for the player whose detector shows it ({@link ZoneArtifact#shownHere}): hanging a
 * little off the ground, turning and bobbing, lit from within.
 */
public class ZoneArtifactRenderer extends EntityRenderer<ZoneArtifact> {
    /** Its middle above the entity's feet, blocks. */
    static final float LIFT = 0.35F;
    private final ItemRenderer items;

    public ZoneArtifactRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemRenderer();
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(ZoneArtifact artifact, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light) {
        if (!ZoneArtifact.shownHere.test(artifact) || artifact.item().isEmpty()) {
            return;
        }
        float t = artifact.tickCount + partialTick;
        pose.pushPose();
        pose.translate(0.0F, LIFT + 0.05F * Mth.sin(t * 0.1F), 0.0F);
        pose.mulPose(Axis.YP.rotation(t * 0.03F));
        items.renderStatic(artifact.item(), ItemDisplayContext.GROUND, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, pose, buffers, artifact.level(), artifact.getId());
        pose.popPose();
        super.render(artifact, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ZoneArtifact artifact) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
