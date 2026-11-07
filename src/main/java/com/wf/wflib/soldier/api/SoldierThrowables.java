package com.wf.wflib.soldier.api;

import com.wf.wflib.grenade.api.ThrownCharge;
import net.minecraft.server.level.ServerPlayer;

/** Grenade handling for the soldier AI, over whatever mod owns the grenade items. Server thread only. */
public interface SoldierThrowables {

    enum Kind {
        FRAG, SMOKE, OTHER
    }

    /** What a grenade item is for: the AI throws frags at hidden contacts and smoke to cover movement. */
    Kind kind(net.minecraft.resources.ResourceLocation item);

    boolean smoke(ThrownCharge charge);

    /** Launch speed (blocks per tick) of the grenade in the main hand, or NaN when it is not a grenade. */
    double heldSpeed(ServerPlayer p);

    /** Put the pin back (the soldier must never keep a primed grenade it is not throwing). */
    void repin(ServerPlayer p);

    /** Pull the pin and cock the arm. */
    void pull(ServerPlayer p);

    /** Throw it. */
    void release(ServerPlayer p);
}
