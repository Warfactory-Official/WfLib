package com.wf.wflib.ragdoll;

import com.norwood.ahf.hook.AhfHooks;
import com.wf.wflib.ragdoll.sim.RagdollPose;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Poses by entity id, one store per logical side (integrated server + client share the JVM); AHF reads them here. */
public final class RagdollStore {

    /** Client playout behind the newest pose, ticks: one late packet never starves the lerp. */
    private static final double DELAY = 2.0;
    private static final double MAX_BEHIND = 5.0;
    private static final int RING = 8;

    private static final RagdollStore SERVER = new RagdollStore(), CLIENT = new RagdollStore();

    public static final AhfHooks.RigPose HOOK = new AhfHooks.RigPose() {
        @Override
        public boolean active(LivingEntity e) {
            return of(e.level().isClientSide).track(e.getId()) != null;
        }

        @Override
        public double pose(LivingEntity e, float pt, double x, double y, double z, double bodyYaw, double[][] out) {
            Track t = of(e.level().isClientSide).track(e.getId());
            if (t == null) {
                return 0.0;
            }
            t.sample(pt).channels(RagdollServer.skeleton(), x, y, z, bodyYaw, out);
            return t.weight(pt);
        }
    };

    private final Map<Integer, Track> tracks = new ConcurrentHashMap<>();

    public static RagdollStore of(boolean client) {
        return client ? CLIENT : SERVER;
    }

    public @Nullable Track track(int id) {
        return tracks.isEmpty() ? null : tracks.get(id);
    }

    public Track open(int id) {
        return tracks.computeIfAbsent(id, k -> new Track());
    }

    public void close(int id) {
        tracks.remove(id);
    }

    public Iterable<Map.Entry<Integer, Track>> all() {
        return tracks.entrySet();
    }

    public void clear() {
        tracks.clear();
    }

    /** One body's poses. Client: ring + playout clock; server: {@link #newest} only. Writes + draws on one thread. */
    public static final class Track {
        private final RagdollPose[] ring = new RagdollPose[RING];
        private final long[] stamps = new long[RING];
        private int count, head = -1;
        private double clock = Double.NaN;
        private final RagdollPose scratch = new RagdollPose();
        /** Stand-up blend: ticks in, of {@link #blend}; 0 blend => holding the pose. */
        private int blend, blendAge;

        private Track() {
            for (int i = 0; i < RING; i++) {
                ring[i] = new RagdollPose();
            }
        }

        public RagdollPose push(long stamp) {
            head = (head + 1) % RING;
            count = Math.min(count + 1, RING);
            stamps[head] = stamp;
            return ring[head];
        }

        public RagdollPose newest() {
            return ring[head];
        }

        public boolean empty() {
            return count == 0;
        }

        /** Client tick: playout advances; a stall or burst snaps it back in range. */
        public void advance() {
            if (count == 0) {
                return;
            }
            double newest = stamps[head];
            clock = Double.isNaN(clock) ? newest - DELAY : clock + 1.0;
            if (newest - clock > MAX_BEHIND) {
                clock = newest - DELAY;
            }
            if (blend > 0) {
                blendAge++;
            }
        }

        public void release(int blendTicks) {
            blend = blendTicks;
            blendAge = 0;
        }

        public boolean released() {
            return blend > 0;
        }

        public boolean blendDone() {
            return blend > 0 && blendAge >= blend;
        }

        public void tickBlend() {
            blendAge++;
        }

        double weight(float pt) {
            if (blend <= 0) {
                return 1.0;
            }
            double t = Math.min(1.0, (blendAge + pt) / blend);
            t = t * t * (3.0 - 2.0 * t);
            return Math.max(1.0e-3, 1.0 - t);
        }

        /** Pose at render time; server (clock unset) = newest. */
        public RagdollPose sample(float pt) {
            if (count <= 1 || Double.isNaN(clock) || blend > 0) {
                return ring[head];
            }
            double at = Math.min(clock + pt, stamps[head]);
            for (int k = 0; k < count - 1; k++) {
                int b = (head - k + RING) % RING, a = (b - 1 + RING) % RING;
                if (stamps[a] <= at) {
                    double span = stamps[b] - stamps[a];
                    scratch.lerp(ring[a], ring[b], span > 0 ? Math.min(1.0, (at - stamps[a]) / span) : 1.0);
                    return scratch;
                }
            }
            return ring[(head - count + 1 + RING) % RING];
        }
    }
}
