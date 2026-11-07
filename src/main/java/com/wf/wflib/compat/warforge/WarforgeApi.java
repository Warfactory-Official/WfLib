package com.wf.wflib.compat.warforge;

import com.flansmod.warforge.api.AlterKind;
import com.flansmod.warforge.api.WarforgeAPI;
import com.flansmod.warforge.api.ZoneKind;
import com.wf.wflib.compat.api.ClaimAction;
import com.wf.wflib.compat.api.TerritoryVerdict;
import com.wf.wflib.damage.api.Attribution;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public final class WarforgeApi {

    private static final UUID NULL_UUID = new UUID(0L, 0L);

    private WarforgeApi() {
    }

    public static void registerKillAttribution() {
        WarforgeAPI.addKillAttributor(Attribution::faction);
    }

    public static UUID factionOfPlayer(UUID playerId) {
        return WarforgeAPI.factionOf(playerId);
    }

    public static UUID factionClaiming(Level level, BlockPos pos) {
        return WarforgeAPI.ownerOf(level.dimension(), new ChunkPos(pos));
    }

    /** Can a drone owned by {@code faction} edit blocks here. */
    public static TerritoryVerdict buildVerdict(Level level, UUID faction, BlockPos pos, ClaimAction kind) {
        TerritoryVerdict zone = zoneVerdict(level, faction, pos);
        if (zone.allowed() && !WarforgeAPI.mayAlter(level.dimension(), new ChunkPos(pos), null, faction, AlterKind.valueOf(kind.name()))) {
            return TerritoryVerdict.CLAIM_RULES;
        }
        return zone;
    }

    private static TerritoryVerdict zoneVerdict(Level level, UUID faction, BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        switch (WarforgeAPI.siegeZoneAt(level.dimension(), chunk)) {
            case SIEGED -> {
                return TerritoryVerdict.SIEGE;
            }
            case WAR -> {
                return TerritoryVerdict.WAR_ZONE;
            }
            default -> {
            }
        }
        ZoneKind zone = WarforgeAPI.zoneAt(level.dimension(), chunk);
        if (zone == ZoneKind.WILDERNESS) {
            return TerritoryVerdict.ALLOWED;
        }
        if (zone == ZoneKind.SAFE_ZONE) {
            return TerritoryVerdict.SAFE_ZONE;
        }
        if (zone == ZoneKind.WAR_ZONE) {
            return TerritoryVerdict.WAR_ZONE;
        }
        if (faction == null || faction.equals(NULL_UUID)) {
            // No faction => no standing against any claim.
            return TerritoryVerdict.FOREIGN_CLAIM;
        }
        UUID claim = WarforgeAPI.ownerOf(level.dimension(), chunk);
        return WarforgeAPI.areAlliedOrTruced(faction, claim) ? TerritoryVerdict.ALLOWED : TerritoryVerdict.FOREIGN_CLAIM;
    }

    /** Batched owner lookup for a route re-asked every edit. Unclaimed chunk: absent, not null-mapped. */
    public static Map<ChunkPos, UUID> ownersAlong(Level level, Collection<ChunkPos> chunks) {
        return WarforgeAPI.ownersAlong(level.dimension(), chunks);
    }

    /** Same rule the block-place event cancels on. */
    public static boolean mayPlaceAt(Level level, UUID playerId, ChunkPos chunk, Block block) {
        return WarforgeAPI.mayPlace(playerId, level.dimension(), chunk, block);
    }

    /** 0xRRGGBB; {@code fallback} if no such faction. */
    public static int factionColour(UUID factionId, int fallback) {
        return WarforgeAPI.factionColour(factionId, fallback);
    }

    /** Null: no such faction. */
    public static String factionName(UUID factionId) {
        return WarforgeAPI.factionName(factionId);
    }

    /** Same faction or allied; truce != ally, no map sharing on a ceasefire. */
    public static boolean areFactionsAllied(UUID a, UUID b) {
        return WarforgeAPI.areAllied(a, b);
    }

    public static boolean maySeeFactionContent(UUID playerId, UUID ownerFaction) {
        return WarforgeAPI.maySeeFactionContent(playerId, ownerFaction);
    }

    public static boolean areFactionsFriendly(UUID a, UUID b) {
        return WarforgeAPI.areAlliedOrTruced(a, b);
    }

    public static boolean claimProtected(Level level, UUID player, BlockPos pos) {
        return WarforgeAPI.isExplosionProtected(level, pos, player);
    }

    public static void filterClaimProtected(Level level, UUID igniterFaction, Collection<BlockPos> positions) {
        WarforgeAPI.filterExplosionProtected(level, igniterFaction, positions);
    }

    public static boolean mayAlter(Level level, ChunkPos chunk, UUID player, UUID faction, ClaimAction kind) {
        return WarforgeAPI.mayAlter(level.dimension(), chunk, player, faction, AlterKind.valueOf(kind.name()));
    }
}
