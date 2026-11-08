package com.wf.wflib.nvg;

import com.wf.wflib.nvg.api.NvgItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/** Goggles worn in the Curios head slot. Optional linkage boundary: only the nested class references Curios classes. */
public final class NvgCurios {
    private NvgCurios() {}

    /** The live goggle stack in the Curios slot (write through it), or EMPTY. */
    public static ItemStack stack(LivingEntity entity) {
        return ModList.get().isLoaded("curios") ? Loaded.stack(entity) : ItemStack.EMPTY;
    }

    /** Puts {@code nvg} in the first free Curios head slot; false without Curios or room. */
    public static boolean wear(LivingEntity entity, ItemStack nvg) {
        return ModList.get().isLoaded("curios") && Loaded.wear(entity, nvg);
    }

    private static final class Loaded {
        static ItemStack stack(LivingEntity entity) {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(entity)
                    .flatMap(inventory -> inventory.findFirstCurio(s -> s.getItem() instanceof NvgItem))
                    .map(top.theillusivec4.curios.api.SlotResult::stack).orElse(ItemStack.EMPTY);
        }

        static boolean wear(LivingEntity entity, ItemStack nvg) {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(entity)
                    .flatMap(inventory -> inventory.getStacksHandler("head")).map(head -> {
                        for (int i = 0; i < head.getSlots(); i++) {
                            if (head.getStacks().getStackInSlot(i).isEmpty()) {
                                head.getStacks().setStackInSlot(i, nvg);
                                return true;
                            }
                        }
                        return false;
                    }).orElse(false);
        }
    }
}
