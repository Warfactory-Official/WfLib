package com.wf.wflib.mine.api;

import com.wf.wflib.core.api.WFRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import com.wf.wflib.mine.MineTargets;
import com.wf.wflib.core.api.WFLib;

/**
 * Registry of the {@link MineTrigger}s a mine can be fused with, keyed by a stable {@link ResourceLocation} exactly
 * as {@link com.wf.wflib.warhead.api.Warheads} keys warheads, so what a buried mine is watching for
 * survives save/load, which a lambda on its own cannot.
 */
public final class MineTriggers {

    /** Anything at all that comes close enough. */
    public static final MineTrigger ANY = MineTrigger.standard(MineTargets.ANY);
    /** Anything alive: the default, and what a mine is normally for. */
    public static final MineTrigger LIVING = MineTrigger.standard(MineTargets.LIVING);
    /** Players only, spectators excluded. */
    public static final MineTrigger PLAYERS = MineTrigger.standard(MineTargets.PLAYERS);
    /** Mobs only, so a minefield laid against a swarm ignores the people who laid it. */
    public static final MineTrigger MOBS = MineTrigger.standard(MineTargets.MOBS);
    /** Vehicles and anything bigger than a person: what an anti-armour mine is fused for. */
    public static final MineTrigger HEAVY = MineTrigger.standard(MineTargets.HEAVY);

    private static final ResourceLocation DEFAULT_ID = rl("living");
    static final WFRegistry<MineTrigger> TRIGGERS = WFRegistry.create("mine", "mine triggers");

    private MineTriggers() {
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path);
    }

    public static ResourceLocation parse(String id) {
        if (id == null || id.isEmpty()) {
            return DEFAULT_ID;
        }
        ResourceLocation parsed = id.indexOf(':') >= 0 ? ResourceLocation.tryParse(id) : rl(id);
        return parsed != null ? parsed : DEFAULT_ID;
    }

    /** Dup id or registration after freeze throws. */
    public static void register(ResourceLocation id, MineTrigger trigger) {
        TRIGGERS.register(id, trigger);
    }

    /** @return the trigger, or {@link #LIVING} for an id nothing registered (an off system's, a removed pack's). */
    public static MineTrigger get(ResourceLocation id) {
        MineTrigger trigger = TRIGGERS.get(id);
        return trigger != null ? trigger : LIVING;
    }

    public static boolean exists(ResourceLocation id) {
        return TRIGGERS.containsKey(id);
    }

    public static Set<ResourceLocation> ids() {
        return TRIGGERS.ids();
    }

    public static ResourceLocation defaultId() {
        return DEFAULT_ID;
    }
}
