package com.wf.wflib.round.api.client;

import com.wf.wflib.round.api.Rounds;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

/** Once-per-shot pass bookkeeping for client observers. */
public final class RoundPasses {
    private RoundPasses() {
    }

    /** Pellets of one shot share its seq: one pass per shot. */
    public static long shotKey(ClientRounds.Round r) {
        return r.seq() == Rounds.NO_SEQ ? r.key() : (long) r.shooterId() << 32 | (r.seq() & 0xFFFFFFFFL);
    }

    /**
     * Shots passed, value = rounds of the shot live at the pass; each end of one decrements. Absent => not passed.
     * Pellets ending before the pass never counted.
     */
    public static final class Fold {
        private final Long2IntOpenHashMap live = new Long2IntOpenHashMap();

        /** First pass of shot {@code key} with {@code rounds} live => true. */
        public boolean pass(long key, int rounds) {
            return live.putIfAbsent(key, rounds) == 0;
        }

        public void ended(long key) {
            if (live.addTo(key, -1) <= 1) {
                live.remove(key);
            }
        }

        public boolean heard(long key) {
            return live.containsKey(key);
        }

        public void clear() {
            live.clear();
        }
    }
}
