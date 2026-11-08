package com.wf.wflib.mixin.armor;

import com.wf.wflib.armor.ArmorSystem;
import com.wf.wflib.armor.api.ArmorProfiles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * A managed piece worn down to its last point sits at {@code damage == maxDamage}, and vanilla's {@code hurtAndBreak}
 * compares that to the maximum even when asked for no damage at all: so the armour pass, whose charge {@code ArmorEvents}
 * zeroes, still destroys a fully worn piece (with the break sound). A charge of nothing breaks nothing.
 */
@Mixin(ItemStack.class)
public abstract class MixinItemStackWornOut {

    @Inject(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), cancellable = true)
    private void wflib$noChargeNoBreak(int amount, ServerLevel level, LivingEntity wearer, Consumer<Item> onBreak, CallbackInfo ci) {
        if (amount <= 0 && ArmorSystem.active() && ArmorProfiles.managed((ItemStack) (Object) this)) {
            ci.cancel();
        }
    }
}
