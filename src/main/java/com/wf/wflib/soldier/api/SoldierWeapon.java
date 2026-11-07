package com.wf.wflib.soldier.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** What the soldier AI needs of a gun mod: read the ammunition, work the weapon, fire. Server thread only. */
public interface SoldierWeapon {

    /** Rounds in the gun, rounds in the fullest magazine that could replace it (0 = none), and a magazine's capacity. */
    record Ammo(int left, int spare, int capacity) {
    }

    enum Shot {
        FIRED, EMPTY, REFUSED
    }

    /** One trigger pull: what happened, and a short word for telemetry. */
    record Result(Shot shot, String verdict) {
    }

    /** The held weapon's ammunition, or null when the soldier is not holding a weapon the AI can use. */
    @Nullable Ammo ammo(ServerPlayer p);

    /** A weapon action (reload, unjam, switch) is under way. */
    boolean busy(ServerPlayer p);

    /** The soldier cannot work a weapon right now (disabled hands, for instance). */
    boolean blocked(ServerPlayer p);

    boolean jammed(ServerPlayer p);

    /** @return true when clearing the jam has started */
    boolean unjam(ServerPlayer p);

    /** @return true when a reload has started */
    boolean reload(ServerPlayer p);

    void cancel(ServerPlayer p);

    /** One shot, through the same path a player's shot takes (ammunition, cadence, jams, effects, attribution). */
    Result fire(ServerPlayer p);

    /** Whether the AI can use this gun: a detachable magazine it can reload. */
    boolean usable(ResourceLocation gun);

    /** Every installed gun id, for a fallback when a preferred one is missing. */
    List<ResourceLocation> guns();

    /**
     * Puts {@code gun} in the main hand loaded, {@code spares} more full magazines in the inventory, and the grenades
     * in the hotbar from the right.
     *
     * @return a one-line description of what was issued
     */
    String arm(ServerPlayer p, ResourceLocation gun, int spares, List<ResourceLocation> grenades);
}
