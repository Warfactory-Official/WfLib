package com.wf.wflib.explosion.nuke.util;

import com.wf.wflib.explosion.nuke.BombForkJoinPool;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LightEngine;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import com.wf.wflib.core.api.WFLib;

/** NTM-next {@code ChunkUtil}, nuke subset. */
@EventBusSubscriber(modid = WFLib.MODID)
public final class NukeChunks {

    private static final VarHandle SECTIONS = MethodHandles.arrayElementVarHandle(LevelChunkSection[].class);
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final ThreadLocal<int[]> TL_CARVED = ThreadLocal.withInitial(() -> new int[4096]);

    public static final TicketType<ChunkPos> BLAST_LOAD =
            TicketType.create(WFLib.MODID + ":blast_load", Comparator.comparingLong(ChunkPos::toLong));

    private static final Object2IntOpenHashMap<ResourceKey<Level>> activeTask = new Object2IntOpenHashMap<>();
    private static final Map<ResourceKey<Level>, ConcurrentHashMap<Long, LevelChunk>> chunkMap = new ConcurrentHashMap<>();
    private static final Map<ServerLevel, Long2IntOpenHashMap> BLAST_LOAD_CLAIMS = new WeakHashMap<>();
    private static volatile int refCounter = 0;

    private NukeChunks() {
    }

    public static ConcurrentHashMap<Long, LevelChunk> acquireMirrorMap(ServerLevel level) {
        ResourceKey<Level> dim = level.dimension();
        ConcurrentHashMap<Long, LevelChunk> thisDim;
        if (activeTask.addTo(dim, 1) == 0) {
            thisDim = new ConcurrentHashMap<>(4096);
            for (ChunkHolder holder : level.getChunkSource().chunkMap.getChunks()) {
                LevelChunk chunk = fullChunkIfLoaded(holder);
                if (chunk != null) thisDim.put(chunk.getPos().toLong(), chunk);
            }
            chunkMap.put(dim, thisDim);
        } else {
            thisDim = chunkMap.get(dim);
        }
        refCounter++;
        return Objects.requireNonNull(thisDim);
    }

    public static void releaseMirrorMap(ServerLevel level) {
        ResourceKey<Level> dim = level.dimension();
        if (activeTask.addTo(dim, -1) == 1) {
            chunkMap.remove(dim);
            activeTask.removeInt(dim);
        }
        refCounter--;
    }

    private static @Nullable LevelChunk fullChunkIfLoaded(@Nullable ChunkHolder holder) {
        if (holder == null || holder.getTicketLevel() > ChunkLevel.byStatus(FullChunkStatus.FULL)) return null;
        return holder.getFullChunkFuture().getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK).orElse(null);
    }

    public static @Nullable LevelChunk liveChunkNow(ServerLevel level, long chunkPos) {
        return fullChunkIfLoaded(level.getChunkSource().chunkMap.getVisibleChunkIfPresent(chunkPos));
    }

    public static void addBlastTicket(ServerLevel level, long chunkPos) {
        Long2IntOpenHashMap held = BLAST_LOAD_CLAIMS.computeIfAbsent(level, ignored -> new Long2IntOpenHashMap());
        if (held.addTo(chunkPos, 1) > 0) return;
        ChunkPos pos = new ChunkPos(chunkPos);
        level.getChunkSource().addRegionTicket(BLAST_LOAD, pos, 0, pos);
    }

    public static void flushChunkTickets(ServerLevel level) {
        level.getChunkSource().runDistanceManagerUpdates();
    }

    public static @Nullable CompletableFuture<ChunkResult<LevelChunk>> blastChunkFuture(ServerLevel level, long chunkPos) {
        ChunkHolder holder = level.getChunkSource().chunkMap.getVisibleChunkIfPresent(chunkPos);
        return holder == null ? null : holder.getFullChunkFuture();
    }

    public static void releaseChunkAsync(ServerLevel level, long chunkPos) {
        Long2IntOpenHashMap held = BLAST_LOAD_CLAIMS.get(level);
        int count = held.addTo(chunkPos, -1);
        if (count <= 0) throw new IllegalStateException("Blast ticket underflow at " + new ChunkPos(chunkPos));
        if (count > 1) return;
        held.remove(chunkPos);
        if (held.isEmpty()) BLAST_LOAD_CLAIMS.remove(level);
        ChunkPos pos = new ChunkPos(chunkPos);
        level.getChunkSource().removeRegionTicket(BLAST_LOAD, pos, 0, pos);
    }

    public static LevelChunkSection @Nullable [] getLoadedSections(ConcurrentHashMap<Long, LevelChunk> mirror,
                                                                   long chunkPos) {
        LevelChunk chunk = mirror.get(chunkPos);
        return chunk == null ? null : chunk.getSections();
    }

    /** Return: lighting changed; caller batches it. */
    public static boolean postCarveBlockUpdate(ServerLevel level, LevelChunk chunk, BlockPos pos,
                                               BlockState oldState, BlockState newState) {
        oldState.onRemove(level, pos, newState, false);
        newState.onPlace(level, pos, oldState, false);
        int lx = pos.getX() & 15;
        int ly = pos.getY();
        int lz = pos.getZ() & 15;
        chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.MOTION_BLOCKING).update(lx, ly, lz, newState);
        chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES).update(lx, ly, lz, newState);
        chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR).update(lx, ly, lz, newState);
        chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE).update(lx, ly, lz, newState);
        return LightEngine.hasDifferentLightProperties(chunk, pos, oldState, newState);
    }

    public static CompletableFuture<?> updateLight(ServerLevel level, LevelChunk chunk, LongList positions,
                                                   int sectionMaskFromBottom) {
        ThreadedLevelLightEngine lightEngine = level.getChunkSource().getLightEngine();
        int cx = chunk.getPos().x;
        int cz = chunk.getPos().z;
        int minSectionY = level.getMinSection();
        LevelChunkSection[] sections = chunk.getSections();
        for (int mask = sectionMaskFromBottom; mask != 0; mask &= mask - 1) {
            int subY = Integer.numberOfTrailingZeros(mask);
            lightEngine.updateSectionStatus(SectionPos.of(cx, subY + minSectionY, cz), sections[subY].hasOnlyAir());
        }
        var skyLightSources = chunk.getSkyLightSources();
        for (int i = 0; i < positions.size(); i++) {
            BlockPos pos = BlockPos.of(positions.getLong(i));
            skyLightSources.update(chunk, pos.getX() & 15, pos.getY(), pos.getZ() & 15);
            lightEngine.checkBlock(pos);
        }
        return lightEngine.waitForPendingTasks(cx, cz);
    }

    public static void broadcastWholesaleResend(ServerLevel level, long chunkPos, CompletableFuture<?> lighting) {
        int cx = ChunkPos.getX(chunkPos);
        int cz = ChunkPos.getZ(chunkPos);
        ThreadedLevelLightEngine lightEngine = level.getChunkSource().getLightEngine();
        lighting.thenAcceptAsync(ignored -> {
            LevelChunk fresh = level.getChunkSource().getChunkNow(cx, cz);
            if (fresh == null) return;
            fresh.setUnsaved(true);
            List<ServerPlayer> players = level.getChunkSource().chunkMap.getPlayers(fresh.getPos(), false);
            if (players.isEmpty()) return;
            ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(fresh, lightEngine, null, null);
            for (ServerPlayer p : players) p.connection.send(packet);
        }, level.getServer());
    }

    public static boolean carveSnapshot(ServerLevel level, int chunkX, int chunkZ, int subY, SectionSnapshot snapshot,
                                        BitSet localMask, LongCollection edgeOut,
                                        Long2ObjectMap<BlockState> modifiedOut) {
        LevelChunkSection src = snapshot.sections[subY];
        if (src == null || src.hasOnlyAir()) return false;

        int nonEmpty = src.nonEmptyBlockCount;
        ConcurrentHashMap<Long, LevelChunk> loaded = chunkMap.get(level.dimension());

        int xBase = chunkX << 4;
        int yBase = level.getMinBuildHeight() + (subY << 4);
        int zBase = chunkZ << 4;

        int[] carved = TL_CARVED.get();
        int carvedCount = 0;
        for (int idx = localMask.nextSetBit(0); idx >= 0 && idx < 4096; idx = localMask.nextSetBit(idx + 1)) {
            int xLocal = idx & 0xF;
            int yLocal = (idx >>> 8) & 0xF;
            int zLocal = (idx >>> 4) & 0xF;

            BlockState old = src.getBlockState(xLocal, yLocal, zLocal);
            if (old.isAir()) continue;

            long packedPos = BlockPos.asLong(xBase | xLocal, yBase | yLocal, zBase | zLocal);
            modifiedOut.put(packedPos, old);

            if (old.hasBlockEntity()) continue;

            if (touchesEdge(loaded, chunkX, chunkZ, subY, src, xLocal, yLocal, zLocal, localMask)) {
                edgeOut.add(packedPos);
            }
            carved[carvedCount++] = idx;
        }
        if (carvedCount == 0) return false;

        if (carvedCount == nonEmpty) {
            snapshot.sections[subY] = new LevelChunkSection(new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, AIR,
                    PalettedContainer.Strategy.SECTION_STATES), src.getBiomes());
        } else {
            for (int i = 0; i < carvedCount; i++) {
                int idx = carved[i];
                src.setBlockState(idx & 0xF, (idx >>> 8) & 0xF, (idx >>> 4) & 0xF, AIR, false);
            }
        }
        snapshot.changed(subY);
        return true;
    }

    private static boolean touchesEdge(@Nullable ConcurrentHashMap<Long, LevelChunk> loaded, int chunkX, int chunkZ,
                                       int subY, LevelChunkSection src, int xLocal, int yLocal, int zLocal,
                                       BitSet carveMask) {
        return checkNeighbor(loaded, chunkX, chunkZ, subY, src, xLocal - 1, yLocal, zLocal, carveMask)
                || checkNeighbor(loaded, chunkX, chunkZ, subY, src, xLocal + 1, yLocal, zLocal, carveMask)
                || checkNeighbor(loaded, chunkX, chunkZ, subY, src, xLocal, yLocal - 1, zLocal, carveMask)
                || checkNeighbor(loaded, chunkX, chunkZ, subY, src, xLocal, yLocal + 1, zLocal, carveMask)
                || checkNeighbor(loaded, chunkX, chunkZ, subY, src, xLocal, yLocal, zLocal - 1, carveMask)
                || checkNeighbor(loaded, chunkX, chunkZ, subY, src, xLocal, yLocal, zLocal + 1, carveMask);
    }

    private static boolean checkNeighbor(@Nullable ConcurrentHashMap<Long, LevelChunk> loaded, int chunkX, int chunkZ,
                                         int subY, LevelChunkSection src, int xLocal, int yLocal, int zLocal,
                                         BitSet carveMask) {
        if (xLocal >= 0 && xLocal < 16 && yLocal >= 0 && yLocal < 16 && zLocal >= 0 && zLocal < 16) {
            if (carveMask.get((yLocal << 8) | (zLocal << 4) | xLocal)) return false;
            return !src.getBlockState(xLocal, yLocal, zLocal).isAir();
        }
        int nCx = chunkX, nCz = chunkZ, nSubY = subY;
        int nx = xLocal, ny = yLocal, nz = zLocal;
        if (xLocal < 0) {
            nCx--;
            nx = 15;
        } else if (xLocal >= 16) {
            nCx++;
            nx = 0;
        }
        if (zLocal < 0) {
            nCz--;
            nz = 15;
        } else if (zLocal >= 16) {
            nCz++;
            nz = 0;
        }
        if (yLocal < 0) {
            nSubY--;
            ny = 15;
        } else if (yLocal >= 16) {
            nSubY++;
            ny = 0;
        }
        if (loaded == null) return true;
        LevelChunk neighbor = loaded.get(ChunkPos.asLong(nCx, nCz));
        if (neighbor == null) return true;
        LevelChunkSection[] nSections = neighbor.getSections();
        if (nSubY < 0 || nSubY >= nSections.length) return false;
        LevelChunkSection s = getSectionVolatile(nSections, nSubY);
        return s != null && !s.hasOnlyAir() && !s.getBlockState(nx, ny, nz).isAir();
    }

    public static boolean casSectionAt(@Nullable LevelChunkSection expect, @Nullable LevelChunkSection update,
                                       LevelChunkSection[] arr, int subY) {
        return SECTIONS.compareAndSet(arr, subY, expect, update);
    }

    public static @Nullable LevelChunkSection getSectionVolatile(LevelChunkSection[] arr, int subY) {
        return (LevelChunkSection) SECTIONS.getVolatile(arr, subY);
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (refCounter == 0 || !(event.getLevel() instanceof ServerLevel server)
                || !(event.getChunk() instanceof LevelChunk chunk)) return;
        ConcurrentHashMap<Long, LevelChunk> mirror = chunkMap.get(server.dimension());
        if (mirror != null) mirror.put(chunk.getPos().toLong(), chunk);
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (refCounter == 0 || !(event.getLevel() instanceof ServerLevel server)) return;
        ConcurrentHashMap<Long, LevelChunk> mirror = chunkMap.get(server.dimension());
        if (mirror != null) mirror.remove(event.getChunk().getPos().toLong());
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel server) BombForkJoinPool.onLevelUnload(server.dimension());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        BombForkJoinPool.onServerStopped();
        chunkMap.clear();
        activeTask.clear();
        BLAST_LOAD_CLAIMS.clear();
        refCounter = 0;
    }
}
