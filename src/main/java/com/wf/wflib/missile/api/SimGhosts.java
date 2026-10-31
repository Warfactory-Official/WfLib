package com.wf.wflib.missile.api;

import com.wf.wflib.core.api.WFRegistry;
import com.wf.wflib.missile.SimGhostTracking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Off-world sim objects shown to nearby players as render-only ghosts; key = ghost kind. */
public final class SimGhosts {
    @FunctionalInterface
    public interface Source {
        void each(ServerLevel level, Sink sink);
    }

    @FunctionalInterface
    public interface Sink {
        void accept(UUID sim, Vec3 pos, ResourceLocation model, int exhaust);
    }

    public static final WFRegistry<Source> SOURCES = WFRegistry.create("missile.sim_ghost_sources");

    private SimGhosts() {
    }

    /** Sim UUID behind a ghost handle, if the player was sent it. */
    @Nullable
    public static UUID simOf(ServerPlayer player, int handle) {
        return SimGhostTracking.sim(player, handle);
    }
}
