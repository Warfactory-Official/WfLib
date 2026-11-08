package com.wf.wflib.nvg.api;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.List;

/** The NVG on an entity's head: worn standalone, mounted on the helmet, or in the Curios head slot. */
public final class NvgWorn {
    private NvgWorn() {
    }

    /** Read-only (mounted: the component's own stack); write through {@link #set}. */
    public static ItemStack nvg(LivingEntity entity) {
        ItemStack head = entity.getItemBySlot(EquipmentSlot.HEAD);
        if (head.getItem() instanceof NvgItem) {
            return head;
        }
        ItemStack mounted = mounted(head);
        return mounted.isEmpty() ? com.wf.wflib.nvg.NvgCurios.stack(entity) : mounted;
    }

    public static NvgState state(LivingEntity entity) {
        return state(nvg(entity));
    }

    public static NvgState state(ItemStack nvg) {
        return nvg.getOrDefault(NvgComponents.STATE.get(), NvgState.UP);
    }

    public static boolean down(LivingEntity entity) {
        ItemStack nvg = nvg(entity);
        return !nvg.isEmpty() && state(nvg).down();
    }

    /** No NVG => no-op. Fires an equipment change (same item). */
    public static void set(LivingEntity entity, NvgState state) {
        ItemStack head = entity.getItemBySlot(EquipmentSlot.HEAD);
        if (head.getItem() instanceof NvgItem) {
            head.set(NvgComponents.STATE.get(), state);
            return;
        }
        ItemContainerContents mount = head.get(NvgComponents.MOUNT.get());
        if (mount == null) {
            ItemStack worn = com.wf.wflib.nvg.NvgCurios.stack(entity);
            if (!worn.isEmpty()) {
                worn.set(NvgComponents.STATE.get(), state);
            }
            return;
        }
        ItemStack nvg = mount.copyOne();
        nvg.set(NvgComponents.STATE.get(), state);
        head.set(NvgComponents.MOUNT.get(), ItemContainerContents.fromItems(List.of(nvg)));
    }

    /** Mounted NVG, or EMPTY. Read-only: the component's own stack. */
    public static ItemStack mounted(ItemStack helmet) {
        ItemContainerContents mount = helmet.get(NvgComponents.MOUNT.get());
        if (mount == null) {
            return ItemStack.EMPTY;
        }
        for (ItemStack stack : mount.nonEmptyItems()) {
            return stack;
        }
        return ItemStack.EMPTY;
    }

    /** Fresh NVG stack leaving a head: flipped up. */
    static ItemStack raised(ItemStack nvg) {
        nvg.set(NvgComponents.STATE.get(), state(nvg).withDown(false));
        return nvg;
    }
}
