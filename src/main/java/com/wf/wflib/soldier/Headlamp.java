package com.wf.wflib.soldier;

import com.wf.wflib.core.api.WFLib;
import net.minecraft.core.component.DataComponentType;
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

    /** On the headlamp stack a player wears: switched on. Soldiers' lamps follow the dark instead and ignore it. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> LIT = DeferredHolder.create(
            Registries.DATA_COMPONENT_TYPE, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "headlamp_on"));
    /** Players whose lamp the server last told clients is on. */
    private static final java.util.Set<java.util.UUID> SHINING = new java.util.HashSet<>();

    private Headlamp() {}

    static void register(IEventBus modBus) {
        DeferredRegister<DataComponentType<?>> components = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, WFLib.MODID);
        components.register("headlamp_on", () -> DataComponentType.<Boolean>builder()
                .persistent(com.mojang.serialization.Codec.BOOL).networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL).build());
        components.register(modBus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post.class, Headlamp::tick);
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

    /** For tests: what the soldier's Curios inventory looks like. */
    static String describe(ServerPlayer p) {
        return curios() ? Curios.describe(p) : "curios not loaded";
    }

    /** Switches a player's worn headlamp on or off (the key); false when none is worn. */
    static boolean toggle(ServerPlayer p) {
        ItemStack lamp = curios() ? Curios.stack(p) : ItemStack.EMPTY;
        if (lamp.isEmpty()) return false;
        boolean now = !lamp.getOrDefault(LIT.get(), false);
        lamp.set(LIT.get(), now);
        p.level().playSound(null, p.getX(), p.getEyeY(), p.getZ(), net.minecraft.sounds.SoundEvents.LEVER_CLICK,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.4F, now ? 1.6F : 1.2F);
        shine(p, now);
        return true;
    }

    /** Whether the player's worn headlamp is switched on. */
    static boolean on(Player p) {
        if (!curios()) return false;
        ItemStack lamp = Curios.stack(p);
        return !lamp.isEmpty() && lamp.getOrDefault(LIT.get(), false);
    }

    private static void shine(ServerPlayer p, boolean on) {
        if (on) SHINING.add(p.getUUID()); else SHINING.remove(p.getUUID());
        SoldierLights.send(p, on);
    }

    /** Keeps nearby clients told while a player's lamp is on (they let it lapse), and tells them when it goes off. */
    static void tick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.tickCount % 10 != 0) return;
        // Every soldier carries its lamp: issue one to any that lost or never got it (a respawn, a reload).
        if (SoldierAi.scripted(p)) {
            if (p.tickCount % 40 == 0 && curios() && !Curios.wearing(p)) issue(p);
            return;
        }
        boolean on = on(p);
        if (on && (!SHINING.contains(p.getUUID()) || p.tickCount % SoldierLights.REPEAT == 0)) shine(p, true);
        else if (!on && SHINING.contains(p.getUUID())) shine(p, false);
    }

    /** Whether the player visibly wears one (the client draws it; false without Curios, where nothing can be worn). */
    public static boolean wearing(Player p) {
        return curios() && Curios.wearing(p);
    }

    static boolean curios() {
        return ModList.get().isLoaded("curios");
    }

    private static final class Curios {
        static boolean has(Player p) {
            // No head slot at all (a pack removed it): nothing to wear a lamp on, so it counts as carried.
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p)
                    .map(inventory -> inventory.getStacksHandler("head").isEmpty()
                            || inventory.findFirstCurio(stack -> stack.is(ITEM.get())).isPresent())
                    .orElse(true);
        }

        static ItemStack stack(Player p) {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p)
                    .flatMap(inventory -> inventory.findFirstCurio(stack -> stack.is(ITEM.get())))
                    .map(top.theillusivec4.curios.api.SlotResult::stack).orElse(ItemStack.EMPTY);
        }

        static boolean wearing(Player p) {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p)
                    .map(inventory -> inventory.findFirstCurio(stack -> stack.is(ITEM.get())).isPresent()).orElse(false);
        }

        static String describe(Player p) {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p)
                    .map(inventory -> "slots " + inventory.getCurios().keySet() + ", head "
                            + inventory.getStacksHandler("head").map(h -> h.getSlots() + " slot(s), holding " + h.getStacks().getStackInSlot(0)).orElse("none"))
                    .orElse("no curios inventory");
        }

        static void give(Player p, ItemStack lamp) {
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p).flatMap(inventory -> inventory.getStacksHandler("head"))
                    .ifPresent(head -> head.getStacks().setStackInSlot(0, lamp));
        }
    }
}
