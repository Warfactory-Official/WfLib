package com.wf.wflib.mixin.core;

import com.wf.wflib.core.api.MenuSlots;
import com.wf.wflib.core.api.SlotGroupHost;
import com.wf.wflib.core.api.SlotLayout;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** Appends every {@link MenuSlots} group after the slots already in the menu; shift-click dispatch. */
@Mixin(InventoryMenu.class)
public abstract class MixinInventoryMenuSlots extends AbstractContainerMenu implements SlotGroupHost {
    @Unique
    private List<SlotLayout.Placed> wflib$placed = List.of();

    protected MixinInventoryMenuSlots(MenuType<?> type, int id) {
        super(type, id);
    }

    @Override
    public List<SlotLayout.Placed> wflib$placedGroups() {
        return wflib$placed;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void wflib$groupSlots(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
        List<SlotLayout.Placed> placed = new ArrayList<>();
        for (MenuSlots.Group g : MenuSlots.groups()) {
            int first = slots.size();
            for (int i = 0; i < g.capacity(); i++) {
                Slot slot = g.factory().create(owner, i);
                addSlot(slot);
                if (slot.index != first + i) {
                    throw new IllegalStateException("Slot group " + g.id() + " slot " + i + " got index " + slot.index
                            + ", expected " + (first + i));
                }
            }
            placed.add(new SlotLayout.Placed(g.id(), first, g.capacity()));
        }
        wflib$placed = List.copyOf(placed);
    }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void wflib$groupQuickMove(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (wflib$placed.isEmpty() || index < 0 || index >= slots.size()) {
            return;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return;
        }
        ItemStack stack = slot.getItem();
        boolean fromGroup = SlotLayout.at(wflib$placed, index) != null;
        ItemStack before = stack.copy();
        boolean moved = false;
        if (fromGroup) {
            moved = moveItemStackTo(stack, 9, 45, false);
            if (!moved) {
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
        } else if (index >= 9 && index < 45
                && player.getEquipmentSlotForItem(stack).getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
            for (MenuSlots.Group g : MenuSlots.groups()) {
                int n = Math.min(g.quickMove().intake(player, index, stack), g.capacity());
                SlotLayout.Placed p = n > 0 ? find(g) : null;
                if (p != null && moveItemStackTo(stack, p.first(), p.first() + n, false)) {
                    moved = true;
                    break;
                }
            }
        }
        if (!moved) {
            return;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY, before);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, stack);
        cir.setReturnValue(before);
    }

    @Unique
    private SlotLayout.Placed find(MenuSlots.Group g) {
        for (SlotLayout.Placed p : wflib$placed) {
            if (p.id().equals(g.id())) {
                return p;
            }
        }
        return null;
    }
}
