package com.wf.wflib.mixin.nvg;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wf.wflib.core.api.WFSystems;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Goggles in the Curios head slot. Drawn at the end of the armour layer: that scope is where the jointed wearer body poses worn models. */
@Mixin(value = HumanoidArmorLayer.class, priority = 500)
public abstract class MixinHumanoidArmorLayerNvg {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("RETURN"))
    private void wflib$draw(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity, float a, float b,
                            float c, float d, float e, float f, CallbackInfo ci) {
        if (entity instanceof AbstractClientPlayer player
                && ((RenderLayer<?, ?>) (Object) this).getParentModel() instanceof HumanoidModel<?> parent && WFSystems.enabled("nvg")) {
            com.wf.wflib.nvg.client.NvgCuriosLayer.draw(pose, light, player, parent);
        }
    }
}
