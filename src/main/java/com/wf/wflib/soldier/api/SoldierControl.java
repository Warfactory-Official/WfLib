package com.wf.wflib.soldier.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wf.wflib.soldier.SoldierAi;
import com.wf.wflib.soldier.SoldierProfiler;
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
        return SoldierProfiler.report(reset);
    }

    /**
     * Marks a soldier as crew of a vehicle: its AI keeps seeing and choosing targets but stops walking, aiming and shooting,
     * leaving the controls to whoever drives the vehicle. Call with {@code false} when it dismounts.
     */
    public static void crewed(ServerPlayer soldier, boolean on) {
        SoldierAi.setCrewed(soldier, on);
    }

    /** Walks one soldier to exactly this point, straight (no cover-to-cover, no arrival radius), then it stands there. */
    public static void walkTo(ServerPlayer soldier, Vec3 at) {
        SoldierAi.walkTo(soldier, at);
    }

    /** Sends one soldier to a point as a MOVE order, ending within {@code radius} blocks of it. */
    public static void moveTo(ServerPlayer soldier, Vec3 at, int radius) {
        SoldierAi.moveTo(soldier, at, radius);
    }

    /** The enemy this soldier sees and would shoot now (a soldier, a player or a hostile mob), or null: what a gunner aims at. */
    public static net.minecraft.world.entity.LivingEntity target(ServerPlayer soldier) {
        return SoldierAi.currentTarget(soldier);
    }

    public static int soldierCount() {
        return SoldierAi.soldiers();
    }
}
