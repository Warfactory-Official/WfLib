package com.wf.wflib.mixin.core;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Overlapping slots: the later one is drawn on top, so it takes the click (vanilla returns the first). */
@Mixin(AbstractContainerScreen.class)
public abstract class MixinContainerScreenFindSlot {
    @Shadow
    @org.spongepowered.asm.mixin.Final
    protected AbstractContainerMenu menu;

    @Shadow
    protected abstract boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY);

    @Inject(method = "findSlot", at = @At("HEAD"), cancellable = true)
    private void wflib$topmostSlot(double mouseX, double mouseY, CallbackInfoReturnable<Slot> cir) {
        for (int i = menu.slots.size() - 1; i >= 0; i--) {
            Slot slot = menu.slots.get(i);
            if (slot.isActive() && isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
                cir.setReturnValue(slot);
                return;
            }
        }
        cir.setReturnValue(null);
    }
}
