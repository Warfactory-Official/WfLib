package com.wf.wflib.hazard.api;


import java.util.ArrayList;
import java.util.List;

public class HazardData {

    /**
     * Mixin's own default, and the convention it carries: ascending, so a LOWER number resolves first and a
     * higher one resolves later, over the top. Room either side for a pack to slot in without renumbering.
     */
    public static final int DEFAULT_PRIORITY = 1000;

    public int mutexBits = 0;
    public int priority = DEFAULT_PRIORITY;

    public List<HazardEntry> entries = new ArrayList<>();

    public HazardData addEntry(IHazardType hazard) {
        return this.addEntry(hazard, 1D);
    }

    public HazardData addEntry(IHazardType hazard, double level) {
        this.entries.add(new HazardEntry(hazard, level));
        return this;
    }

    public HazardData addEntry(HazardEntry entry) {
        this.entries.add(entry);
        return this;
    }

    public int getMutex() {
        return mutexBits;
    }

    public HazardData setMutex(int mutex) {
        this.mutexBits = mutex;
        return this;
    }

    public HazardData setPriority(int priority) {
        this.priority = priority;
        return this;
    }
}
