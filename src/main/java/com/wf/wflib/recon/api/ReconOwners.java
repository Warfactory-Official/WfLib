package com.wf.wflib.recon.api;

import com.wf.wflib.compat.api.WarforgeCompat;
import com.wf.wflib.core.api.OrderedRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** The ownership chain, and the answer for anything nobody claimed. */
public final class ReconOwners {

    /** Highest priority first. */
    public static final OrderedRegistry<OwnerSource> SOURCES = OrderedRegistry.create("recon", "recon.owners");

    private ReconOwners() {
    }

    public static int sourceCount() {
        return SOURCES.values().size();
    }

    /**
     * @return who holds this ground, or null if nobody does.
     */
    @Nullable
    public static UUID owningAt(ServerLevel level, BlockPos pos) {
        for (OwnerSource source : SOURCES.values()) {
            UUID owner = source.owningAt(level, pos);
            if (owner != null) {
                return owner;
            }
        }
        return null;
    }

    /**
     * @return whose side this entity is on, or null if it has none.
     */
    @Nullable
    public static UUID owningEntity(Entity entity) {
        for (OwnerSource source : SOURCES.values()) {
            UUID owner = source.owningEntity(entity);
            if (owner != null) {
                return owner;
            }
        }
        return null;
    }

    /**
     * @return the network id of whoever holds this ground, or {@link ReconNet#UNAFFILIATED}.
     */
    public static long netAt(ServerLevel level, BlockPos pos) {
        return ReconNet.netId(owningAt(level, pos));
    }

    /**
     * @return the network id of whoever this entity answers to, or {@link ReconNet#UNAFFILIATED}.
     */
    public static long netOf(Entity entity) {
        return ReconNet.netId(owningEntity(entity));
    }

    /** WarForge, if it is installed; registered by {@code ReconSystem}. */
    public static final class WarforgeOwners implements OwnerSource {

        @Override
        @Nullable
        public UUID owningAt(ServerLevel level, BlockPos pos) {
            return WarforgeCompat.factionClaiming(level, pos);
        }

        @Override
        @Nullable
        public UUID owningEntity(Entity entity) {
            return entity instanceof Player player ? WarforgeCompat.factionOfPlayer(player.getUUID()) : null;
        }
    }
}
