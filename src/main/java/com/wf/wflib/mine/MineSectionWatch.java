package com.wf.wflib.mine;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** A revision counter for the subchunks that something is resting on. */
public final class MineSectionWatch {

    /** The revision of a section nothing is watching. Never equal to a live revision. */
    public static final int UNWATCHED = Integer.MIN_VALUE;

    private static final Map<Level, Sections> BY_LEVEL = new ConcurrentHashMap<>();

    /** Non-zero when any section anywhere is watched: the first thing every block write in the game asks. */
    private static final AtomicInteger WATCHED = new AtomicInteger();

    private MineSectionWatch() {
    }

    /** @return true if any section is being watched at all. */
    public static boolean isWatching() {
        return WATCHED.get() != 0;
    }

    /**
     * Starts watching the section containing {@code pos}, or takes another reference on it.
     *
     * @return the section's current revision, to be compared against later
     */
    public static int watch(ServerLevel level, long section) {
        for (; ; ) {
            Sections sections = BY_LEVEL.computeIfAbsent(level, key -> new Sections());
            synchronized (sections) {
                if (sections.dead) {
                    continue;
                }
                if (sections.refs.addTo(section, 1) == 0) {
                    sections.versions.put(section, new AtomicInteger());
                    WATCHED.incrementAndGet();
                    return 0;
                }
                return sections.versions.get(section).get();
            }
        }
    }

    /** Drops a reference taken by {@link #watch}. */
    public static void unwatch(ServerLevel level, long section) {
        Sections sections = BY_LEVEL.get(level);
        if (sections == null) {
            return;
        }
        synchronized (sections) {
            int refs = sections.refs.get(section);
            if (refs <= 0) {
                return;
            }
            if (refs == 1) {
                sections.refs.remove(section);
                sections.versions.remove(section);
                WATCHED.decrementAndGet();
                if (sections.refs.isEmpty()) {
                    sections.dead = true;
                    BY_LEVEL.remove(level, sections);
                }
            } else {
                sections.refs.put(section, refs - 1);
            }
        }
    }

    /** @return the section's current revision, or {@link #UNWATCHED} if nothing is watching it. */
    public static int version(ServerLevel level, long section) {
        Sections sections = BY_LEVEL.get(level);
        if (sections == null) {
            return UNWATCHED;
        }
        AtomicInteger version = sections.versions.get(section);
        return version == null ? UNWATCHED : version.get();
    }

    /** @return the key {@link #watch} and {@link #version} take for the section containing {@code pos}. */
    public static long key(BlockPos pos) {
        return SectionPos.asLong(pos);
    }

    /** Every block write in a loaded chunk lands here. Gated on {@link #isWatching} by the caller. */
    public static void onBlockChanged(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        Sections sections = BY_LEVEL.get(level);
        if (sections == null) {
            return;
        }
        AtomicInteger version = sections.versions.get(SectionPos.asLong(pos));
        if (version != null) {
            version.incrementAndGet();
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        Sections sections = BY_LEVEL.remove(event.getLevel());
        if (sections != null) {
            synchronized (sections) {
                sections.dead = true;
                WATCHED.addAndGet(-sections.refs.size());
                sections.refs.clear();
                sections.versions.clear();
            }
        }
    }

    /** {@code refs}, {@code dead}: guarded by this; {@code versions}: lock-free read, writes under the lock. */
    private static final class Sections {

        private final Long2IntOpenHashMap refs = new Long2IntOpenHashMap();
        private final ConcurrentHashMap<Long, AtomicInteger> versions = new ConcurrentHashMap<>();
        private boolean dead;
    }
}
