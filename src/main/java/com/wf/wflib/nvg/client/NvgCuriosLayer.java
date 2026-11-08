package com.wf.wflib.nvg.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wf.wflib.nvg.NvgCurios;
import com.wf.wflib.nvg.api.NvgItem;
import com.wf.wflib.nvg.api.NvgWorn;
import com.wf.wflib.nvg.api.client.NvgItemClient;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Goggles worn in the Curios head slot: the harness band and the goggle on it (the head armour layer draws the others).
 * Drawn from inside the armour layer ({@code MixinHumanoidArmorLayerNvg}), where the jointed wearer body is live.
 */
public final class NvgCuriosLayer {
    private NvgCuriosLayer() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void draw(PoseStack pose, int light, AbstractClientPlayer player, HumanoidModel parent) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (head.getItem() instanceof NvgItem || !NvgWorn.mounted(head).isEmpty()) return;
        ItemStack nvg = NvgCurios.stack(player);
        if (nvg.isEmpty()) return;
        HumanoidModel m = (HumanoidModel) NvgItemClient.INSTANCE.getHumanoidArmorModel(player, nvg, EquipmentSlot.HEAD, null);
        parent.copyPropertiesTo(m);
        com.wf.wflib.core.client.WearerScope.begin(parent);
        try {
            m.renderToBuffer(pose, null, light, OverlayTexture.NO_OVERLAY, -1);
        } finally {
            com.wf.wflib.core.client.WearerScope.end();
        }
    }
}
