package com.wf.wflib.pda;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import com.wf.wflib.pda.api.Pda;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

/** Optional linkage boundary. Only the nested class references Curios classes. */
public final class PdaCurios {
    private PdaCurios() {
    }

    public static ItemStack stack(Player player) {
        return ModList.get().isLoaded("curios") ? Loaded.stack(player) : ItemStack.EMPTY;
    }

    private static final class Loaded {
        public static ItemStack stack(Player player) {
            return CuriosApi.getCuriosInventory(player)
                    .flatMap(inventory -> inventory.findFirstCurio(Pda::is))
                    .map(SlotResult::stack).orElse(ItemStack.EMPTY);
        }
    }
}
