package com.wf.wflib.radiation;

import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Divergence from NTM {@code EntityFalloutRain} (terrain -> fallout / waste blocks): per-chunk ground source, no
 * blocks. Strength {@code s(t) = s0 * 2^(-t / falloutHalfLifeSeconds)}; each second a loaded chunk's surface pocket
 * is driven to {@code s(t)} ({@code emission = saturation = s}). Unloaded chunks wait; nothing force-loads.
 */
public final class Fallout extends SavedData {
    public static final String NAME = "wflib_fallout";
    /** NTM MK5 fallout scale = radius * 2.5 at {@code falloutRange} 100. */
    public static final double RANGE_PER_RADIUS = 2.5D;
    private static final double MIN_RAD = 0.05D;
    private static final int PERIOD = 20;

    private final Long2DoubleOpenHashMap strength = new Long2DoubleOpenHashMap();
    private final Long2LongOpenHashMap since = new Long2LongOpenHashMap();

    public static Fallout get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Fallout::new, Fallout::load), NAME);
    }

    /** Ground-zero {@code peak} RAD, {@code (1 - d/range)^2} out to {@code range} blocks, by chunk centre. */
    public static void deposit(ServerLevel level, double x, double z, double range, double peak) {
        if (!(range > 0) || !(peak > 0)) return;
        Fallout f = get(level);
        long now = level.getGameTime();
        int c0x = Mth.floor((x - range) / 16), c1x = Mth.floor((x + range) / 16);
        int c0z = Mth.floor((z - range) / 16), c1z = Mth.floor((z + range) / 16);
        for (int cx = c0x; cx <= c1x; cx++) {
            for (int cz = c0z; cz <= c1z; cz++) {
                double dx = cx * 16 + 8 - x, dz = cz * 16 + 8 - z;
                double d = Math.sqrt(dx * dx + dz * dz) / range;
                if (d >= 1) continue;
                double add = peak * (1 - d) * (1 - d);
                long key = ChunkPos.asLong(cx, cz);
                f.strength.put(key, f.current(key, now) + add);
                f.since.put(key, now);
            }
        }
        f.setDirty();
    }

    /** Ground source RAD at chunk {@code key}; 0 = none. */
    public double strengthAt(long key, long now) {
        return strength.containsKey(key) ? current(key, now) : 0D;
    }

    private double current(long key, long now) {
        double s = strength.get(key);
        if (s == 0D) return 0D;
        return s * Math.pow(0.5D, (now - since.get(key)) / (RadiationConfig.falloutHalfLifeSeconds * 20D));
    }

    static void tick(ServerLevel level) {
        if (level.getGameTime() % PERIOD != 0) return;
        Fallout f = get(level);
        if (f.strength.isEmpty()) return;
        long now = level.getGameTime();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        ObjectIterator<Long2DoubleMap.Entry> it = f.strength.long2DoubleEntrySet().fastIterator();
        while (it.hasNext()) {
            long key = it.next().getLongKey();
            double s = f.current(key, now);
            if (s < MIN_RAD) {
                it.remove();
                f.since.remove(key);
                f.setDirty();
                continue;
            }
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
            if (chunk == null) continue;
            int bx = ChunkPos.getX(key) * 16 + 8, bz = ChunkPos.getZ(key) * 16 + 8;
            pos.set(bx, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz) + 1, bz);
            RadiationSystemNT.incrementRad(level, pos, s, s);
        }
    }

    /** {@code /wfrad clearall}. */
    static void clear(ServerLevel level) {
        Fallout f = get(level);
        if (f.strength.isEmpty()) return;
        f.strength.clear();
        f.since.clear();
        f.setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long[] keys = strength.keySet().toLongArray();
        long[] bits = new long[keys.length];
        long[] times = new long[keys.length];
        for (int i = 0; i < keys.length; i++) {
            bits[i] = Double.doubleToRawLongBits(strength.get(keys[i]));
            times[i] = since.get(keys[i]);
        }
        tag.putLongArray("chunks", keys);
        tag.putLongArray("strength", bits);
        tag.putLongArray("since", times);
        return tag;
    }

    private static Fallout load(CompoundTag tag, HolderLookup.Provider registries) {
        Fallout f = new Fallout();
        long[] keys = tag.getLongArray("chunks");
        long[] bits = tag.getLongArray("strength");
        long[] times = tag.getLongArray("since");
        for (int i = 0; i < keys.length; i++) {
            f.strength.put(keys[i], Double.longBitsToDouble(bits[i]));
            f.since.put(keys[i], times[i]);
        }
        return f;
    }
}
