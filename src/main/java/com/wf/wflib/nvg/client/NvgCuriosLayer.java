package com.wf.wflib.nvg.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wf.wflib.nvg.NvgCurios;
import com.wf.wflib.nvg.api.NvgItem;
import com.wf.wflib.nvg.api.NvgWorn;
import com.wf.wflib.nvg.api.client.NvgItemClient;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Goggles worn in the Curios head slot: the harness band and the goggle on it (the head armour layer draws the others). */
public final class NvgCuriosLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private NvgCuriosLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void addLayers(EntityRenderersEvent.AddLayers e) {
        for (PlayerSkin.Model skin : e.getSkins()) {
            if (e.getSkin(skin) instanceof LivingEntityRenderer r) r.addLayer(new NvgCuriosLayer(r));
        }
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float age, float headYaw, float headPitch) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (head.getItem() instanceof NvgItem || !NvgWorn.mounted(head).isEmpty()) return;
        ItemStack nvg = NvgCurios.stack(player);
        if (nvg.isEmpty()) return;
        HumanoidModel m = (HumanoidModel) NvgItemClient.INSTANCE.getHumanoidArmorModel(player, nvg, EquipmentSlot.HEAD, null);
        getParentModel().copyPropertiesTo(m);
        m.renderToBuffer(pose, null, light, OverlayTexture.NO_OVERLAY, -1);
    }
}
