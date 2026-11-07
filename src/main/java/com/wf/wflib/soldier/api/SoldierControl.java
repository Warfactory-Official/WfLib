package com.wf.wflib.soldier.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wf.wflib.soldier.SoldierAi;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/** Orders, telemetry and events for the soldier AI: how scripted tooling and gun mods talk to it. Server thread only. */
public final class SoldierControl {
    private SoldierControl() {
    }

    /** Merge {@code orders} into the named soldier's orders; only the given keys change. */
    public static JsonObject order(JsonObject orders) {
        return SoldierAi.order(orders);
    }

    /** Give a whole fire team a common objective. */
    public static JsonArray squad(JsonObject orders) {
        return SoldierAi.squad(orders);
    }

    /** Every soldier's state. */
    public static JsonArray status() {
        return SoldierAi.status();
    }

    /** Drop the scripted (non-egg) soldiers' orders. */
    public static String clear() {
        return SoldierAi.clear();
    }

    /** Equip a player the way the AI expects. */
    public static String arm(ServerPlayer p, ResourceLocation gun, int spares) {
        return SoldierAi.arm(p, gun, spares);
    }

    /** Where Carpet-scripted bots (not soldiers) may run; default nowhere. */
    public static void filmingGate(Predicate<MinecraftServer> gate) {
        SoldierAi.filmingGate(gate);
    }

    /** A gun mod reports a real shot: soldiers who can hear it react. */
    public static void shotFired(ServerPlayer shooter, Vec3 origin) {
        SoldierAi.onGunshot(shooter, origin);
    }

    /** Where a soldier tick goes (rays, searches, ms per phase) over the last 400 ticks; {@code reset} starts a new window. */
    public static JsonObject profile(boolean reset) {
        return com.wf.wflib.soldier.SoldierProfiler.report(reset);
    }

    public static int soldierCount() {
        return SoldierAi.soldiers();
    }
}
