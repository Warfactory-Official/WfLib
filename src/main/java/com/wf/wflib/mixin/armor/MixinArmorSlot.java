package com.wf.wflib.mixin.armor;

import com.wf.wflib.armor.ArmorSystem;
import com.wf.wflib.armor.api.ArmorStacks;
import com.wf.wflib.armor.api.Dress;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Suit-occupied armour slots refuse placement ({@link ArmorStacks#blocked}); {@code canEquip} sees only the item. Unaided dressing: {@link Dress}. */
@Mixin(targets = "net.minecraft.world.inventory.ArmorSlot")
public abstract class MixinArmorSlot extends Slot {
    private MixinArmorSlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
    }

    @Shadow
    @Final
    private LivingEntity owner;
    @Shadow
    @Final
    private EquipmentSlot slot;

    @Inject(method = "mayPlace", at = @At("RETURN"), cancellable = true)
    private void wflib$suit(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (ArmorSystem.active() && cir.getReturnValueZ() && (ArmorStacks.blocked(owner, slot, stack)
                || owner instanceof Player p && !Dress.selfAllowed(p, stack))) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "mayPickup", at = @At("RETURN"), cancellable = true)
    private void wflib$dress(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (ArmorSystem.active() && cir.getReturnValueZ() && !Dress.selfAllowed(player, getItem())) {
            cir.setReturnValue(false);
        }
    }
}
