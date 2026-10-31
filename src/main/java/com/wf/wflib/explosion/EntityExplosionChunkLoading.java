package com.wf.wflib.explosion;

import com.wf.wflib.missile.api.WFChunkValidation;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import com.wf.wflib.core.api.WFLib;

/** NTM-next per-entity BLAST_ANCHOR ticket => NeoForge entity-owned ticking ticket. */
public abstract class EntityExplosionChunkLoading extends Entity {
    public static final TicketController CHUNK_TICKET = new TicketController(
            ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "explosion"),
            WFChunkValidation::validateTickets);

    private final LongOpenHashSet forcedChunks = new LongOpenHashSet();
    private boolean released = false;
    /** Transient: {@link WFChunkValidation} drops persisted tickets => reloaded entity re-anchors once. */
    private boolean anchored;

    protected EntityExplosionChunkLoading(EntityType<?> type, Level level) {
        super(type, level);
    }

    /** NTM-next anchors on first tick => entity outside simulation distance never starts. */
    protected void anchor(int chunkRadius) {
        if (forcedChunks.isEmpty()) forceArea(chunkRadius);
        else ensureForced();
    }

    protected void forceArea(int chunkRadius) {
        if (released || !(level() instanceof ServerLevel server)) return;
        int cx = blockPosition().getX() >> 4;
        int cz = blockPosition().getZ() >> 4;
        for (int x = cx - chunkRadius; x <= cx + chunkRadius; x++) {
            for (int z = cz - chunkRadius; z <= cz + chunkRadius; z++) {
                if (forcedChunks.add(ChunkPos.asLong(x, z))) CHUNK_TICKET.forceChunk(server, this, x, z, true, true);
            }
        }
        anchored = true;
    }

    protected void ensureForced() {
        if (released || anchored || forcedChunks.isEmpty() || !(level() instanceof ServerLevel server)) return;
        for (long key : forcedChunks) {
            CHUNK_TICKET.forceChunk(server, this, ChunkPos.getX(key), ChunkPos.getZ(key), true, true);
        }
        anchored = true;
    }

    public void clearChunkLoader() {
        if (released || forcedChunks.isEmpty() || !(level() instanceof ServerLevel server)) return;
        released = true;
        if (anchored) {
            for (long key : forcedChunks) {
                CHUNK_TICKET.forceChunk(server, this, ChunkPos.getX(key), ChunkPos.getZ(key), false, true);
            }
        }
        anchored = false;
        forcedChunks.clear();
    }

    @Override
    public void remove(RemovalReason reason) {
        clearChunkLoader();
        super.remove(reason);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        int[] packed = tag.getIntArray("forcedChunks");
        for (int i = 0; i + 1 < packed.length; i += 2) forcedChunks.add(ChunkPos.asLong(packed[i], packed[i + 1]));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (forcedChunks.isEmpty()) return;
        int[] packed = new int[forcedChunks.size() * 2];
        int i = 0;
        for (long key : forcedChunks) {
            packed[i++] = ChunkPos.getX(key);
            packed[i++] = ChunkPos.getZ(key);
        }
        tag.putIntArray("forcedChunks", packed);
    }
}
