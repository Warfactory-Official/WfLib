package com.wf.wflib.aef.nuke;

import com.mojang.logging.LogUtils;
import com.wf.wflib.aef.nuke.util.BulkSectionUpdates;
import com.wf.wflib.aef.nuke.util.ConcurrentBitSet;
import com.wf.wflib.aef.nuke.util.MpscIntArrayListCollector;
import com.wf.wflib.aef.nuke.util.NukeChunks;
import com.wf.wflib.aef.nuke.util.SectionSnapshot;
import com.wf.wflib.mine.MineSectionWatch;
import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.SingleValuePalette;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.BitSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

// NTM-next port. Lock-free NTM collections (NonBlockingHashMapLong, Mpsc queues, off-heap masks, TLPool)
// -> JDK concurrent maps/queues, BitSet, plain allocation.
public class ExplosionNukeRayParallelized implements BombForkJoinPool.IJobCancellable {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static long nextCacheEpoch;
    private final long cacheEpoch = allocateCacheEpoch();

    private static synchronized long allocateCacheEpoch() {
        return ++nextCacheEpoch;
    }

    static final float NUKE_RESISTANCE_CUTOFF = 2_000_000F;
    static final float INITIAL_ENERGY_FACTOR = 0.3F;
    static final double RESOLUTION_FACTOR = 1.0;
    static final int LUT_RESISTANCE_BINS = 256;
    static final int LUT_DISTANCE_BINS = 256;
    static final float LUT_MAX_RESISTANCE = 100.0F;
    static final float[][] ENERGY_LOSS_LUT = new float[LUT_RESISTANCE_BINS][LUT_DISTANCE_BINS];
    static final float DAMAGE_PER_BLOCK = 0.50F;
    static final float DAMAGE_THRESHOLD_MULT = 1.00F;
    static final float LOW_R_BOUND = 0.25F;
    static final float LOW_R_PASS_LENGTH_BREAK = 0.75F;
    static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));

    static final double RAY_DIRECTION_EPSILON = 1e-6;
    static final double PROCESSING_EPSILON = 1e-9;
    static final float MIN_EFFECTIVE_DIST_FOR_ENERGY_CALC = 0.01f;

    static final int PAUSED_RAY_WORDS = 6;
    static final int MAX_CHUNK_COORD = SectionPos.blockToSectionCoord(Level.MAX_LEVEL_SIZE);

    static final int EMPTY_CACHE_SHIFT = 7;
    static final ThreadLocal<EmptySubMaskCache> TL_EMPTY_CACHE = ThreadLocal.withInitial(EmptySubMaskCache::new);
    static final int RES_CACHE_SHIFT = 7;
    static final ThreadLocal<DecodedSection[]> TL_RES_CACHE = ThreadLocal.withInitial(
            () -> new DecodedSection[1 << RES_CACHE_SHIFT]);

    static final ThreadLocal<BlockPos.MutableBlockPos> TL_POS = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);
    static final ThreadLocal<LocalAgg> TL_LOCAL_AGG = ThreadLocal.withInitial(LocalAgg::new);
    static final ThreadLocal<Long2ObjectOpenHashMap<BlockState>> TL_MODIFIED = ThreadLocal.withInitial(
            () -> new Long2ObjectOpenHashMap<>(16));
    static final ThreadLocal<LongOpenHashSet> TL_EDGES = ThreadLocal.withInitial(() -> new LongOpenHashSet(64));

    static {
        for (int r = 0; r < LUT_RESISTANCE_BINS; r++) {
            float resistance = (r / (float) (LUT_RESISTANCE_BINS - 1)) * LUT_MAX_RESISTANCE;
            for (int d = 0; d < LUT_DISTANCE_BINS; d++) {
                float distFrac = d / (float) (LUT_DISTANCE_BINS - 1);
                ENERGY_LOSS_LUT[r][d] = (float) (Math.pow(resistance + 1.0, 3.0 * distFrac) - 1.0);
            }
        }
    }

    final ServerLevel world;
    final double explosionX, explosionY, explosionZ;
    final double invRadius, invRayIndexScale;
    final int originX, originY, originZ;
    final int radius;
    final int strength;
    final int worldHeight;
    final int worldMinY;
    final int bitsetSize;
    final int subchunkPerChunk;
    final ConcurrentHashMap<Long, ConcurrentBitSet> destructionMap;
    final ConcurrentHashMap<Long, ChunkAgg> aggMap;
    final ConcurrentHashMap<Long, MpscIntArrayListCollector> waitingRoom;
    final ConcurrentHashMap<Long, ConcurrentLinkedQueue<ResumeItem>> postLoadQueues;
    final ConcurrentLinkedQueue<Long> chunkLoadQueue = new ConcurrentLinkedQueue<>();
    final Long2IntOpenHashMap sectionMaskByChunk;
    private final Long2ObjectOpenHashMap<CompletableFuture<?>> lightingByChunk = new Long2ObjectOpenHashMap<>();
    final int algorithm;
    final int rayCount;
    /** Server thread only. */
    private final Long2ObjectOpenHashMap<CompletableFuture<ChunkResult<LevelChunk>>> chunksInFlight =
            new Long2ObjectOpenHashMap<>();
    private final LongArrayList ticketsPendingFlush = new LongArrayList();
    // TODO: held whole until the blast ends (NTM-next same); release per chunk once carved + notified.
    private final LongOpenHashSet ticketedChunks = new LongOpenHashSet();
    private final LongOpenHashSet readinessLoads = new LongOpenHashSet();
    private boolean notificationTicketsPendingFlush;
    private int notificationAdmissionVersion;
    int @Nullable [] rayOrder;
    private volatile long @Nullable [] pausedRays;
    @Nullable ForkJoinPool pool;
    private @Nullable BulkSectionUpdates sectionUpdates;
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final AtomicInteger activeWorkerTasks = new AtomicInteger();
    private volatile long admissionDeadline;
    private final AtomicInteger admissionPumpQueued = new AtomicInteger();
    private volatile boolean cancelling;
    private boolean mainCleanupDone;
    private final AtomicInteger workerCleanupDone = new AtomicInteger();
    @Nullable ConcurrentHashMap<Long, LevelChunk> mirror;
    final AtomicInteger mapAcquired = new AtomicInteger();
    final AtomicInteger consolidationStarted = new AtomicInteger();
    final AtomicInteger finishQueued = new AtomicInteger();
    final AtomicInteger pendingRays = new AtomicInteger();
    final AtomicInteger pendingPostLoadWork = new AtomicInteger();
    final AtomicInteger collectFinished = new AtomicInteger();
    final AtomicInteger consolidationFinished = new AtomicInteger();
    final AtomicInteger destroyFinished = new AtomicInteger();
    final AtomicInteger poolAcquired = new AtomicInteger();
    final AtomicInteger jobRegistered = new AtomicInteger();

    public ExplosionNukeRayParallelized(ServerLevel world, double x, double y, double z, int strength, int radius,
                                        int algorithm) {
        this.world = world;
        this.algorithm = algorithm;
        explosionX = x;
        explosionY = y;
        explosionZ = z;
        originX = (int) Math.floor(x);
        originY = (int) Math.floor(y);
        originZ = (int) Math.floor(z);
        this.strength = strength;
        this.radius = radius;
        invRadius = radius > 0 ? 1.0 / radius : 0.0;

        this.worldMinY = world.getMinBuildHeight();
        this.worldHeight = (world.getHeight() + 15) & ~15;
        this.subchunkPerChunk = worldHeight >> 4;
        if (subchunkPerChunk > Integer.SIZE) {
            throw new IllegalStateException("Nuke section masks are 32 bits; level has " + subchunkPerChunk + " sections");
        }
        this.bitsetSize = 16 * worldHeight * 16;

        rayCount = Math.max(0, (int) (2.5 * Math.PI * strength * strength * RESOLUTION_FACTOR));
        invRayIndexScale = rayCount > 1 ? 1.0 / (rayCount - 1) : 0.0;
        pendingRays.set(rayCount);

        int estimatedChunkCount = Math.max(16, count(false));
        int chunkCap = capFor(estimatedChunkCount * 2);
        int subChunkCap = capFor(Math.max(16, count(true)));

        destructionMap = new ConcurrentHashMap<>(chunkCap);
        aggMap = new ConcurrentHashMap<>(chunkCap);
        waitingRoom = new ConcurrentHashMap<>(subChunkCap);
        postLoadQueues = new ConcurrentHashMap<>(subChunkCap);
        sectionMaskByChunk = new Long2IntOpenHashMap(estimatedChunkCount);
        sectionMaskByChunk.defaultReturnValue(0);

        initializeAndStartWorkers();
    }

    static int capFor(int n) {
        int c = 1;
        while (c < n) c <<= 1;
        return Math.max(16, c);
    }

    static int ceilPow2(int x) {
        if (x <= 1) return 1;
        int hb = Integer.highestOneBit(x - 1);
        int r = hb << 1;
        return r > 0 ? r : (1 << 30);
    }

    @SuppressWarnings("deprecation")
    static float getNukeResistance(BlockState state) {
        if (state.liquid()) return 0.1F;
        if (state.is(Blocks.SANDSTONE)) return Blocks.STONE.getExplosionResistance();
        if (state.is(Blocks.OBSIDIAN)) return Blocks.STONE.getExplosionResistance() * 3.0F;
        return state.getBlock().getExplosionResistance();
    }

    static double getEnergyLossFactor(float resistance, double distFrac) {
        if (resistance >= NUKE_RESISTANCE_CUTOFF) return resistance;
        if (resistance <= 0) return 0.0;
        if (resistance > LUT_MAX_RESISTANCE) return Math.pow(resistance + 1.0, 3.0 * distFrac) - 1.0;
        int rBin = (int) (resistance * (LUT_RESISTANCE_BINS - 1) / LUT_MAX_RESISTANCE);
        if (rBin == 0) return Math.pow(resistance + 1.0, 3.0 * distFrac) - 1.0;
        int dBin = (int) (distFrac * (LUT_DISTANCE_BINS - 1));
        return ENERGY_LOSS_LUT[rBin][dBin];
    }

    private static int rayKeyCubeMorton(double x, double y, double z) {
        double ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
        int face;
        double u, v, inv; //@formatter:off
        if (ax >= ay && ax >= az) {
            if (x >= 0) { face = 0; inv = 1.0 / ax; u = (-z * inv + 1.0) * 0.5; }
            else        { face = 1; inv = 1.0 / ax; u = ( z * inv + 1.0) * 0.5; }
            v = (-y * inv + 1.0) * 0.5;
        } else if (ay >= az) {
            if (y >= 0) { face = 2; inv = 1.0 / ay; u = ( x * inv + 1.0) * 0.5; v = ( z * inv + 1.0) * 0.5; }
            else        { face = 3; inv = 1.0 / ay; u = ( x * inv + 1.0) * 0.5; v = (-z * inv + 1.0) * 0.5; }
        } else {
            if (z >= 0) { face = 4; inv = 1.0 / az; u = ( x * inv + 1.0) * 0.5; }
            else        { face = 5; inv = 1.0 / az; u = (-x * inv + 1.0) * 0.5; }
            v = (-y * inv + 1.0) * 0.5;
        } //@formatter:on
        int uq = Math.clamp((int) (u * 1023.0), 0, 1023);
        int vq = Math.clamp((int) (v * 1023.0), 0, 1023);
        return (face << 20) | (part1By1_10(uq) << 1) | part1By1_10(vq);
    }

    private static int part1By1_10(int x) {
        x &= 0x3FF;
        x = (x | (x << 8)) & 0x00FF00FF;
        x = (x | (x << 4)) & 0x0F0F0F0F;
        x = (x | (x << 2)) & 0x33333333;
        x = (x | (x << 1)) & 0x55555555;
        return x;
    }

    static int emptySubMask(EmptySubMaskCache cache, LevelChunkSection[] sections) {
        int slot = (System.identityHashCode(sections) * 0x9E3775CD) >>> (32 - EMPTY_CACHE_SHIFT);
        if (cache.arrays[slot] == sections) return cache.masks[slot];
        int mask = 0;
        for (int i = 0; i < sections.length; i++) {
            if (sections[i].hasOnlyAir()) mask |= (1 << i);
        }
        cache.arrays[slot] = sections;
        cache.masks[slot] = mask;
        return mask;
    }

    static final class EmptySubMaskCache {
        long epoch;
        final LevelChunkSection[][] arrays = new LevelChunkSection[1 << EMPTY_CACHE_SHIFT][];
        final int[] masks = new int[1 << EMPTY_CACHE_SHIFT];
    }

    static DecodedSection resolveDecoded(DecodedSection[] cache, LevelChunkSection section) {
        int slot = (System.identityHashCode(section) * 0x9E3775CD) >>> (32 - RES_CACHE_SHIFT);
        DecodedSection ds = cache[slot];
        if (ds == null) {
            ds = new DecodedSection();
            cache[slot] = ds;
        }
        if (ds.section != section) ds.decode(section);
        return ds;
    }

    int count(boolean perSubchunk) {
        int cr = (radius + 15) >> 4;
        int minCX = (originX >> 4) - cr;
        int maxCX = (originX >> 4) + cr;
        int minCZ = (originZ >> 4) - cr;
        int maxCZ = (originZ >> 4) + cr;
        int minSubY = Math.max(0, ((originY - radius) - worldMinY) >> 4);
        int maxSubY = Math.min(subchunkPerChunk - 1, ((originY + radius) - worldMinY) >> 4);
        if (minSubY > maxSubY) return 0;
        double R2 = (radius + 14) * (radius + 14);
        int total = 0;
        for (int cx = minCX; cx <= maxCX; cx++) {
            double dx = ((cx << 4) + 8) - explosionX;
            double dx2 = dx * dx;
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                double dz = ((cz << 4) + 8) - explosionZ;
                double base = dx2 + dz * dz;
                if (base > R2) continue;
                double ry = Math.sqrt(R2 - base);
                int firstY = (int) Math.ceil(((explosionY - ry) - worldMinY - 8.0) / 16.0);
                int lastY = (int) Math.floor(((explosionY + ry) - worldMinY - 8.0) / 16.0);
                if (firstY < minSubY) firstY = minSubY;
                if (lastY > maxSubY) lastY = maxSubY;
                if (perSubchunk) {
                    int countY = lastY - firstY + 1;
                    if (countY > 0) total += countY;
                } else if (lastY >= firstY) total++;
            }
        }
        return total;
    }

    void initializeAndStartWorkers() {
        if (rayCount <= 0) {
            collectFinished.set(1);
            consolidationFinished.set(1);
            destroyFinished.set(1);
            return;
        }
        if (poolAcquired.compareAndSet(0, 1)) pool = BombForkJoinPool.acquire();
        if (jobRegistered.compareAndSet(0, 1)) BombForkJoinPool.register(pool, world.dimension(), this);
        mirror = NukeChunks.acquireMirrorMap(world);
        mapAcquired.set(1);
        ForkJoinPool p = pool;
        sectionUpdates = new BulkSectionUpdates(world, p, this::fail);
        submitWorker(p, () -> {
            if (destroyFinished.get() != 0) return;
            buildRayOrder();
            int minRayGrain = (rayOrder != null) ? 4096 : 512;
            new RayTracerTask(0, rayCount, computeTaskGrain(rayCount, minRayGrain)).invoke();
        });
    }

    private boolean submitWorker(ForkJoinPool executor, Runnable work) {
        activeWorkerTasks.incrementAndGet();
        if (cancelling) {
            workerFinished();
            return false;
        }
        try {
            executor.submit(() -> {
                try {
                    work.run();
                } catch (Throwable cause) {
                    fail(cause);
                } finally {
                    workerFinished();
                }
            });
            return true;
        } catch (RuntimeException | Error cause) {
            workerFinished();
            fail(cause);
            return false;
        }
    }

    private void workerFinished() {
        if (activeWorkerTasks.decrementAndGet() == 0) {
            if (cancelling) finishCancellation();
            else if (pendingRays.get() == 0) onAllRaysFinished();
        }
    }

    private void fail(Throwable cause) {
        if (failure.compareAndSet(null, cause)) {
            world.getServer().execute(() -> {
                LOGGER.error("Nuclear terrain work failed in {} at {}, {}, {}",
                        world.dimension().location(), originX, originY, originZ, cause);
                cancel();
            });
        }
    }

    void releasePoolIfHeld() {
        if (jobRegistered.getAndSet(0) != 0) BombForkJoinPool.unregister(pool, world.dimension(), this);
        if (poolAcquired.getAndSet(0) != 0) {
            BombForkJoinPool.release(pool);
            pool = null;
        }
    }

    private void buildRayOrder() {
        int n = rayCount;
        if (n < 200_000) return;
        long[] packed = new long[n];
        ForkJoinPool p = pool;
        int par = (p == null) ? 1 : p.getParallelism();
        if (par > 1 && n >= 512_000) {
            p.invoke(new BuildPackedTask(packed, 0, n, computeTaskGrain(n, 16384), invRayIndexScale));
        } else {
            for (int i = 0; i < n; i++) packed[i] = packedRayKey(i, invRayIndexScale);
        }
        if (n >= 1_000_000) LongArrays.parallelRadixSort(packed);
        else LongArrays.radixSort(packed);

        int[] order = new int[n];
        for (int i = 0; i < n; i++) order[i] = (int) packed[i];
        rayOrder = order;
    }

    private static long packedRayKey(int i, double invRayIndexScale) {
        double y = 1.0 - (i * invRayIndexScale) * 2.0;
        double r = Math.sqrt(Math.max(0.0, 1.0 - y * y));
        double t = GOLDEN_ANGLE * i;
        int key = rayKeyCubeMorton(Math.cos(t) * r, y, Math.sin(t) * r);
        return (((long) key) << 32) | (i & 0xFFFF_FFFFL);
    }

    private int computeTaskGrain(int totalRays, int minGrain) {
        int p = pool == null ? 1 : pool.getParallelism();
        int targetTasks = Math.max(1, p << 2);
        int batch = ceilPow2((totalRays + targetTasks - 1) / targetTasks);
        if (batch < minGrain) batch = minGrain;
        if (batch > totalRays && totalRays > 0) batch = totalRays;
        return batch;
    }

    private int computeKeyTaskGrain(int totalKeys) {
        return computeTaskGrain(totalKeys, 64);
    }

    void onAllRaysFinished() {
        // A resumed ray can finish before its earlier worker has merged the prefix contributions.
        if (activeWorkerTasks.get() != 0 || collectFinished.get() != 0 || destroyFinished.get() != 0
                || failure.get() != null) return;
        collectFinished.set(1);
        rayOrder = null;
        pausedRays = null;
        if (consolidationStarted.compareAndSet(0, 1)) {
            ForkJoinPool p = pool;
            if (p != null && !p.isShutdown()) submitWorker(p, this::runConsolidation);
            else runConsolidation();
        }
    }

    void queueChunkLoad(long chunkPos) {
        chunkLoadQueue.offer(chunkPos);
        requestChunkPump();
    }

    private void requestChunkPump() {
        if (destroyFinished.get() != 0 || failure.get() != null || System.nanoTime() >= admissionDeadline) return;
        if (!admissionPumpQueued.compareAndSet(0, 1)) return;
        MinecraftServer server = world.getServer();
        // execute() would run inline on the server thread and reenter the request iterator.
        server.tell(new TickTask(server.getTickCount(), this::pumpChunkAdmissions));
    }

    private void pumpChunkAdmissions() {
        admissionPumpQueued.set(0);
        long deadline = admissionDeadline;
        if (destroyFinished.get() != 0 || failure.get() != null || System.nanoTime() >= deadline) return;
        try {
            loadMissingChunksUntil(deadline);
        } catch (Throwable cause) {
            fail(cause);
        }
    }

    void trackChunkFuture(long chunkPos, CompletableFuture<ChunkResult<LevelChunk>> future) {
        chunksInFlight.put(chunkPos, future);
        if (!future.isDone()) future.whenComplete((result, cause) -> requestChunkPump());
    }

    void loadMissingChunksUntil(long deadline) {
        while (System.nanoTime() < deadline
                && chunksInFlight.size() + ticketsPendingFlush.size() < NukeConfig.chunksInFlight()) {
            Long polled = chunkLoadQueue.poll();
            if (polled == null) break;
            long ck = polled;
            if (chunksInFlight.containsKey(ck) || ticketsPendingFlush.contains(ck)) continue;
            if (ticketedChunks.add(ck)) NukeChunks.addBlastTicket(world, ck);
            ticketsPendingFlush.add(ck);
        }
        if ((!ticketsPendingFlush.isEmpty() || notificationTicketsPendingFlush) && System.nanoTime() < deadline) {
            NukeChunks.flushChunkTickets(world);
            notificationTicketsPendingFlush = false;
            for (int i = 0; i < ticketsPendingFlush.size(); i++) {
                long ck = ticketsPendingFlush.getLong(i);
                CompletableFuture<ChunkResult<LevelChunk>> future = NukeChunks.blastChunkFuture(world, ck);
                // No holder after flush: ticket lost; re-queue.
                if (future != null) trackChunkFuture(ck, future);
                else queueChunkLoad(ck);
            }
            ticketsPendingFlush.clear();
        }
        harvestLoadedChunks(deadline);
    }

    private void harvestLoadedChunks(long deadlineNanos) {
        if (chunksInFlight.isEmpty()) return;
        var it = chunksInFlight.long2ObjectEntrySet().fastIterator();
        while (it.hasNext() && System.nanoTime() < deadlineNanos) {
            var entry = it.next();
            CompletableFuture<ChunkResult<LevelChunk>> future = entry.getValue();
            if (!future.isDone()) continue;
            long ck = entry.getLongKey();
            it.remove();
            LevelChunk chunk = future.join().orElse(null);
            if (chunk != null) chunk = NukeChunks.liveChunkNow(world, ck);
            if (chunk == null) {
                queueChunkLoad(ck);
                continue;
            }
            processChunkLoadRequest(ck, chunk);
            // Completion can clear chunksInFlight from the request callback.
            if (destroyFinished.get() != 0 || failure.get() != null || System.nanoTime() >= deadlineNanos) return;
        }
    }

    void releaseChunkTickets() {
        for (long ck : ticketedChunks) NukeChunks.releaseChunkAsync(world, ck);
        ticketedChunks.clear();
        ticketsPendingFlush.clear();
        readinessLoads.clear();
        notificationTicketsPendingFlush = false;
        chunksInFlight.clear();
    }

    void processChunkLoadRequest(long chunkPos, LevelChunk chunk) {
        readinessLoads.remove(chunkPos);
        // Load event fires only at EMPTY->FULL.
        if (mirror != null) mirror.put(chunkPos, chunk);

        ForkJoinPool p = pool;
        boolean poolActive = (p != null && !p.isShutdown());
        MpscIntArrayListCollector waiters = waitingRoom.remove(chunkPos);
        if (waiters != null) {
            if (!poolActive) {
                ensureCollectorRegistered(chunkPos, waiters);
                queueChunkLoad(chunkPos);
            } else {
                IntArrayList batch = waiters.drain();
                if (!batch.isEmpty()) {
                    submitWorker(p, () -> new ResumeBatchTask(batch, 0, batch.size(),
                            computeTaskGrain(batch.size(), rayOrder == null ? 512 : 4096)).invoke());
                }
            }
        }
        // Keep completion blocked across the remove-to-submit gap.
        pendingPostLoadWork.incrementAndGet();
        try {
            ConcurrentLinkedQueue<ResumeItem> q = postLoadQueues.remove(chunkPos);
            if (q != null) {
                if (!poolActive) {
                    ConcurrentLinkedQueue<ResumeItem> prev = postLoadQueues.putIfAbsent(chunkPos, q);
                    if (prev != null && prev != q) {
                        ResumeItem it;
                        while ((it = q.poll()) != null) prev.offer(it);
                    }
                    queueChunkLoad(chunkPos);
                    return;
                }
                ResumeItem item;
                while ((item = q.poll()) != null) {
                    if (item.agg != null) {
                        ChunkAgg agg = item.agg;
                        submitPostLoadWork(p, () -> applyAggregate(chunkPos, agg));
                    } else {
                        Int2ObjectOpenHashMap<BitSet> masks = item.masks;
                        submitPostLoadWork(p, () -> applyCarve(chunkPos, masks));
                    }
                }
            }
        } finally {
            finishPostLoadWork();
        }
    }

    void submitPostLoadWork(ForkJoinPool p, Runnable work) {
        pendingPostLoadWork.incrementAndGet();
        boolean accepted = submitWorker(p, () -> {
            try {
                work.run();
            } finally {
                finishPostLoadWork();
            }
        });
        if (!accepted) finishPostLoadWork();
    }

    void finishPostLoadWork() {
        if (pendingPostLoadWork.decrementAndGet() == 0) maybeFinish();
    }

    void secondPass() {
        if (sectionMaskByChunk.isEmpty()) return;
        var iterator = sectionMaskByChunk.long2IntEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Long2IntMap.Entry e = iterator.next();
            long packed = e.getLongKey();
            LevelChunk chunk = world.getChunkSource().getChunkNow(ChunkPos.getX(packed), ChunkPos.getZ(packed));
            if (chunk == null) continue;
            CompletableFuture<?> lighting = NukeChunks.updateLight(world, chunk, LongList.of(), e.getIntValue());
            CompletableFuture<?> carved = lightingByChunk.remove(packed);
            if (carved != null && carved != lighting) lighting = CompletableFuture.allOf(carved, lighting);
            NukeChunks.broadcastWholesaleResend(world, packed, lighting);
        }
        sectionMaskByChunk.clear();
        lightingByChunk.clear();
    }

    public boolean isComplete() {
        return failure.get() == null && collectFinished.get() != 0 && consolidationFinished.get() != 0
                && destroyFinished.get() != 0;
    }

    public boolean hasFailed() {
        return failure.get() != null;
    }

    Int2ObjectOpenHashMap<BitSet> splitBySubchunk(ConcurrentBitSet chunkMask) {
        Int2ObjectOpenHashMap<BitSet> m = new Int2ObjectOpenHashMap<>();
        int bit = chunkMask.nextSetBit(0);
        while (bit >= 0) {
            int yGlobal = worldHeight - 1 - (bit >>> 8);
            m.computeIfAbsent(yGlobal >>> 4, k -> new BitSet(4096)).set(localIndex(bit, yGlobal));
            bit = chunkMask.nextSetBit(bit + 1);
        }
        return m;
    }

    private static int localIndex(int bitIndex, int yGlobal) {
        return ((yGlobal & 0xF) << 8) | ((bitIndex & 0xF) << 4) | ((bitIndex >>> 4) & 0xF);
    }

    Int2ObjectOpenHashMap<BitSet> buildMasksFromAgg(ChunkAgg agg, LevelChunkSection[] sections) {
        Int2ObjectOpenHashMap<BitSet> sub = new Int2ObjectOpenHashMap<>();
        Int2DoubleOpenHashMap dmg = agg.damage;
        Int2DoubleOpenHashMap len = agg.passLen;

        ObjectIterator<Int2DoubleMap.Entry> it = dmg.int2DoubleEntrySet().fastIterator();
        while (it.hasNext()) {
            Int2DoubleMap.Entry e = it.next();
            int bitIndex = e.getIntKey();
            if (shouldDestroy(bitIndex, sections, e.getDoubleValue(), len.get(bitIndex))) {
                int yGlobal = worldHeight - 1 - (bitIndex >>> 8);
                sub.computeIfAbsent(yGlobal >>> 4, k -> new BitSet(4096)).set(localIndex(bitIndex, yGlobal));
            }
        }
        it = len.int2DoubleEntrySet().fastIterator();
        while (it.hasNext()) {
            Int2DoubleMap.Entry e = it.next();
            int bitIndex = e.getIntKey();
            if (dmg.containsKey(bitIndex)) continue;
            if (shouldDestroy(bitIndex, sections, 0.0, e.getDoubleValue())) {
                int yGlobal = worldHeight - 1 - (bitIndex >>> 8);
                sub.computeIfAbsent(yGlobal >>> 4, k -> new BitSet(4096)).set(localIndex(bitIndex, yGlobal));
            }
        }
        return sub;
    }

    boolean shouldDestroy(int bitIndex, LevelChunkSection[] sections, double accumulatedDamage, double passLen) {
        int yGlobal = worldHeight - 1 - (bitIndex >>> 8);
        int subY = yGlobal >>> 4;
        if (subY < 0 || subY >= sections.length) return false;
        LevelChunkSection s = sections[subY];
        if (s.hasOnlyAir()) return false;
        BlockState st = s.getBlockState((bitIndex >>> 4) & 0xF, yGlobal & 0xF, bitIndex & 0xF);
        if (st.isAir()) return false;
        float resistance = getNukeResistance(st);
        if (accumulatedDamage >= (resistance * DAMAGE_THRESHOLD_MULT)) return true;
        return resistance <= LOW_R_BOUND && passLen >= LOW_R_PASS_LENGTH_BREAK;
    }

    /** Server thread. */
    public void update(long msBudget) {
        if (destroyFinished.get() != 0 || failure.get() != null) return;
        long deadline = System.nanoTime() + msBudget * 1_000_000L;
        admissionDeadline = deadline;
        int admittedVersion;
        do {
            if (sectionUpdates != null) sectionUpdates.drain(deadline);
            if (destroyFinished.get() != 0 || failure.get() != null) return;
            loadMissingChunksUntil(deadline);
            if (destroyFinished.get() != 0 || failure.get() != null) return;
            admittedVersion = notificationAdmissionVersion;
            if (sectionUpdates != null) sectionUpdates.drain(deadline);
        } while (notificationAdmissionVersion != admittedVersion && System.nanoTime() < deadline);
        maybeFinish();
    }

    /** Server thread. */
    public void cancel() {
        cancelling = true;
        collectFinished.set(1);
        consolidationFinished.set(1);
        destroyFinished.set(1);
        if (sectionUpdates != null) sectionUpdates.close();
        if (!mainCleanupDone) {
            mainCleanupDone = true;
            try {
                // Cancellation rejects new work, but accepted terrain still needs its client update.
                secondPass();
            } finally {
                sectionMaskByChunk.clear();
                lightingByChunk.clear();
                releasePoolIfHeld();
                if (mapAcquired.getAndSet(0) != 0) NukeChunks.releaseMirrorMap(world);
                releaseChunkTickets();
            }
        }
        if (activeWorkerTasks.get() == 0) finishCancellation();
    }

    private void finishCancellation() {
        // The last worker can release these after server shutdown, when its task queue no longer runs.
        if (!workerCleanupDone.compareAndSet(0, 1)) return;
        waitingRoom.clear();
        postLoadQueues.clear();
        rayOrder = null;
        pausedRays = null;
        destructionMap.clear();
        aggMap.clear();
        chunkLoadQueue.clear();
    }

    @Override
    public void cancelJob() {
        cancel();
    }

    void runConsolidation() {
        if (algorithm == 2) {
            long[] keys = aggMap.keySet().stream().mapToLong(Long::longValue).toArray();
            ChunkAgg[] aggregates = new ChunkAgg[keys.length];
            for (int i = 0; i < keys.length; i++) aggregates[i] = aggMap.remove(keys[i]);
            if (keys.length != 0) {
                new ConsolidateAggTask(keys, aggregates, 0, keys.length, computeKeyTaskGrain(keys.length)).invoke();
            }
        } else {
            long[] keys = destructionMap.keySet().stream().mapToLong(Long::longValue).toArray();
            if (keys.length != 0) {
                new ConsolidateMaskTask(keys, 0, keys.length, computeKeyTaskGrain(keys.length)).invoke();
            }
        }
        consolidationFinished.set(1);
        maybeFinish();
    }

    void maybeFinish() {
        if (collectFinished.get() == 0 || consolidationFinished.get() == 0 || destroyFinished.get() != 0) return;
        if (sectionUpdates != null && sectionUpdates.hasPending()) return;
        if (pendingPostLoadWork.get() != 0) return;
        if (!waitingRoom.isEmpty()) return;
        if (!postLoadQueues.isEmpty()) return;
        if (!finishQueued.compareAndSet(0, 1)) return;
        world.getServer().execute(() -> {
            if (destroyFinished.get() != 0 || failure.get() != null) return;
            secondPass();
            destroyFinished.set(1);
            if (sectionUpdates != null) sectionUpdates.close();
            releasePoolIfHeld();
            if (mapAcquired.getAndSet(0) != 0) NukeChunks.releaseMirrorMap(world);
            releaseChunkTickets();
        });
    }

    void applyCarve(long chunkPos, Int2ObjectOpenHashMap<BitSet> masks) {
        if (masks.isEmpty()) return;
        sectionUpdates.submit(new NukeCarveWork(chunkPos, masks));
    }

    void applyAggregate(long chunkPos, ChunkAgg aggregate) {
        sectionUpdates.submit(new NukeCarveWork(chunkPos, aggregate));
    }

    private final class NukeCarveWork extends BulkSectionUpdates.Work {
        private final long chunkPos;
        private final @Nullable ChunkAgg aggregate;
        private @Nullable Int2ObjectOpenHashMap<BitSet> masks;
        private final int observed;
        private final Long2ObjectOpenHashMap<BlockState> modified = new Long2ObjectOpenHashMap<>();
        private final LongArrayList neighbors = new LongArrayList();
        private final Long2IntOpenHashMap neighborMask = new Long2IntOpenHashMap();
        private int selfMask;
        private boolean transferred;

        NukeCarveWork(long chunkPos, Int2ObjectOpenHashMap<BitSet> masks) {
            super(chunkPos);
            this.chunkPos = chunkPos;
            this.aggregate = null;
            this.masks = masks;
            int observed = 0;
            for (var iterator = masks.keySet().iterator(); iterator.hasNext(); ) observed |= 1 << iterator.nextInt();
            this.observed = observed;
        }

        NukeCarveWork(long chunkPos, ChunkAgg aggregate) {
            super(chunkPos);
            this.chunkPos = chunkPos;
            this.aggregate = aggregate;
            int observed = 0;
            for (var iterator = aggregate.damage.keySet().iterator(); iterator.hasNext(); ) {
                observed |= 1 << ((worldHeight - 1 - (iterator.nextInt() >>> 8)) >>> 4);
            }
            for (var iterator = aggregate.passLen.keySet().iterator(); iterator.hasNext(); ) {
                observed |= 1 << ((worldHeight - 1 - (iterator.nextInt() >>> 8)) >>> 4);
            }
            this.observed = observed;
        }

        @Override
        protected int observedSections() {
            return observed;
        }

        @Override
        protected void calculate(SectionSnapshot snapshot) {
            if (aggregate != null) masks = buildMasksFromAgg(aggregate, snapshot.sections);
            int cx = ChunkPos.getX(chunkPos);
            int cz = ChunkPos.getZ(chunkPos);
            Long2ObjectOpenHashMap<BlockState> local = TL_MODIFIED.get();
            LongOpenHashSet edges = TL_EDGES.get();
            for (var iterator = masks.int2ObjectEntrySet().fastIterator(); iterator.hasNext(); ) {
                var entry = iterator.next();
                int subY = entry.getIntKey();
                local.clear();
                edges.clear();
                boolean carved = NukeChunks.carveSnapshot(world, cx, cz, subY, snapshot, entry.getValue(), edges, local);
                if (carved || !local.isEmpty() || snapshot.sections[subY].hasOnlyAir()) selfMask |= 1 << subY;
                modified.putAll(local);
                for (LongIterator it = edges.iterator(); it.hasNext(); ) {
                    long pos = it.nextLong();
                    neighbors.add(pos);
                    long neighbor = ChunkPos.asLong(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4);
                    neighborMask.put(neighbor,
                            neighborMask.get(neighbor) | (1 << ((BlockPos.getY(pos) - worldMinY) >>> 4)));
                }
            }
        }

        @Override
        protected void published(LevelChunk chunk) {
            if (selfMask != 0) chunkFixup(chunkPos, modified, neighbors, selfMask, neighborMask);
        }

        @Override
        protected boolean readyToPublish(LevelChunk chunk) {
            return (modified.isEmpty() && neighbors.isEmpty()) || notificationNeighborhoodReady(chunkPos);
        }

        @Override
        protected void missing() {
            if (aggregate != null) enqueueForMissingChunk(chunkPos, new ResumeItem(null, aggregate));
            else enqueueForMissingChunk(chunkPos, new ResumeItem(masks, null));
            transferred = true;
        }

        @Override
        protected void clearAttempt() {
            modified.clear();
            neighbors.clear();
            neighborMask.clear();
            selfMask = 0;
            if (aggregate != null) masks = null;
        }

        @Override
        protected void discard() {
            clearAttempt();
            if (!transferred && aggregate != null) aggregate.clear();
        }
    }

    boolean notificationNeighborhoodReady(long chunkPos) {
        int cx = ChunkPos.getX(chunkPos);
        int cz = ChunkPos.getZ(chunkPos);
        boolean ready = true;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int nx = cx + dx, nz = cz + dz;
                if (Math.abs(nx) > MAX_CHUNK_COORD || Math.abs(nz) > MAX_CHUNK_COORD) continue;
                long neighbor = ChunkPos.asLong(nx, nz);
                if (NukeChunks.liveChunkNow(world, neighbor) == null) {
                    ready = false;
                    if (!chunksInFlight.containsKey(neighbor) && readinessLoads.add(neighbor)) {
                        queueChunkLoad(neighbor);
                        notificationAdmissionVersion++;
                    }
                } else if (ticketedChunks.add(neighbor)) {
                    NukeChunks.addBlastTicket(world, neighbor);
                    notificationTicketsPendingFlush = true;
                    notificationAdmissionVersion++;
                }
            }
        }
        return ready && !notificationTicketsPendingFlush;
    }

    void chunkFixup(long cpLong, Long2ObjectOpenHashMap<BlockState> modified, LongArrayList neighborNotifies,
                    int selfMask, Long2IntOpenHashMap neighborMask) {
        sectionMaskByChunk.put(cpLong, sectionMaskByChunk.get(cpLong) | selfMask);
        var iterator = neighborMask.long2IntEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Long2IntMap.Entry e = iterator.next();
            sectionMaskByChunk.put(e.getLongKey(), sectionMaskByChunk.get(e.getLongKey()) | e.getIntValue());
        }
        BlockPos.MutableBlockPos p = TL_POS.get();
        BlockState air = Blocks.AIR.defaultBlockState();
        LongArrayList lightChanges = new LongArrayList();
        LevelChunk selfChunk = world.getChunkSource().getChunkNow(ChunkPos.getX(cpLong), ChunkPos.getZ(cpLong));
        if (selfChunk != null) {
            var modIter = modified.long2ObjectEntrySet().fastIterator();
            while (modIter.hasNext()) {
                Long2ObjectMap.Entry<BlockState> entry = modIter.next();
                long lp = entry.getLongKey();
                BlockState oldState = entry.getValue();
                p.set(BlockPos.getX(lp), BlockPos.getY(lp), BlockPos.getZ(lp));
                if (oldState.hasBlockEntity()) {
                    world.setBlock(p.immutable(), air, Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
                } else if (NukeChunks.postCarveBlockUpdate(world, selfChunk, p, oldState, air)) {
                    lightChanges.add(lp);
                }
            }
            // Section replacement bypasses LevelChunk.setBlockState => MixinLevelChunk never sees it.
            if (MineSectionWatch.isWatching()) {
                for (int mask = selfMask; mask != 0; mask &= mask - 1) {
                    p.set(ChunkPos.getX(cpLong) << 4, worldMinY + (Integer.numberOfTrailingZeros(mask) << 4),
                            ChunkPos.getZ(cpLong) << 4);
                    MineSectionWatch.onBlockChanged(world, p);
                }
            }
        }
        for (int i = 0, n = neighborNotifies.size(); i < n; i++) {
            long lp = neighborNotifies.getLong(i);
            p.set(BlockPos.getX(lp), BlockPos.getY(lp), BlockPos.getZ(lp));
            world.updateNeighborsAt(p, air.getBlock());
            air.updateNeighbourShapes(world, p, Block.UPDATE_CLIENTS);
        }
        if (selfChunk != null) {
            CompletableFuture<?> lighting = NukeChunks.updateLight(world, selfChunk, lightChanges, selfMask);
            CompletableFuture<?> earlier = lightingByChunk.put(cpLong, lighting);
            if (earlier != null && earlier != lighting) {
                lightingByChunk.put(cpLong, CompletableFuture.allOf(earlier, lighting));
            }
        }
    }

    void handleMissingChunk(LocalAgg agg, long chunkPos, int dirIndex) {
        MpscIntArrayListCollector group = waitingRoom.get(chunkPos);
        if (group == null) {
            MpscIntArrayListCollector created = new MpscIntArrayListCollector();
            MpscIntArrayListCollector prev = waitingRoom.putIfAbsent(chunkPos, created);
            if (prev == null) {
                created.push(dirIndex);
                ensureCollectorRegistered(chunkPos, created);
                queueChunkLoad(chunkPos);
                return;
            }
        }
        agg.deferMissing(chunkPos, dirIndex);
    }

    void flushDeferredMissing(LocalAgg agg) {
        if (agg.deferredMissing.isEmpty()) return;
        var iterator = agg.deferredMissing.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Long2ObjectMap.Entry<IntArrayList> entry = iterator.next();
            IntArrayList list = entry.getValue();
            if (list.isEmpty()) continue;
            long chunkPos = entry.getLongKey();
            MpscIntArrayListCollector collector = waitingRoom.get(chunkPos);
            boolean amFirst = false;
            if (collector == null) {
                MpscIntArrayListCollector created = new MpscIntArrayListCollector();
                MpscIntArrayListCollector prev = waitingRoom.putIfAbsent(chunkPos, created);
                if (prev == null) {
                    collector = created;
                    amFirst = true;
                } else collector = prev;
            }
            collector.pushBatch(list);
            boolean requeued = ensureCollectorRegistered(chunkPos, collector);
            if (amFirst || requeued) queueChunkLoad(chunkPos);
        }
        agg.deferredMissing.clear();
    }

    boolean ensureCollectorRegistered(long chunkPos, MpscIntArrayListCollector collector) {
        while (true) {
            MpscIntArrayListCollector current = waitingRoom.get(chunkPos);
            if (current == collector) return false;
            if (current == null) {
                if (waitingRoom.putIfAbsent(chunkPos, collector) == null) return true;
                continue;
            }
            IntArrayList drained = collector.drain();
            for (int i = drained.size() - 1; i >= 0; i--) current.push(drained.getInt(i));
            collector = current;
        }
    }

    void enqueueForMissingChunk(long chunkPos, ResumeItem item) {
        ConcurrentLinkedQueue<ResumeItem> q = postLoadQueues.get(chunkPos);
        if (q == null) {
            ConcurrentLinkedQueue<ResumeItem> created = new ConcurrentLinkedQueue<>();
            created.offer(item);
            ConcurrentLinkedQueue<ResumeItem> prev = postLoadQueues.putIfAbsent(chunkPos, created);
            if (prev == null) {
                queueChunkLoad(chunkPos);
                return;
            }
            q = created;
        } else {
            q.offer(item);
        }
        if (ensurePostLoadQueueRegistered(chunkPos, q)) queueChunkLoad(chunkPos);
    }

    boolean ensurePostLoadQueueRegistered(long chunkPos, ConcurrentLinkedQueue<ResumeItem> queue) {
        while (true) {
            ConcurrentLinkedQueue<ResumeItem> current = postLoadQueues.get(chunkPos);
            if (current == queue) return false;
            if (current == null) {
                if (postLoadQueues.putIfAbsent(chunkPos, queue) == null) return true;
                continue;
            }
            ResumeItem item;
            while ((item = queue.poll()) != null) current.offer(item);
            queue = current;
        }
    }

    private long[] pausedRays() {
        long[] states = pausedRays;
        if (states != null) return states;
        synchronized (this) {
            if (pausedRays == null) pausedRays = new long[Math.multiplyExact(rayCount, PAUSED_RAY_WORDS)];
            return pausedRays;
        }
    }

    private void pauseRay(int index, int x, int y, int z, double energy, double position,
                          double maxX, double maxY, double maxZ) {
        long[] states = pausedRays();
        int offset = index * PAUSED_RAY_WORDS;
        states[offset] = Double.doubleToRawLongBits(energy);
        states[offset + 1] = BlockPos.asLong(x, y, z);
        states[offset + 2] = Double.doubleToRawLongBits(position);
        states[offset + 3] = Double.doubleToRawLongBits(maxX);
        states[offset + 4] = Double.doubleToRawLongBits(maxY);
        states[offset + 5] = Double.doubleToRawLongBits(maxZ);
    }

    boolean traceSingle(int dirIndex, LocalAgg agg) {
        double energy = strength * INITIAL_ENERGY_FACTOR;
        double px = explosionX, py = explosionY, pz = explosionZ;
        int x = originX, y = originY, z = originZ;
        double dirY = 1.0 - (dirIndex * invRayIndexScale) * 2.0;
        double r = Math.sqrt(Math.max(0.0, 1.0 - dirY * dirY));
        double t = GOLDEN_ANGLE * dirIndex;
        double dirX = Math.cos(t) * r;
        double dirZ = Math.sin(t) * r;
        double currentRayPosition = 0.0;
        double absDirX = Math.abs(dirX);
        int stepX = (absDirX < RAY_DIRECTION_EPSILON) ? 0 : (dirX > 0 ? 1 : -1);
        double tDeltaX = (stepX == 0) ? Double.POSITIVE_INFINITY : 1.0 / absDirX;

        double absDirY = Math.abs(dirY);
        int stepY = (absDirY < RAY_DIRECTION_EPSILON) ? 0 : (dirY > 0 ? 1 : -1);
        double tDeltaY = (stepY == 0) ? Double.POSITIVE_INFINITY : 1.0 / absDirY;

        double absDirZ = Math.abs(dirZ);
        int stepZ = (absDirZ < RAY_DIRECTION_EPSILON) ? 0 : (dirZ > 0 ? 1 : -1);
        double tDeltaZ = (stepZ == 0) ? Double.POSITIVE_INFINITY : 1.0 / absDirZ;

        double minDeltaT = Math.min(tDeltaX, Math.min(tDeltaY, tDeltaZ));
        double radiusLimit = radius - PROCESSING_EPSILON;
        boolean useAggDamage = (algorithm == 2);

        double tMaxX = (stepX == 0) ? Double.POSITIVE_INFINITY : ((stepX > 0 ? (x + 1 - px) : (px - x)) * tDeltaX);
        double tMaxY = (stepY == 0) ? Double.POSITIVE_INFINITY : ((stepY > 0 ? (y + 1 - py) : (py - y)) * tDeltaY);
        double tMaxZ = (stepZ == 0) ? Double.POSITIVE_INFINITY : ((stepZ > 0 ? (z + 1 - pz) : (pz - z)) * tDeltaZ);

        long[] states = pausedRays;
        int stateOffset = dirIndex * PAUSED_RAY_WORDS;
        // Positive energy marks a saved continuation; the waiting-room handoff publishes all six words.
        if (states != null && states[stateOffset] != 0) {
            energy = Double.longBitsToDouble(states[stateOffset]);
            long packed = states[stateOffset + 1];
            x = BlockPos.getX(packed);
            y = BlockPos.getY(packed);
            z = BlockPos.getZ(packed);
            currentRayPosition = Double.longBitsToDouble(states[stateOffset + 2]);
            tMaxX = Double.longBitsToDouble(states[stateOffset + 3]);
            tMaxY = Double.longBitsToDouble(states[stateOffset + 4]);
            tMaxZ = Double.longBitsToDouble(states[stateOffset + 5]);
            states[stateOffset] = 0;
        }

        long cachedCPLong = 0L;
        LevelChunkSection[] sections = null;
        int lastCX = Integer.MIN_VALUE, lastCZ = Integer.MIN_VALUE;
        int chunkMinX = 0, chunkMaxX = 0, chunkMinZ = 0, chunkMaxZ = 0;
        int emptySubMask = 0;
        long bitsetCPLong = Long.MIN_VALUE;
        ConcurrentBitSet currentBits = null;
        int loopCount = 0;
        int maxY = worldMinY + worldHeight;
        DecodedSection[] resCache = TL_RES_CACHE.get();
        EmptySubMaskCache emptyCache = TL_EMPTY_CACHE.get();
        prepareRayCaches(resCache, emptyCache);
        LevelChunkSection lastStorage = null;
        DecodedSection decoded = null;

        try {
            if (energy <= 0) return true;
            while (energy > 0) {
                if ((loopCount++ & 0x3FF) == 0) {
                    if (destroyFinished.get() != 0 || y < worldMinY || y >= maxY || Thread.currentThread().isInterrupted())
                        break;
                    if (currentRayPosition >= radiusLimit) break;
                } else {
                    if (currentRayPosition >= radiusLimit) break;
                    if (y < worldMinY || y >= maxY) break;
                }
                int cx = x >> 4;
                int cz = z >> 4;

                if (cx != lastCX || cz != lastCZ) {
                    cachedCPLong = ChunkPos.asLong(cx, cz);
                    sections = NukeChunks.getLoadedSections(mirror, cachedCPLong);
                    if (sections == null) {
                        pauseRay(dirIndex, x, y, z, energy, currentRayPosition, tMaxX, tMaxY, tMaxZ);
                        handleMissingChunk(agg, cachedCPLong, dirIndex);
                        return false;
                    }
                    lastCX = cx;
                    lastCZ = cz;
                    chunkMinX = cx << 4;
                    chunkMaxX = chunkMinX + 16;
                    chunkMinZ = cz << 4;
                    chunkMaxZ = chunkMinZ + 16;
                    emptySubMask = emptySubMask(emptyCache, sections);
                    if (!useAggDamage && bitsetCPLong != cachedCPLong) {
                        currentBits = getOrCreateDestructionBitSet(cachedCPLong);
                        bitsetCPLong = cachedCPLong;
                    }
                }

                int subY = (y - worldMinY) >> 4;
                if (subY < 0 || subY >= sections.length) break;
                if (((emptySubMask >>> subY) & 1) != 0) {
                    double minY = (subY << 4) + worldMinY;
                    double maxYd = minY + 16.0;

                    double tExitAbs = Double.POSITIVE_INFINITY;
                    if (stepX > 0) tExitAbs = Math.min(tExitAbs, (chunkMaxX - px) * tDeltaX);
                    else if (stepX < 0) tExitAbs = Math.min(tExitAbs, (px - chunkMinX) * tDeltaX);
                    if (stepY > 0) tExitAbs = Math.min(tExitAbs, (maxYd - py) * tDeltaY);
                    else if (stepY < 0) tExitAbs = Math.min(tExitAbs, (py - minY) * tDeltaY);
                    if (stepZ > 0) tExitAbs = Math.min(tExitAbs, (chunkMaxZ - pz) * tDeltaZ);
                    else if (stepZ < 0) tExitAbs = Math.min(tExitAbs, (pz - chunkMinZ) * tDeltaZ);

                    double deltaT = Math.min(tExitAbs, radius) - currentRayPosition;
                    if (deltaT <= PROCESSING_EPSILON) deltaT = minDeltaT;
                    currentRayPosition += deltaT;
                    if (currentRayPosition >= radiusLimit) break;

                    final double bias = 1e-9;
                    x = (int) Math.floor(px + dirX * (currentRayPosition - bias));
                    y = (int) Math.floor(py + dirY * (currentRayPosition - bias));
                    z = (int) Math.floor(pz + dirZ * (currentRayPosition - bias));

                    tMaxX = (stepX == 0) ? Double.POSITIVE_INFINITY : ((stepX > 0 ? (x + 1 - px) : (px - x)) * tDeltaX);
                    tMaxY = (stepY == 0) ? Double.POSITIVE_INFINITY : ((stepY > 0 ? (y + 1 - py) : (py - y)) * tDeltaY);
                    tMaxZ = (stepZ == 0) ? Double.POSITIVE_INFINITY : ((stepZ > 0 ? (z + 1 - pz) : (pz - z)) * tDeltaZ);
                    continue;
                }
                LevelChunkSection storage = sections[subY];
                if (storage != lastStorage) {
                    decoded = resolveDecoded(resCache, storage);
                    lastStorage = storage;
                }

                float resistance = decoded.resistanceAt(x & 0xF, y & 0xF, z & 0xF);

                double tExitVoxel = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));
                double segLen = tExitVoxel - currentRayPosition;
                double remaining = radius - currentRayPosition;
                if (remaining <= PROCESSING_EPSILON) break;
                boolean clipAtRadius = segLen > remaining - 1e-12;
                if (clipAtRadius) segLen = remaining;

                if (resistance >= 0 && segLen > PROCESSING_EPSILON) {
                    if (resistance >= NUKE_RESISTANCE_CUTOFF) {
                        energy = 0;
                    } else {
                        double distFrac = Math.max(currentRayPosition, MIN_EFFECTIVE_DIST_FOR_ENERGY_CALC) * invRadius;
                        double energyLoss = getEnergyLossFactor(resistance, distFrac) * segLen;
                        energy -= energyLoss;
                        int bitIndex = ((worldHeight - 1 - (y - worldMinY)) << 8) | ((x & 0xF) << 4) | (z & 0xF);
                        if (useAggDamage) {
                            double damageInc = Math.max(DAMAGE_PER_BLOCK * segLen, energyLoss) * INITIAL_ENERGY_FACTOR;
                            agg.recordHit(cachedCPLong, bitIndex, damageInc, segLen);
                        } else if (energy > 0) {
                            currentBits.set(bitIndex);
                        }
                    }
                }

                currentRayPosition = tExitVoxel;
                if (energy <= 0.0 || clipAtRadius) break;
                if (tMaxX < tMaxY) {
                    if (tMaxX < tMaxZ) {
                        x += stepX;
                        tMaxX += tDeltaX;
                    } else {
                        z += stepZ;
                        tMaxZ += tDeltaZ;
                    }
                } else {
                    if (tMaxY < tMaxZ) {
                        y += stepY;
                        tMaxY += tDeltaY;
                    } else {
                        z += stepZ;
                        tMaxZ += tDeltaZ;
                    }
                }
            }
            return true;
        } catch (Exception e) {
            fail(e);
            return true;
        }
    }

    void mergeLocalAgg(LocalAgg agg) {
        if (algorithm == 2) {
            var dIter = agg.localDamage.long2ObjectEntrySet().fastIterator();
            while (dIter.hasNext()) {
                Long2ObjectMap.Entry<IntDoubleAccumulator> entry = dIter.next();
                getOrCreateChunkAgg(entry.getLongKey()).qDmg.offer(entry.getValue());
            }
            var lIter = agg.localLen.long2ObjectEntrySet().fastIterator();
            while (lIter.hasNext()) {
                Long2ObjectMap.Entry<IntDoubleAccumulator> entry = lIter.next();
                getOrCreateChunkAgg(entry.getLongKey()).qLen.offer(entry.getValue());
            }
        }
        agg.clear();
    }

    ChunkAgg getOrCreateChunkAgg(long chunkPos) {
        return aggMap.computeIfAbsent(chunkPos, ignored -> new ChunkAgg());
    }

    ConcurrentBitSet getOrCreateDestructionBitSet(long chunkPos) {
        ConcurrentBitSet bits = destructionMap.get(chunkPos);
        if (bits != null) return bits;
        ConcurrentBitSet created = new ConcurrentBitSet(bitsetSize);
        ConcurrentBitSet previous = destructionMap.putIfAbsent(chunkPos, created);
        return previous != null ? previous : created;
    }

    static final class BuildPackedTask extends RecursiveAction {
        final long[] packed;
        final int lo, hi, threshold;
        final double invRayIndexScale;

        BuildPackedTask(long[] packed, int lo, int hi, int threshold, double invRayIndexScale) {
            this.packed = packed;
            this.lo = lo;
            this.hi = hi;
            this.threshold = Math.max(1, threshold);
            this.invRayIndexScale = invRayIndexScale;
        }

        @Override
        protected void compute() {
            int len = hi - lo;
            if (len <= threshold) {
                for (int i = lo; i < hi; i++) packed[i] = packedRayKey(i, invRayIndexScale);
                return;
            }
            int mid = lo + (len >>> 1);
            invokeAll(new BuildPackedTask(packed, lo, mid, threshold, invRayIndexScale),
                    new BuildPackedTask(packed, mid, hi, threshold, invRayIndexScale));
        }
    }

    private void prepareRayCaches(DecodedSection[] resistance, EmptySubMaskCache empty) {
        if (empty.epoch == cacheEpoch) return;
        empty.epoch = cacheEpoch;
        // A reused worker can see new content without either the section or its array being replaced.
        Arrays.fill(empty.arrays, null);
        for (DecodedSection decoded : resistance) {
            if (decoded != null) decoded.section = null;
        }
    }

    static final class DecodedSection {
        static final int MAX_LUT = 4096;

        @Nullable LevelChunkSection section;
        @Nullable BitStorage storage;
        @Nullable Palette<BlockState> pal;
        float @Nullable [] lut;
        boolean single;
        boolean direct;
        float singleRes;

        static float resistanceOf(BlockState state) {
            if (state.isAir()) return -1f;
            return getNukeResistance(state);
        }

        void decode(LevelChunkSection sec) {
            PalettedContainer.Data<BlockState> data = sec.getStates().data; // storage + palette: one consistent pair
            BitStorage bits = data.storage();
            Palette<BlockState> palette = data.palette();
            if (bits.getBits() == 0 || palette instanceof SingleValuePalette<?>) {
                single = true;
                singleRes = resistanceOf(palette.valueFor(0));
            } else {
                single = false;
                int palSize = palette.getSize();
                direct = palSize > MAX_LUT;
                if (!direct) {
                    // idFor can grow a palette inside the same bit width while this data pair stays live.
                    int capacity = Math.max(1 << bits.getBits(), palSize);
                    if (lut == null || lut.length < capacity) lut = new float[Math.max(capacity, 64)];
                    Arrays.fill(lut, 0, capacity, Float.NaN);
                }
                storage = bits;
                pal = palette;
            }
            section = sec;
        }

        /** -1 = air; else nuke resistance. */
        float resistanceAt(int xLocal, int yLocal, int zLocal) {
            if (single) return singleRes;
            int pi = storage.get((yLocal << 8) | (zLocal << 4) | xLocal);
            if (direct) return resistanceOf(pal.valueFor(pi));
            float r = lut[pi];
            if (Float.isNaN(r)) {
                r = resistanceOf(pal.valueFor(pi));
                lut[pi] = r;
            }
            return r;
        }
    }

    /** Exactly one of {@code masks} / {@code agg} non-null. */
    record ResumeItem(@Nullable Int2ObjectOpenHashMap<BitSet> masks, @Nullable ChunkAgg agg) {
    }

    static final class IntDoubleAccumulator {
        static final int EMPTY = Integer.MIN_VALUE;
        static final int BASE_CAPACITY = 128;
        static final double LOAD = 0.50;
        static final int HASH_MUL = 0xC2B2AE35;

        int[] keys;
        double[] vals;
        int mask;
        int shift;
        int size;
        int resizeThreshold;

        IntDoubleAccumulator() {
            init(BASE_CAPACITY);
        }

        void init(int capacity) {
            keys = new int[capacity];
            Arrays.fill(keys, EMPTY);
            vals = new double[capacity];
            mask = capacity - 1;
            shift = 32 - Integer.numberOfTrailingZeros(capacity);
            size = 0;
            resizeThreshold = (int) (capacity * LOAD);
        }

        void add(int key, double delta) {
            int[] ks = keys;
            double[] vs = vals;
            int m = mask;
            int idx = (key * HASH_MUL) >>> shift;
            while (true) {
                int k = ks[idx];
                if (k == EMPTY) {
                    ks[idx] = key;
                    vs[idx] = delta;
                    if (++size >= resizeThreshold) rehash();
                    return;
                }
                if (k == key) {
                    vs[idx] += delta;
                    return;
                }
                idx = (idx + 1) & m;
            }
        }

        void accumulateTo(Int2DoubleOpenHashMap map) {
            int[] ks = keys;
            double[] vs = vals;
            for (int i = 0; i < ks.length; i++) {
                int k = ks[i];
                if (k != EMPTY) map.addTo(k, vs[i]);
            }
        }

        void rehash() {
            int[] oldK = keys;
            double[] oldV = vals;
            int newCap = oldK.length << 1;
            int[] newK = new int[newCap];
            Arrays.fill(newK, EMPTY);
            double[] newV = new double[newCap];
            int newMask = newCap - 1;
            int newShift = 32 - Integer.numberOfTrailingZeros(newCap);
            for (int i = 0; i < oldK.length; i++) {
                int k = oldK[i];
                if (k == EMPTY) continue;
                int idx = (k * HASH_MUL) >>> newShift;
                while (newK[idx] != EMPTY) idx = (idx + 1) & newMask;
                newK[idx] = k;
                newV[idx] = oldV[i];
            }
            keys = newK;
            vals = newV;
            mask = newMask;
            shift = newShift;
            resizeThreshold = (int) (newCap * LOAD);
        }
    }

    static final class LocalAgg {
        final Long2ObjectOpenHashMap<IntDoubleAccumulator> localDamage = new Long2ObjectOpenHashMap<>(16);
        final Long2ObjectOpenHashMap<IntDoubleAccumulator> localLen = new Long2ObjectOpenHashMap<>(16);
        final Long2ObjectOpenHashMap<IntArrayList> deferredMissing = new Long2ObjectOpenHashMap<>(4);
        long lastCp = Long.MIN_VALUE;
        @Nullable IntDoubleAccumulator lastDmg;
        @Nullable IntDoubleAccumulator lastLen;

        void clear() {
            localDamage.clear();
            localLen.clear();
            lastCp = Long.MIN_VALUE;
            lastDmg = null;
            lastLen = null;
            deferredMissing.clear();
        }

        void deferMissing(long chunkPos, int dirIndex) {
            deferredMissing.computeIfAbsent(chunkPos, k -> new IntArrayList()).add(dirIndex);
        }

        void recordHit(long chunkPos, int bitIndex, double damageInc, double segLen) {
            if (damageInc <= 0.0 && segLen <= 0.0) return;
            if (chunkPos != lastCp) {
                lastCp = chunkPos;
                lastDmg = localDamage.get(chunkPos);
                lastLen = localLen.get(chunkPos);
            }
            if (damageInc > 0.0) {
                if (lastDmg == null) {
                    lastDmg = new IntDoubleAccumulator();
                    localDamage.put(chunkPos, lastDmg);
                }
                lastDmg.add(bitIndex, damageInc);
            }
            if (segLen > 0.0) {
                if (lastLen == null) {
                    lastLen = new IntDoubleAccumulator();
                    localLen.put(chunkPos, lastLen);
                }
                lastLen.add(bitIndex, segLen);
            }
        }
    }

    static final class ChunkAgg {
        final Int2DoubleOpenHashMap damage = new Int2DoubleOpenHashMap(64);
        final Int2DoubleOpenHashMap passLen = new Int2DoubleOpenHashMap(64);
        final ConcurrentLinkedQueue<IntDoubleAccumulator> qDmg = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<IntDoubleAccumulator> qLen = new ConcurrentLinkedQueue<>();

        void drainUnlocked() {
            IntDoubleAccumulator a;
            while ((a = qDmg.poll()) != null) if (a.size > 0) a.accumulateTo(damage);
            while ((a = qLen.poll()) != null) if (a.size > 0) a.accumulateTo(passLen);
        }

        void clear() {
            damage.clear();
            passLen.clear();
            qDmg.clear();
            qLen.clear();
        }
    }

    private abstract class BlastTask extends RecursiveAction {
        @Override
        protected final void compute() {
            try {
                computeBlast();
            } catch (Throwable cause) {
                fail(cause);
            }
        }

        protected abstract void computeBlast();
    }

    final class ConsolidateAggTask extends BlastTask {
        final long[] keys;
        final ChunkAgg[] aggregates;
        final int start, end, threshold;

        ConsolidateAggTask(long[] keys, ChunkAgg[] aggregates, int start, int end, int threshold) {
            this.keys = keys;
            this.aggregates = aggregates;
            this.start = start;
            this.end = end;
            this.threshold = Math.max(1, threshold);
        }

        @Override
        protected void computeBlast() {
            int len = end - start;
            if (len <= threshold) {
                for (int i = start; i < end; i++) {
                    if (destroyFinished.get() != 0) return;
                    long cpLong = keys[i];
                    ChunkAgg agg = aggregates[i];
                    if (agg == null) continue;
                    agg.drainUnlocked();
                    if (NukeChunks.getLoadedSections(mirror, cpLong) == null) {
                        enqueueForMissingChunk(cpLong, new ResumeItem(null, agg));
                    } else {
                        applyAggregate(cpLong, agg);
                    }
                }
            } else {
                int mid = start + (len >>> 1);
                invokeAll(new ConsolidateAggTask(keys, aggregates, start, mid, threshold),
                        new ConsolidateAggTask(keys, aggregates, mid, end, threshold));
            }
        }
    }

    final class ConsolidateMaskTask extends BlastTask {
        final long[] keys;
        final int start, end, threshold;

        ConsolidateMaskTask(long[] keys, int start, int end, int threshold) {
            this.keys = keys;
            this.start = start;
            this.end = end;
            this.threshold = Math.max(1, threshold);
        }

        @Override
        protected void computeBlast() {
            int len = end - start;
            if (len <= threshold) {
                for (int i = start; i < end; i++) {
                    long cpLong = keys[i];
                    ConcurrentBitSet chunkBitSet = destructionMap.remove(cpLong);
                    if (chunkBitSet == null || chunkBitSet.isEmpty()) continue;
                    Int2ObjectOpenHashMap<BitSet> masks = splitBySubchunk(chunkBitSet);
                    if (NukeChunks.getLoadedSections(mirror, cpLong) == null) {
                        enqueueForMissingChunk(cpLong, new ResumeItem(masks, null));
                    } else {
                        applyCarve(cpLong, masks);
                    }
                }
            } else {
                int mid = start + (len >>> 1);
                invokeAll(new ConsolidateMaskTask(keys, start, mid, threshold),
                        new ConsolidateMaskTask(keys, mid, end, threshold));
            }
        }
    }

    abstract class RayBatchTask extends BlastTask {
        final int start, end, threshold;

        RayBatchTask(int start, int end, int threshold) {
            this.start = start;
            this.end = end;
            this.threshold = Math.max(1, threshold);
        }

        abstract int dirIndex(int i);

        abstract RayBatchTask split(int lo, int hi);

        @Override
        protected void computeBlast() {
            int len = end - start;
            if (len <= threshold) {
                LocalAgg agg = TL_LOCAL_AGG.get();
                agg.clear();
                int completed = 0;
                for (int i = start; i < end; i++) {
                    if (Thread.currentThread().isInterrupted() || destroyFinished.get() != 0) break;
                    if (traceSingle(dirIndex(i), agg)) completed++;
                }
                flushDeferredMissing(agg);
                mergeLocalAgg(agg);
                if (completed > 0 && pendingRays.addAndGet(-completed) == 0) onAllRaysFinished();
            } else {
                int mid = start + (len >>> 1);
                invokeAll(split(start, mid), split(mid, end));
            }
        }
    }

    final class RayTracerTask extends RayBatchTask {
        RayTracerTask(int start, int end, int threshold) {
            super(start, end, threshold);
        }

        @Override
        int dirIndex(int i) {
            int[] order = rayOrder;
            return order != null ? order[i] : i;
        }

        @Override
        RayBatchTask split(int lo, int hi) {
            return new RayTracerTask(lo, hi, threshold);
        }
    }

    final class ResumeBatchTask extends RayBatchTask {
        final IntArrayList indices;

        ResumeBatchTask(IntArrayList indices, int start, int end, int threshold) {
            super(start, end, threshold);
            this.indices = indices;
        }

        @Override
        int dirIndex(int i) {
            return indices.getInt(i);
        }

        @Override
        RayBatchTask split(int lo, int hi) {
            return new ResumeBatchTask(indices, lo, hi, threshold);
        }
    }
}
