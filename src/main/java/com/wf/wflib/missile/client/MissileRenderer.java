package com.wf.wflib.missile.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import com.wf.wflib.round.api.client.RoundRenderer;
import com.wf.wflib.round.api.client.RoundRenderers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import com.wf.wflib.missile.api.MissileEntity;
import com.wf.wflib.core.api.WFLib;

public class MissileRenderer extends EntityRenderer<MissileEntity> {

    public MissileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(MissileEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "textures/entity/missile.png");
    }

    /** {@link MissileEntity#getLook} with a {@link RoundRenderer}: drawn here, not by {@code MissileVisual}. */
    @Override
    public void render(MissileEntity entity, float entityYaw, float partialTicks,
                       com.mojang.blaze3d.vertex.PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource buffer, int packedLight) {
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        ResourceLocation look = entity.getLook();
        RoundRenderer renderer = look == null ? null : RoundRenderers.get(look);
        if (renderer == null || !renderer.hasLook()) {
            return;
        }
        Vec3 travel = entity.position().subtract(entity.xo, entity.yo, entity.zo);
        Vec3 nose = travel.lengthSqr() > 1.0e-8 ? travel.normalize() : entity.getViewVector(partialTicks);
        renderer.render(poseStack, buffer, packedLight, nose, false);
    }
}
