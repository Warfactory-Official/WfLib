package com.wf.wflib.soldier;

import com.wf.wflib.soldier.api.SoldierMedical;
import com.wf.wflib.soldier.api.SoldierServices;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** First aid through whatever {@link SoldierMedical} the medical mod registered, using the same timed actions a player does. */
final class BotMedical {
    private BotMedical() {}

    private static SoldierMedical m() { return SoldierServices.medical(); }

    static boolean present() { return m().present(); }

    static SoldierMedical.Condition condition(ServerPlayer p) { return m().condition(p); }

    static boolean active(ServerPlayer p) { return m().active(p); }

    static void cancel(ServerPlayer p) { m().cancel(p); }

    static boolean begin(ServerPlayer actor, ServerPlayer patient, SoldierMedical.Plan plan) {
        return plan != null && m().begin(actor, patient, plan);
    }

    static boolean revive(ServerPlayer actor, ServerPlayer patient) { return m().revive(actor, patient); }

    static SoldierMedical.Plan plan(ServerPlayer actor, ServerPlayer patient) { return m().plan(actor, patient); }

    static void kit(ServerPlayer p, boolean medic) { m().kit(p, medic); }

    /** The item to use goes to the hotbar and is selected. */
    static void equip(ServerPlayer p, SoldierMedical.Plan plan) {
        int slot = plan.slot();
        if (slot >= 9) {
            ItemStack displaced = p.getInventory().getItem(6);
            p.getInventory().setItem(6, p.getInventory().getItem(slot));
            p.getInventory().setItem(slot, displaced);
            slot = 6;
        }
        p.getInventory().selected = slot;
    }

    static int count(ServerPlayer p, ResourceLocation id) {
        int count = 0;
        for (ItemStack stack : p.getInventory().items)
            if (BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id)) count += stack.getCount();
        return count;
    }
}
