package com.wf.wflib.missile.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.world.chunk.TicketController;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.wf.wflib.core.api.ChunkTicketOps;
import com.wf.wflib.core.api.LevelCache;
import com.wf.wflib.core.api.WFLib;

/** Per-dimension registry of things that want a chance to engage a passing missile. */
public final class MissileListenerData extends SavedData {
    public static final String NAME = "wflib_missile_listeners";

    private static final long THREAT_TTL_TICKS = 60L;
    private static final double WAKE_MARGIN = MissileConfig.listenerSpawnMargin();

    /** Registered on the mod bus by {@code WFServerEvents.ModBusEvents#onRegisterTicketControllers}. */
    public static final TicketController CHUNK_TICKET = new TicketController(
            ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "missile_listener"),
            WFChunkValidation::validateTickets);

    private static final BlockRecord[] NO_RECORDS = new BlockRecord[0];
    private static final LevelCache<MissileListenerData> CACHE = LevelCache.perLevel(NAME,
            new SavedData.Factory<>(MissileListenerData::new, (tag, reg) -> MissileListenerData.load(tag)));

    // Mutated under the blockRecords monitor; reads (missile ticks on any region) use the copy-on-write snapshot.
    private final Map<BlockPos, BlockRecord> blockRecords = new ConcurrentHashMap<>();
    private volatile BlockRecord[] snapshot = NO_RECORDS;
    private final Map<UUID, IMissileListener> entityListeners = new ConcurrentHashMap<>();
    // BlockPos of block listeners we currently hold a wakeup chunk ticket for (transient).
    private final Set<BlockPos> forced = new HashSet<>();
    private boolean reconciled = false;

    public static MissileListenerData get(ServerLevel level) {
        return CACHE.get(level);
    }

    public static MissileListenerData load(CompoundTag tag) {
        MissileListenerData r = new MissileListenerData();
        ListTag list = tag.getList("Blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            BlockPos pos = new BlockPos(t.getInt("X"), t.getInt("Y"), t.getInt("Z"));
            Vec3 center = new Vec3(t.getDouble("CX"), t.getDouble("CY"), t.getDouble("CZ"));
            r.blockRecords.put(pos, new BlockRecord(pos, center, t.getDouble("Range")));
        }
        r.publish();
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (BlockRecord r : blockRecords.values()) {
            CompoundTag t = new CompoundTag();
            t.putInt("X", r.pos.getX());
            t.putInt("Y", r.pos.getY());
            t.putInt("Z", r.pos.getZ());
            t.putDouble("CX", r.center.x);
            t.putDouble("CY", r.center.y);
            t.putDouble("CZ", r.center.z);
            t.putDouble("Range", r.range);
            list.add(t);
        }
        tag.put("Blocks", list);
        return tag;
    }

    /** Register or refresh a listener. */
    public void register(Object key, IMissileListener listener) {
        if (key instanceof BlockPos pos) {
            Vec3 center = listener.listenerCenter();
            double range = listener.listenerRange();
            BlockRecord seen = blockRecords.get(pos);
            if (seen != null && seen.range == range && seen.center.equals(center)) {
                return;
            }
            synchronized (blockRecords) {
                BlockRecord existing = blockRecords.get(pos);
                if (existing == null || existing.range != range || !existing.center.equals(center)) {
                    blockRecords.put(pos, new BlockRecord(pos, center, range));
                    publish();
                    setDirty();
                }
            }
        } else {
            entityListeners.put((UUID) key, listener);
        }
    }

    public void deregister(Object key) {
        if (key instanceof BlockPos pos) {
            synchronized (blockRecords) {
                if (removeRecord(pos)) {
                    setDirty();
                }
            }
            // Any wakeup ticket still held for pos is released by tickWakeups' orphan sweep (which has the level).
        } else {
            entityListeners.remove(key);
        }
    }

    /**
     * Detection view of every active listener (block records, never dropped on unload, plus valid entity listeners)
     * as plain center/range pairs.
     */
    public List<ListenerView> views() {
        BlockRecord[] records = snapshot;
        List<ListenerView> out = new ArrayList<>(records.length + entityListeners.size());
        for (BlockRecord r : records) {
            out.add(r.view);
        }
        Iterator<Map.Entry<UUID, IMissileListener>> it = entityListeners.entrySet().iterator();
        while (it.hasNext()) {
            IMissileListener l = it.next().getValue();
            if (l.listenerValid()) {
                out.add(new ListenerView(l.listenerCenter(), l.listenerRange()));
            } else {
                it.remove();
            }
        }
        return out;
    }

    /**
     * Note that a missile is at {@code threatPos} this tick: any block listener within range wakes (or stays awake)
     * for {@link #THREAT_TTL_TICKS}.
     */
    public void noteThreat(Vec3 threatPos, long now) {
        BlockRecord[] records = snapshot;
        for (BlockRecord r : records) {
            double reach = r.range + WAKE_MARGIN;
            if (r.center.distanceToSqr(threatPos) <= reach * reach) {
                BlockRecord.LAST_THREAT.setOpaque(r, now);
            }
        }
    }

    /**
     * Hold a ticking chunk ticket on every block listener with a live threat, and release the rest. The monitor is
     * never held across a world read or ticket op: those lease the owning region on main, and a region worker
     * blocked on the monitor in {@link #register} would never go idle => deadlock.
     */
    public void tickWakeups(ServerLevel level, long now) {
        tickWakeups(new World() {
            @Override
            public void force(BlockPos pos, boolean add) {
                setForced(level, pos, add);
            }

            @Override
            public boolean loaded(BlockPos pos) {
                return level.isLoaded(pos);
            }

            @Override
            public boolean listensAt(BlockPos pos) {
                return level.getBlockEntity(pos) instanceof IMissileListener;
            }
        }, now);
    }

    /** World access of {@link #tickWakeups}; every call may block on a region lease. */
    public interface World {
        void force(BlockPos pos, boolean add);

        boolean loaded(BlockPos pos);

        boolean listensAt(BlockPos pos);
    }

    public void tickWakeups(World world, long now) {
        List<BlockPos> acquire = null;
        List<BlockPos> release = null;
        List<BlockRecord> probe = null;
        synchronized (blockRecords) {
            if (!reconciled) {
                for (BlockRecord r : snapshot) {
                    release = add(release, r.pos);
                }
                forced.clear();
                reconciled = true;
            }
            // Release tickets whose block listener was deregistered (broken) since last tick.
            Iterator<BlockPos> fit = forced.iterator();
            while (fit.hasNext()) {
                BlockPos p = fit.next();
                if (!blockRecords.containsKey(p)) {
                    release = add(release, p);
                    fit.remove();
                }
            }
            for (BlockRecord r : snapshot) {
                boolean want = (now - (long) BlockRecord.LAST_THREAT.getOpaque(r)) <= THREAT_TTL_TICKS;
                if (want) {
                    if (forced.add(r.pos)) {
                        acquire = add(acquire, r.pos);
                    }
                    if (probe == null) {
                        probe = new ArrayList<>(2);
                    }
                    probe.add(r);
                } else if (forced.remove(r.pos)) {
                    release = add(release, r.pos);
                }
            }
        }
        if (release != null) {
            for (BlockPos p : release) {
                world.force(p, false);
            }
        }
        if (acquire != null) {
            for (BlockPos p : acquire) {
                world.force(p, true);
            }
        }
        if (probe != null) {
            for (BlockRecord r : probe) {
                if (world.loaded(r.pos) && !world.listensAt(r.pos)) {
                    // The block was removed (broken while unloaded, worldedit, ...) but its record lingered.
                    world.force(r.pos, false);
                    synchronized (blockRecords) {
                        forced.remove(r.pos);
                        if (blockRecords.remove(r.pos, r)) {
                            publish();
                            setDirty();
                        }
                    }
                }
            }
        }
    }

    private static List<BlockPos> add(List<BlockPos> list, BlockPos pos) {
        List<BlockPos> out = list == null ? new ArrayList<>(2) : list;
        out.add(pos);
        return out;
    }

    private boolean removeRecord(BlockPos pos) {
        synchronized (blockRecords) {
            if (blockRecords.remove(pos) == null) {
                return false;
            }
            publish();
            return true;
        }
    }

    private void publish() {
        snapshot = blockRecords.values().toArray(NO_RECORDS);
    }

    private static void setForced(ServerLevel level, BlockPos pos, boolean add) {
        ChunkPos cp = new ChunkPos(pos);
        ChunkTicketOps.force(CHUNK_TICKET, level, pos, cp.x, cp.z, add, true);
    }

    public record ListenerView(Vec3 center, double range) {
    }

    private static final class BlockRecord {
        /** Opaque access only: value compared against a TTL, no ordering needed; no fence per in-range write. */
        private static final VarHandle LAST_THREAT;

        static {
            try {
                LAST_THREAT = MethodHandles.lookup().findVarHandle(BlockRecord.class, "lastThreat", long.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        private final BlockPos pos;
        private final Vec3 center;
        private final double range;
        private final ListenerView view;
        private long lastThreat = Long.MIN_VALUE;

        private BlockRecord(BlockPos pos, Vec3 center, double range) {
            this.pos = pos;
            this.center = center;
            this.range = range;
            this.view = new ListenerView(center, range);
        }
    }
}
