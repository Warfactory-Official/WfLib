package com.wf.wflib.belt.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * {@link BeltChain} diagnostics: per {@code update} the inputs + shape metrics + {@link Flag}s, last {@code capacity}
 * frames kept. Motion measured in the feed frame (X = out, Z = cartridge) = on screen for a viewmodel; inertial lag
 * moves links there smoothly => judged by jitter (acceleration reversing frame to frame), jump informational. JSONL lines
 * ({@link #resetJson} / {@link Frame#toJson}) replay through {@link #replay} bit-identical from a chain's first frame
 * (chain state = f(inputs)); a dump starting mid-chain converges after the restart.
 */
public final class BeltProbe {
    public enum Mode {EMPTY, FREE, ANCHORED, ANCHOR_DROPPED}

    public enum Flag {
        NON_FINITE, PITCH, JUMP, JITTER, REST_JITTER, END_GAP, TWISTED, GRAVITY_ON_AXIS, ANCHOR_DROPPED, DT_CLAMPED, STALL,
        RESIZED, RESTARTED;

        /** State, not misbehaviour. */
        public boolean benign() {
            return this == JUMP || this == RESIZED || this == RESTARTED || this == STALL || this == GRAVITY_ON_AXIS
                    || this == ANCHOR_DROPPED || this == DT_CLAMPED;
        }
    }

    /**
     * {@link Flag} thresholds. {@code JUMP}: pitches/frame; {@code JITTER}: min(|a_t|, |a_t-1|) where a = second
     * difference, a_t . a_t-1 < 0, pitches/frame^2 (clamp kinks / smooth sway 0), {@code + JITTER_SPEED} * link
     * speed (pitches/frame: whip on a fast swing unseen); {@code TWIST_TOTAL}: degrees.
     */
    public static final float JUMP_PITCHES = 2, JITTER_PITCHES = 0.1F, JITTER_SPEED = 0.2F, PITCH_ERROR = 0.02F, END_GAP_PITCHES = 0.5F,
            TWIST_TOTAL = 60, GRAVITY_AXIS = 0.9F;

    public record Input(Vector3fc feed, Vector3fc out, Vector3fc cartridge, List<Vector3fc> guides,
                        @Nullable Vector3fc end, @Nullable Vector3fc into, int count, Vector3fc gravity, Matrix4fc frame,
                        float dt) {
        static Input copy(Vector3fc feed, Vector3fc out, Vector3fc cartridge, List<? extends Vector3fc> guides,
                          @Nullable Vector3fc end, @Nullable Vector3fc into, int count, Vector3fc gravity,
                          Matrix4fc frame, float dt) {
            return new Input(new Vector3f(feed), new Vector3f(out), new Vector3f(cartridge),
                    guides.stream().<Vector3fc>map(Vector3f::new).toList(), end == null ? null : new Vector3f(end),
                    into == null ? null : new Vector3f(into), count, new Vector3f(gravity), new Matrix4f(frame), dt);
        }
    }

    /**
     * {@code need}: anchored curve links (-1 free); jumps / gaps / deviation in pitches; {@code jitter}: worst link
     * ({@code jitterAt}), pitches / (1 + speed * JITTER_SPEED / JITTER_PITCHES); angles in degrees; {@code
     * gravityAxis} = |g.cartridge|/|g|.
     */
    public record Frame(long n, float pitch, Input in, Mode mode, int need, int links, float pitchError, float maxBend,
                        float maxTwist, float totalTwist, float endGap, float jump, float jitter, int jitterAt,
                        float restJitter, float restDev, float gravityAxis, EnumSet<Flag> flags) {
        public boolean erratic() {
            return flags.stream().anyMatch(f -> !f.benign());
        }

        public JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("t", "frame");
            o.addProperty("n", n);
            o.addProperty("pitch", pitch);
            JsonObject i = new JsonObject();
            i.add("feed", vec(in.feed));
            i.add("out", vec(in.out));
            i.add("cartridge", vec(in.cartridge));
            if (!in.guides.isEmpty()) {
                JsonArray g = new JsonArray();
                in.guides.forEach(v -> g.add(vec(v)));
                i.add("guides", g);
            }
            if (in.end != null) {
                i.add("end", vec(in.end));
                i.add("into", vec(in.into));
            }
            i.addProperty("count", in.count);
            i.add("gravity", vec(in.gravity));
            JsonArray fr = new JsonArray();
            for (float v : in.frame.get(new float[16])) fr.add(v);
            i.add("frame", fr);
            i.addProperty("dt", in.dt);
            o.add("in", i);
            JsonObject m = new JsonObject();
            m.addProperty("mode", mode.name());
            m.addProperty("need", need);
            m.addProperty("links", links);
            m.addProperty("pitchError", pitchError);
            m.addProperty("maxBend", maxBend);
            m.addProperty("maxTwist", maxTwist);
            m.addProperty("totalTwist", totalTwist);
            m.addProperty("endGap", endGap);
            m.addProperty("jump", jump);
            m.addProperty("jitter", jitter);
            m.addProperty("jitterAt", jitterAt);
            m.addProperty("restJitter", restJitter);
            m.addProperty("restDev", restDev);
            m.addProperty("gravityAxis", gravityAxis);
            JsonArray f = new JsonArray();
            flags.forEach(x -> f.add(x.name()));
            m.add("flags", f);
            o.add("m", m);
            return o;
        }

        public String summary() {
            return String.format(Locale.ROOT,
                    "#%d %s links %d/%d need %d jump %.2f jitter %.2f@%d rest %.2f dev %.2f pitch %.3f bend %.1f twist %.1f/%.1f gap %.2f gAxis %.2f dt %.4f %s",
                    n, mode, links, in.count, need, jump, jitter, jitterAt, restJitter, restDev, pitchError, maxBend, maxTwist,
                    totalTwist, endGap, gravityAxis, in.dt, flags);
        }
    }

    private final int capacity;
    private final ArrayDeque<Frame> frames = new ArrayDeque<>();
    private final int[] counts = new int[Flag.values().length];
    private long n;
    private boolean restarted = true;
    /** Feed-frame positions: this / last / second-last frame; sim + rest. */
    private Vector3f[] local = new Vector3f[0], local1 = new Vector3f[0], local2 = new Vector3f[0],
            restLocal = new Vector3f[0], restLocal1 = new Vector3f[0], restLocal2 = new Vector3f[0];
    private int previous = -1, previous2 = -1, previous3 = -1;
    private final Vector3f bx = new Vector3f(), by = new Vector3f(), bz = new Vector3f(), w = new Vector3f(),
            v = new Vector3f();

    public BeltProbe(int capacity) {
        this.capacity = capacity;
    }

    /** Next frame compares nothing (new chain / reset). */
    public void restart() {
        restarted = true;
        previous = previous2 = previous3 = -1;
    }

    public static JsonObject resetJson(float pitch) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "reset");
        o.addProperty("pitch", pitch);
        return o;
    }

    @Nullable
    public Frame last() {
        return frames.peekLast();
    }

    public List<Frame> frames() {
        return new ArrayList<>(frames);
    }

    public int count(Flag f) {
        return counts[f.ordinal()];
    }

    public long updates() {
        return n;
    }

    public void clearCounts() {
        java.util.Arrays.fill(counts, 0);
    }

    void record(BeltChain c, Input in, Mode mode, int need, boolean clamped) {
        EnumSet<Flag> flags = EnumSet.noneOf(Flag.class);
        int links = c.links();
        float pitch = c.pitch();
        bx.set(in.out).normalize();
        bz.set(in.cartridge).fma(-in.cartridge.dot(bx), bx).normalize();
        bz.cross(bx, by);
        if (local.length < links) {
            local = grow(local, links);
            local1 = grow(local1, links);
            local2 = grow(local2, links);
            restLocal = grow(restLocal, links);
            restLocal1 = grow(restLocal1, links);
            restLocal2 = grow(restLocal2, links);
        }
        float pitchError = 0, maxBend = 0, maxTwist = 0, jump = 0, jitter = 0, restJitter = 0, restDev = 0;
        int jitterAt = -1;
        boolean finite = true;
        int compare = restarted ? 0 : Math.min(previous, links);
        int compare3 = restarted || previous3 < 0 ? 0 : Math.min(Math.min(compare, previous2), previous3);
        for (int k = 0; k < links; k++) {
            Vector3f x = c.x[k];
            finite &= Float.isFinite(x.x) && Float.isFinite(x.y) && Float.isFinite(x.z);
            if (k > 0) {
                pitchError = Math.max(pitchError, Math.abs(x.distance(c.x[k - 1]) - pitch) / pitch);
                maxBend = Math.max(maxBend, c.dir[k - 1].angle(c.dir[k]));
                maxTwist = Math.max(maxTwist, c.axis[k - 1].angle(c.axis[k]));
            }
            restDev = Math.max(restDev, x.distance(c.rest[k]) / pitch);
            toLocal(x, in.feed, w);
            toLocal(c.rest[k], in.feed, v);
            if (k < compare) jump = Math.max(jump, w.distance(local[k]) / pitch);
            if (k < compare3) {
                float j = jitter(w, local[k], local1[k], local2[k]) / pitch
                        / (1 + JITTER_SPEED / JITTER_PITCHES * w.distance(local[k]) / pitch);
                if (j > jitter) {
                    jitter = j;
                    jitterAt = k;
                }
                restJitter = Math.max(restJitter, jitter(v, restLocal[k], restLocal1[k], restLocal2[k]) / pitch);
            }
            local2[k].set(local1[k]);
            local1[k].set(local[k]);
            local[k].set(w);
            restLocal2[k].set(restLocal1[k]);
            restLocal1[k].set(restLocal[k]);
            restLocal[k].set(v);
        }
        float totalTwist = links > 1 ? c.axis[0].angle(c.axis[links - 1]) : 0;
        float endGap = mode == Mode.ANCHORED && in.end != null ? c.x[links - 1].distance(in.end) / pitch : 0;
        float g = in.gravity.length();
        float gravityAxis = g > 0 ? Math.abs(in.gravity.dot(bz)) / g : 0;
        if (!finite) flags.add(Flag.NON_FINITE);
        if (pitchError > PITCH_ERROR) flags.add(Flag.PITCH);
        if (jump > JUMP_PITCHES) flags.add(Flag.JUMP);
        if (jitter > JITTER_PITCHES) flags.add(Flag.JITTER);
        if (restJitter > JITTER_PITCHES) flags.add(Flag.REST_JITTER);
        if (endGap > END_GAP_PITCHES) flags.add(Flag.END_GAP);
        if (Math.toDegrees(totalTwist) > TWIST_TOTAL) flags.add(Flag.TWISTED);
        if (gravityAxis > GRAVITY_AXIS) flags.add(Flag.GRAVITY_ON_AXIS);
        if (mode == Mode.ANCHOR_DROPPED) flags.add(Flag.ANCHOR_DROPPED);
        if (clamped) flags.add(Flag.DT_CLAMPED);
        if (in.dt <= 0) flags.add(Flag.STALL);
        if (!restarted && previous != links) flags.add(Flag.RESIZED);
        if (restarted) flags.add(Flag.RESTARTED);
        for (Flag f : flags) counts[f.ordinal()]++;
        restarted = false;
        previous3 = previous2;
        previous2 = previous;
        previous = links;
        if (frames.size() == capacity) frames.removeFirst();
        frames.addLast(new Frame(n++, pitch, in, mode, need, links, pitchError, (float) Math.toDegrees(maxBend),
                (float) Math.toDegrees(maxTwist), (float) Math.toDegrees(totalTwist), endGap, jump, jitter, jitterAt,
                restJitter, restDev, gravityAxis, flags));
    }

    /** Positions a (now) .. e (3 frames back): a1 = a - 2b + c, a0 = b - 2c + e; a1 . a0 < 0 => min(|a1|, |a0|), else 0. */
    private static float jitter(Vector3fc a, Vector3fc b, Vector3fc c, Vector3fc e) {
        float x1 = a.x() - 2 * b.x() + c.x(), y1 = a.y() - 2 * b.y() + c.y(), z1 = a.z() - 2 * b.z() + c.z();
        float x0 = b.x() - 2 * c.x() + e.x(), y0 = b.y() - 2 * c.y() + e.y(), z0 = b.z() - 2 * c.z() + e.z();
        if (x1 * x0 + y1 * y0 + z1 * z0 >= 0) return 0;
        return (float) Math.sqrt(Math.min(x1 * x1 + y1 * y1 + z1 * z1, x0 * x0 + y0 * y0 + z0 * z0));
    }

    private void toLocal(Vector3fc p, Vector3fc feed, Vector3f dest) {
        float dx = p.x() - feed.x(), dy = p.y() - feed.y(), dz = p.z() - feed.z();
        dest.set(dx * bx.x + dy * bx.y + dz * bx.z, dx * by.x + dy * by.y + dz * by.z, dx * bz.x + dy * bz.y + dz * bz.z);
    }

    private static Vector3f[] grow(Vector3f[] a, int n) {
        Vector3f[] out = new Vector3f[n];
        System.arraycopy(a, 0, out, 0, a.length);
        for (int i = a.length; i < n; i++) out[i] = new Vector3f();
        return out;
    }

    private static JsonArray vec(Vector3fc v) {
        JsonArray a = new JsonArray();
        a.add(v.x());
        a.add(v.y());
        a.add(v.z());
        return a;
    }

    private static Vector3f vec(JsonElement e) {
        JsonArray a = e.getAsJsonArray();
        return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }

    /** JSONL capture -> fresh chains (one per {@code reset} line) fed the recorded inputs -> their frames. */
    public static List<Frame> replay(BufferedReader lines) throws IOException {
        List<Frame> out = new ArrayList<>();
        BeltChain chain = null;
        BeltProbe probe = new BeltProbe(Integer.MAX_VALUE);
        for (String line; (line = lines.readLine()) != null; ) {
            if (line.isBlank()) continue;
            JsonObject o = JsonParser.parseString(line).getAsJsonObject();
            String t = o.has("t") ? o.get("t").getAsString() : "";
            if (t.equals("reset")) {
                chain = new BeltChain(o.get("pitch").getAsFloat());
                chain.probe(probe);
            } else if (t.equals("frame")) {
                // dump ring spans chains: RESTARTED / pitch change => new chain
                boolean restarted = o.getAsJsonObject("m").getAsJsonArray("flags").contains(
                        new JsonPrimitive(Flag.RESTARTED.name()));
                if (o.has("pitch") && (chain == null || restarted || chain.pitch() != o.get("pitch").getAsFloat())) {
                    chain = new BeltChain(o.get("pitch").getAsFloat());
                    chain.probe(probe);
                }
                if (chain == null) throw new IOException("frame before reset: " + line);
                JsonObject i = o.getAsJsonObject("in");
                List<Vector3fc> guides = new ArrayList<>();
                if (i.has("guides")) i.getAsJsonArray("guides").forEach(e -> guides.add(vec(e)));
                Matrix4f frame = new Matrix4f();
                if (i.has("frame")) {
                    float[] m = new float[16];
                    for (int k = 0; k < 16; k++) m[k] = i.getAsJsonArray("frame").get(k).getAsFloat();
                    frame.set(m);
                }
                chain.update(vec(i.get("feed")), vec(i.get("out")), vec(i.get("cartridge")), guides,
                        i.has("end") ? vec(i.get("end")) : null, i.has("into") ? vec(i.get("into")) : null,
                        i.get("count").getAsInt(), vec(i.get("gravity")), frame, i.get("dt").getAsFloat());
                Frame f = probe.last();
                if (f != null && (out.isEmpty() || out.getLast() != f)) out.add(f);
            }
        }
        return out;
    }
}
