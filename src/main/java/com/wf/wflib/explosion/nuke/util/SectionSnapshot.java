package com.wf.wflib.explosion.nuke.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

public final class SectionSnapshot {
    private final LevelChunk chunk;
    private final LevelChunkSection[] original;
    private final long[] generations;
    private final int observed;
    public final LevelChunkSection[] sections;
    private int changed;

    private SectionSnapshot(LevelChunk chunk, int observed) {
        this.chunk = chunk;
        this.observed = observed;
        LevelChunkSection[] live = chunk.getSections();
        this.original = new LevelChunkSection[live.length];
        this.generations = new long[live.length];
        this.sections = new LevelChunkSection[live.length];
        for (int mask = observed; mask != 0; mask &= mask - 1) {
            int index = Integer.numberOfTrailingZeros(mask);
            LevelChunkSection source = live[index];
            original[index] = source;
            generations[index] = ((GenerationSection) source).wflib$generation();
            sections[index] = copy(source);
        }
    }

    /** 1.21.1 has no {@code LevelChunkSection.copy}; biomes shared, live writers replace rather than mutate them. */
    static LevelChunkSection copy(LevelChunkSection source) {
        return new LevelChunkSection(source.getStates().copy(), source.getBiomes());
    }

    public static SectionSnapshot capture(LevelChunk chunk, int observed) {
        int length = chunk.getSections().length;
        int available = length >= Integer.SIZE ? -1 : (1 << length) - 1;
        return new SectionSnapshot(chunk, observed & available);
    }

    public void changed(int index) {
        changed |= 1 << index;
    }

    public LevelChunk chunk() {
        return chunk;
    }

    public boolean publish() {
        ServerLevel level = (ServerLevel) chunk.getLevel();
        if (NukeChunks.liveChunkNow(level, chunk.getPos().toLong()) != chunk) return false;
        LevelChunkSection[] live = chunk.getSections();
        for (int mask = observed; mask != 0; mask &= mask - 1) {
            int index = Integer.numberOfTrailingZeros(mask);
            if (live[index] != original[index]
                    || ((GenerationSection) live[index]).wflib$generation() != generations[index]) {
                return false;
            }
        }
        for (int mask = changed; mask != 0; mask &= mask - 1) {
            int index = Integer.numberOfTrailingZeros(mask);
            if (!NukeChunks.casSectionAt(original[index], sections[index], live, index)) {
                throw new IllegalStateException("Section " + index + " of " + chunk.getPos() + " replaced mid-publish");
            }
        }
        // TODO: Lithium section change listener not handed to the replacement (NTM-next LithiumSectionCompat).
        if (changed != 0) chunk.setUnsaved(true);
        return true;
    }
}
