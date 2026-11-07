package com.wf.wflib.soldier;

import com.wf.wflib.core.api.WFLib;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The headlamp: a Curios {@code head} item soldiers wear, and the only source of a soldier's light. Without Curios
 * installed a soldier is treated as carrying one, so the lights still work. Only {@code Curios} links Curios classes.
 */
public final class Headlamp {
    public static final DeferredHolder<Item, Item> ITEM = DeferredHolder.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "headlamp"));

    private Headlamp() {}

    static void register(IEventBus modBus) {
        DeferredRegister<Item> items = DeferredRegister.create(Registries.ITEM, WFLib.MODID);
        items.register("headlamp", () -> new Item(new Item.Properties().stacksTo(1)));
        items.register(modBus);
        com.wf.wflib.core.api.CreativeTabs.contribute("soldier", 460, out -> out.accept(ITEM.get()));
    }

    /** Whether the soldier has a lamp on its head to light. */
    static boolean worn(ServerPlayer p) {
        return !curios() || Curios.has(p);
    }

    /** Puts a headlamp on a freshly spawned soldier's head (a no-op without Curios). */
    public static void issue(ServerPlayer p) {
        if (curios()) Curios.give(p, new ItemStack(ITEM.get()));
    }

    /** Takes the headlamp off (a no-op without Curios). */
    static void take(ServerPlayer p) {
        if (curios()) Curios.give(p, ItemStack.EMPTY);
    }

    static boolean curios() {
        return ModList.get().isLoaded("curios");
    }

    private static final class Curios {
        static boolean has(Player p) {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p)
                    .flatMap(inventory -> inventory.findFirstCurio(stack -> stack.is(ITEM.get()))).isPresent();
        }

        static void give(Player p, ItemStack lamp) {
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p).flatMap(inventory -> inventory.getStacksHandler("head"))
                    .ifPresent(head -> head.getStacks().setStackInSlot(0, lamp));
        }
    }
}
