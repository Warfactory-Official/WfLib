package com.wf.wflib.armor.api.exo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server glue: stacks -> runtime, sprint gate. */
public final class ExoEvents {
    private ExoEvents() {
    }

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && event.getSlot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
            ExoServer.recompute(p, false);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            ExoServer.forget(p.getUUID());
            ExoServer.recompute(p, false);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            ExoServer.forget(p.getUUID());
            ExoServer.recompute(p, false);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ExoServer.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer p && p.isSprinting() && !ExoServer.effect(p).canSprint()) {
            p.setSprinting(false);
        }
    }
}
