package com.wf.wflib.corpse.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.wf.wflib.backpack.api.client.BackpackLayer;
import com.wf.wflib.core.api.WFSystems;
import com.wf.wflib.corpse.api.CorpseEntity;
import com.wf.wflib.ragdoll.api.Ragdolls;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.PlayerModelPart;

/** Player model, owner's skin; the ragdoll owns every bone (AHF rig pose), so only yaw is applied here. */
public final class CorpseRenderer extends LivingEntityRenderer<CorpseEntity, PlayerModel<CorpseEntity>> {
    private final PlayerModel<CorpseEntity> wide;
    private final PlayerModel<CorpseEntity> slim;

    public CorpseRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.0F);
        wide = model;
        slim = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), ctx.getModelManager()));
        addLayer(new ItemInHandLayer<>(this, ctx.getItemInHandRenderer()));
        addLayer(new CustomHeadLayer<>(this, ctx.getModelSet(), ctx.getItemInHandRenderer()));
        if (WFSystems.enabled("backpack")) {
            addLayer(new BackpackLayer<>(this, CorpseEntity::backpack, null));
        }
    }

    @Override
    public void render(CorpseEntity corpse, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!Ragdolls.active(corpse)) {
            return;
        }
        model = skin(corpse).model() == PlayerSkin.Model.SLIM ? slim : wide;
        model.setAllVisible(true);
        model.hat.visible = corpse.partShown(PlayerModelPart.HAT);
        model.jacket.visible = corpse.partShown(PlayerModelPart.JACKET);
        model.leftPants.visible = corpse.partShown(PlayerModelPart.LEFT_PANTS_LEG);
        model.rightPants.visible = corpse.partShown(PlayerModelPart.RIGHT_PANTS_LEG);
        model.leftSleeve.visible = corpse.partShown(PlayerModelPart.LEFT_SLEEVE);
        model.rightSleeve.visible = corpse.partShown(PlayerModelPart.RIGHT_SLEEVE);
        super.render(corpse, yaw, pt, pose, buffers, light);
    }

    @Override
    protected void scale(CorpseEntity corpse, PoseStack pose, float pt) {
        pose.scale(0.9375F, 0.9375F, 0.9375F);
    }

    @Override
    protected void setupRotations(CorpseEntity corpse, PoseStack pose, float bob, float bodyYaw, float pt, float scale) {
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
    }

    @Override
    protected boolean shouldShowName(CorpseEntity corpse) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(CorpseEntity corpse) {
        return skin(corpse).texture();
    }

    /** Online owner: tab list skin; else the profile captured at death. */
    private static PlayerSkin skin(CorpseEntity corpse) {
        Minecraft mc = Minecraft.getInstance();
        PlayerInfo info = corpse.owner().isEmpty() || mc.getConnection() == null ? null
                : mc.getConnection().getPlayerInfo(corpse.owner().get());
        return info != null ? info.getSkin() : mc.getSkinManager().getInsecureSkin(corpse.profile());
    }
}
