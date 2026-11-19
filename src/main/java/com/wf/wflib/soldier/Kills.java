package com.wf.wflib.soldier;

import com.wf.wflib.damage.api.Attribution;
import com.wf.wflib.soldier.api.SoldierKillEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/** Turns a soldier's death into a {@link SoldierKillEvent}. */
final class Kills {
    private Kills() {
    }

    /** Builds and posts the event for {@code victim} (a scripted soldier) dying to {@code source}. */
    static SoldierKillEvent post(ServerPlayer victim, String victimTeam, DamageSource source) {
        var event = build(victim, victimTeam, source);
        NeoForge.EVENT_BUS.post(event);
        return event;
    }

    static SoldierKillEvent build(ServerPlayer victim, String victimTeam, DamageSource source) {
        Entity attacker = source.getEntity();
        ServerPlayer killer = attacker instanceof ServerPlayer p && p != victim ? p : null;
        String killerTeam = null;
        if (killer != null && SoldierAi.scripted(killer)) {
            var bot = SoldierAi.botOf(killer.getGameProfile().getName());
            killerTeam = bot == null || bot.team.isEmpty() ? null : bot.team;
        }
        double distance = killer == null ? -1 : killer.position().distanceTo(victim.position());
        return new SoldierKillEvent(victim, victimTeam, killer, killerTeam, Attribution.faction(source),
                weapon(killer, source), distance, source);
    }

    /** The killer's held item, else the direct cause's type (an arrow, a thrown grenade); null when neither says anything. */
    @Nullable
    private static ResourceLocation weapon(@Nullable ServerPlayer killer, DamageSource source) {
        if (killer != null) {
            ItemStack held = killer.getMainHandItem();
            if (!held.isEmpty()) return BuiltInRegistries.ITEM.getKey(held.getItem());
        }
        Entity direct = source.getDirectEntity();
        return direct == null || direct == killer ? null : BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType());
    }
}
