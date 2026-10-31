package com.wf.wflib.mixin.carry;

import com.wf.wflib.carry.api.Shoulder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shouldered weapon is the carrier's main-hand item; the inventory slot underneath is untouched. */
@Mixin(Player.class)
public abstract class MixinPlayerShoulder {
    @Inject(method = "getItemBySlot", at = @At("HEAD"), cancellable = true)
    private void wflib$shoulderedGun(EquipmentSlot slot, CallbackInfoReturnable<ItemStack> cir) {
        if (slot == EquipmentSlot.MAINHAND) {
            ItemStack gun = Shoulder.gun((Player) (Object) this);
            if (!gun.isEmpty()) {
                cir.setReturnValue(gun);
            }
        }
    }
}
