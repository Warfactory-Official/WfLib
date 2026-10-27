package com.wf.wflib.client.xray;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.wf.wflib.WFLib;
import com.wf.wflib.client.render.WFRenderTypes;
import com.wf.wflib.client.xray.XrDefs.Action;
import com.wf.wflib.client.xray.XrDefs.Effect;
import com.wf.wflib.client.xray.XrDefs.Group;
import com.wf.wflib.client.xray.XrDefs.GroupEntry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Plays STALKER particle groups ({@link XrDefs}) as X-Ray does: each effect stepped at its fixed 33 ms
 * ({@code PS::fDT_STEP}, at most three steps a frame), its actions run in order ({@code particle_actions_collection}),
 * frames animated ({@code CPEDef::ExecuteAnimate}), sprites built as {@code FillSprite} does (camera-facing, or along
 * the path / world for {@code dfAlignToPath}) and drawn unlit with the effect's blend. Positions are local to the
 * group's origin, spread by its scale across and its height up when drawn (an anomaly can be wider than its STALKER
 * zone without growing taller).
 * Child effects follow {@code CParticleGroup}: started where a particle is born ({@code on_birth_child}), following it
 * while it lives ({@code on_play_child}), or where it dies ({@code on_dead_child}). Not done: collision, distortion
 * sprites, parent motion.
 */
@EventBusSubscriber(modid = WFLib.MODID, value = Dist.CLIENT)
public final class XrParticles {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float STEP = 0.033F;
    private static final int MAX_STEPS = 3;

    private static final Map<String, Optional<Effect>> EFFECTS = new HashMap<>();
    private static final Map<String, Optional<Group>> GROUPS = new HashMap<>();
    private static final List<Playing> PLAYING = new ArrayList<>();
    private static long lastNanos;

    private XrParticles() {
    }

    /**
     * A playing group; {@link #stop} it (its particles live out), or it stops by its own time limit. Looped (an
     * anomaly's idle look), it starts over once everything in it has run out, as a looped {@code CParticlesObject}.
     */
    public static final class Playing {
        private final List<Running> effects = new ArrayList<>();
        /** Started by particles ({@link GroupEntry#birthChild} ...), offset to where they were. */
        private final List<Running> children = new ArrayList<>();
        /** A particle -> the play child following it. */
        private final Map<P, Running> following = new java.util.IdentityHashMap<>();
        private final float timeLimit;
        private final boolean loop;
        private Vec3 origin;
        /** Spread across (x, z) and up (y, and sprite size). */
        private final float scale;
        private final float height;
        private float age;
        private boolean stopped;
        private float carry;

        private Playing(Group group, Vec3 origin, float scale, float height, boolean loop) {
            this.timeLimit = group.timeLimit();
            this.loop = loop;
            this.origin = origin;
            this.scale = scale;
            this.height = height;
            for (GroupEntry entry : group.entries()) {
                effect(entry.effect()).ifPresent(e -> effects.add(new Running(e, entry, this)));
            }
        }

        public void stop() {
            stopped = true;
        }

        public void moveTo(Vec3 origin) {
            this.origin = origin;
        }

        /**
         * Move the emitter, leaving what it has already let go where it is (X-Ray emits into world space): a moving
         * source trails its particles. Call it every frame for a smooth path.
         */
        public void trailTo(Vec3 origin) {
            float dx = (float) (origin.x - this.origin.x) / scale;
            float dy = (float) (origin.y - this.origin.y) / height;
            float dz = (float) (origin.z - this.origin.z) / scale;
            this.origin = origin;
            for (List<Running> runs : List.of(effects, children)) {
                for (Running run : runs) {
                    for (int i = 0; i < run.count; i++) {
                        run.particles[i].x -= dx;
                        run.particles[i].y -= dy;
                        run.particles[i].z -= dz;
                    }
                }
            }
        }

        public boolean finished() {
            return stopped && spent();
        }

        /** Nothing left emitting or alive. */
        private boolean spent() {
            if (!children.isEmpty()) {
                return false;
            }
            for (Running r : effects) {
                boolean done = r.limited || timeLimit > 0.0F && age >= timeLimit
                        || r.entry.time1() > 0.0F && age >= r.entry.time1();
                if (r.count > 0 || !stopped && !done) {
                    return false;
                }
            }
            return true;
        }

        private void step(RandomSource random) {
            if (loop && !stopped && spent()) {
                age = 0.0F;
                for (Running r : effects) {
                    r.limited = false;
                    r.elapsed = 0.0F;
                }
            }
            age += STEP;
            if (timeLimit > 0.0F && age >= timeLimit) {
                stopped = true;
            }
            for (Running r : effects) {
                boolean started = age >= r.entry.time0();
                boolean ended = stopped || r.entry.time1() > 0.0F && age >= r.entry.time1();
                r.emitting = started && !ended && !r.limited;
                if (started) {
                    r.step(random);
                }
            }
            for (Map.Entry<P, Running> f : following.entrySet()) {
                Running parent = f.getValue().parent;
                f.getValue().moveTo(parent.ox + f.getKey().x, parent.oy + f.getKey().y, parent.oz + f.getKey().z);
            }
            // Copied: a child's particles never start children of their own, but the list may still grow.
            for (Running c : List.copyOf(children)) {
                c.emitting = !c.limited && !c.released;
                c.step(random);
            }
            children.removeIf(c -> !c.emitting && c.count == 0);
        }

        /** {@code CParticleGroup} birth hook: free and related children. */
        private void born(Running from, P p) {
            GroupEntry e = from.entry;
            if (e.birthChild() != null) {
                child(e.birthChild(), from, p, true);
            }
            if (e.playChild() != null) {
                Running c = child(e.playChild(), from, p, false);
                if (c != null) {
                    following.put(p, c);
                }
            }
        }

        /** Death hook: a related child released (it lives out), a death child started. */
        private void died(Running from, P p) {
            Running c = following.remove(p);
            if (c != null) {
                c.released = true;
            }
            if (from.entry.deadChild() != null) {
                child(from.entry.deadChild(), from, p, true);
            }
        }

        @org.jetbrains.annotations.Nullable
        private Running child(String name, Running from, P p, boolean free) {
            Optional<Effect> def = effect(name);
            // A free child has to end by itself (StartFreeChild refuses looped ones).
            if (def.isEmpty() || free && def.get().timeLimit() <= 0.0F) {
                return null;
            }
            Running c = new Running(def.get(), new GroupEntry(name, 0.0F, 0.0F, null, null, null), this);
            c.parent = from;
            c.moveTo(from.ox + p.x, from.oy + p.y, from.oz + p.z);
            children.add(c);
            return c;
        }
    }

    /** One particle (X-Ray {@code PAPI::Particle}). */
    private static final class P {
        float x;
        float y;
        float z;
        float vx;
        float vy;
        float vz;
        float sx;
        float sy;
        float sz;
        float rot;
        float r;
        float g;
        float b;
        float a;
        float age;
        float frame;
        boolean ccw;
    }

    /** One effect of a playing group. */
    private static final class Running {
        final Effect def;
        final GroupEntry entry;
        final Playing owner;
        /** The effect whose particle started this one; null for the group's own. */
        @org.jetbrains.annotations.Nullable
        Running parent;
        /** Where this effect's particles are relative to (a child: its particle). */
        float ox;
        float oy;
        float oz;
        /** A play child whose particle died: stops emitting, lives out. */
        boolean released;
        final P[] particles;
        int count;
        boolean emitting;
        /** Its own time limit ran out ({@code dfTimeLimit}): stopped for good. */
        boolean limited;
        float elapsed;
        /** {@link XrDefs#TURBULENCE}'s own clock. */
        float turbulenceAge;
        private final float[] scratch = new float[3];

        Running(Effect def, GroupEntry entry, Playing owner) {
            this.def = def;
            this.entry = entry;
            this.owner = owner;
            this.particles = new P[Math.max(def.maxParticles(), 1)];
            for (int i = 0; i < particles.length; i++) {
                particles[i] = new P();
            }
        }

        void moveTo(float x, float y, float z) {
            ox = x;
            oy = y;
            oz = z;
        }

        void step(RandomSource random) {
            if (def.timeLimit() > 0.0F && emitting) {
                elapsed += STEP;
                if (elapsed >= def.timeLimit()) {
                    limited = true;
                    emitting = false;
                }
            }
            float tmMax = 0.0F;
            for (Action a : def.actions()) {
                switch (a.type()) {
                    case XrDefs.KILL_OLD -> {
                        // floats: Age Limit; bools: Kill Less Than
                        tmMax = a.f(0);
                        for (int i = count - 1; i >= 0; i--) {
                            if (!((particles[i].age < a.f(0)) ^ a.b(0))) {
                                owner.died(this, particles[i]);
                                remove(i);
                            }
                        }
                    }
                    case XrDefs.SOURCE -> {
                        // floats: Age Sigma, Color\Alpha, Parent Motion, Rate, Starting Age
                        // domains: Color, Domain, Rotation, Size, Velocity; bools: Allow Rotate, Single Size
                        if (!emitting) {
                            continue;
                        }
                        float want = a.f(3) * STEP;
                        int rate = (int) Math.floor(want);
                        if (random.nextFloat() < want - rate) {
                            rate++;
                        }
                        rate = Math.min(rate, particles.length - count);
                        for (int i = 0; i < rate; i++) {
                            P p = particles[count++];
                            a.d(1).generate(random, scratch);
                            p.x = scratch[0];
                            p.y = scratch[1];
                            p.z = scratch[2];
                            a.d(3).generate(random, scratch);
                            p.sx = scratch[0];
                            p.sy = a.b(1) ? scratch[0] : scratch[1];
                            p.sz = a.b(1) ? scratch[0] : scratch[2];
                            a.d(2).generate(random, scratch);
                            p.rot = scratch[0];
                            a.d(4).generate(random, scratch);
                            p.vx = scratch[0];
                            p.vy = scratch[1];
                            p.vz = scratch[2];
                            a.d(0).generate(random, scratch);
                            p.r = scratch[0];
                            p.g = scratch[1];
                            p.b = scratch[2];
                            p.a = a.f(1);
                            p.age = a.f(4) + (float) random.nextGaussian() * a.f(0);
                            p.frame = def.has(XrDefs.RANDOM_FRAME) ? random.nextInt(Math.max(def.frameCount(), 1)) : 0;
                            p.ccw = def.has(XrDefs.RANDOM_PLAYBACK) && random.nextBoolean();
                            owner.born(this, p);
                        }
                    }
                    case XrDefs.TARGET_COLOR -> {
                        // floats: Alpha, Scale, TimeFrom, TimeTo; vectors: Color
                        float k = a.f(1) * STEP;
                        float[] c = a.v(0);
                        for (int i = 0; i < count; i++) {
                            P p = particles[i];
                            if (p.age < a.f(2) * tmMax || p.age > a.f(3) * tmMax) {
                                continue;
                            }
                            p.r += (c[0] - p.r) * k;
                            p.g += (c[1] - p.g) * k;
                            p.b += (c[2] - p.b) * k;
                            p.a += (a.f(0) - p.a) * k;
                        }
                    }
                    case XrDefs.TARGET_SIZE -> {
                        // vectors: Scale, Size
                        float[] k = a.v(0);
                        float[] s = a.v(1);
                        for (int i = 0; i < count; i++) {
                            P p = particles[i];
                            p.sx += (s[0] - p.sx) * k[0] * STEP;
                            p.sy += (s[1] - p.sy) * k[1] * STEP;
                            p.sz += (s[2] - p.sz) * k[2] * STEP;
                        }
                    }
                    case XrDefs.MOVE -> {
                        for (int i = 0; i < count; i++) {
                            P p = particles[i];
                            p.age += STEP;
                            p.x += p.vx * STEP;
                            p.y += p.vy * STEP;
                            p.z += p.vz * STEP;
                        }
                    }
                    case XrDefs.TARGET_VELOCITY -> {
                        // floats: Scale; vectors: Velocity
                        float k = a.f(0) * STEP;
                        float[] v = a.v(0);
                        for (int i = 0; i < count; i++) {
                            P p = particles[i];
                            p.vx += (v[0] - p.vx) * k;
                            p.vy += (v[1] - p.vy) * k;
                            p.vz += (v[2] - p.vz) * k;
                        }
                    }
                    case XrDefs.GRAVITY -> {
                        // vectors: Direction
                        float[] d = a.v(0);
                        for (int i = 0; i < count; i++) {
                            P p = particles[i];
                            p.vx += d[0] * STEP;
                            p.vy += d[1] * STEP;
                            p.vz += d[2] * STEP;
                        }
                    }
                    case XrDefs.TARGET_ROTATE -> {
                        // floats: Scale; vectors: Rotation
                        float k = a.f(0) * STEP;
                        float target = Math.abs(a.v(0)[0]);
                        for (int i = 0; i < count; i++) {
                            P p = particles[i];
                            float sign = p.rot >= 0.0F ? k : -k;
                            p.rot += (target - Math.abs(p.rot)) * sign;
                        }
                    }
                    case XrDefs.TURBULENCE -> {
                        // floats: Delta, Frequency, Magnitude; ints: Octaves; vectors: Movement
                        turbulenceAge += STEP;
                        float eps = a.f(0);
                        float freq = a.f(1);
                        float mag = a.f(2);
                        int octaves = a.ints().length > 0 ? Math.max(a.ints()[0], 1) : 1;
                        float[] off = a.v(0);
                        for (int i = 0; i < count; i++) {
                            P p = particles[i];
                            float x = p.x + off[0] * turbulenceAge;
                            float y = p.y + off[1] * turbulenceAge;
                            float z = p.z + off[2] * turbulenceAge;
                            float d = Noise.fractal(x, y, z, freq, octaves);
                            float dx = (Noise.fractal(x + eps, y, z, freq, octaves) - d) * mag;
                            float dy = (Noise.fractal(x, y + eps, z, freq, octaves) - d) * mag;
                            float dz = (Noise.fractal(x, y, z + eps, freq, octaves) - d) * mag;
                            float before = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy + p.vz * p.vz);
                            p.vx += dx;
                            p.vy += dy;
                            p.vz += dz;
                            float after = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy + p.vz * p.vz);
                            if (after > 1.0E-6F) {
                                // Turned, not sped up: the speed it had.
                                float k = before / after;
                                p.vx *= k;
                                p.vy *= k;
                                p.vz *= k;
                            }
                        }
                    }
                    case XrDefs.RANDOM_DISPLACE -> {
                        for (int i = 0; i < count; i++) {
                            a.d(0).generate(random, scratch);
                            P p = particles[i];
                            p.x += scratch[0] * STEP;
                            p.y += scratch[1] * STEP;
                            p.z += scratch[2] * STEP;
                        }
                    }
                    case XrDefs.RANDOM_ACCEL -> {
                        for (int i = 0; i < count; i++) {
                            a.d(0).generate(random, scratch);
                            P p = particles[i];
                            p.vx += scratch[0] * STEP;
                            p.vy += scratch[1] * STEP;
                            p.vz += scratch[2] * STEP;
                        }
                    }
                    default -> {
                        // Unported action: skipped (logged once per effect at load).
                    }
                }
            }
            if (def.has(XrDefs.FRAMED) && def.has(XrDefs.ANIMATED) && def.frameCount() > 0) {
                for (int i = 0; i < count; i++) {
                    P p = particles[i];
                    p.frame += (p.ccw ? -1.0F : 1.0F) * def.frameSpeed() * STEP;
                    p.frame = ((p.frame % def.frameCount()) + def.frameCount()) % def.frameCount();
                }
            }
        }

        private void remove(int i) {
            P dead = particles[i];
            particles[i] = particles[--count];
            particles[count] = dead;
        }
    }

    /**
     * Plays {@code group} (e.g. {@code anomaly2/electra2_blast}) at {@code origin}, spread {@code scale} times as wide
     * and {@code height} times as high (sprites {@code height} times as big); empty if it does not load.
     * {@code loop}: starts over whenever it runs out, until stopped.
     */
    public static Optional<Playing> play(String group, Vec3 origin, float scale, float height, boolean loop) {
        Optional<Group> def = GROUPS.computeIfAbsent(group, XrParticles::loadGroup);
        if (def.isEmpty()) {
            return Optional.empty();
        }
        Playing playing = new Playing(def.get(), origin, scale, height, loop);
        PLAYING.add(playing);
        return Optional.of(playing);
    }

    private static Optional<Group> loadGroup(String name) {
        return read(XrDefs.location(name, "pg")).map(Group::parse);
    }

    private static Optional<Effect> effect(String name) {
        return EFFECTS.computeIfAbsent(name, n -> read(XrDefs.location(n, "pe")).map(text -> {
            Effect e = Effect.parse(n, text);
            for (Action a : e.actions()) {
                if (!List.of(XrDefs.KILL_OLD, XrDefs.SOURCE, XrDefs.TARGET_COLOR, XrDefs.TARGET_SIZE, XrDefs.MOVE,
                        XrDefs.TARGET_VELOCITY, XrDefs.GRAVITY, XrDefs.TARGET_ROTATE, XrDefs.TURBULENCE,
                        XrDefs.RANDOM_DISPLACE, XrDefs.RANDOM_ACCEL).contains(a.type())) {
                    LOGGER.warn("X-Ray effect {}: action type {} not ported, skipped", n, a.type());
                }
            }
            return e;
        }));
    }

    private static Optional<String> read(ResourceLocation location) {
        Optional<net.minecraft.server.packs.resources.Resource> resource = Minecraft.getInstance().getResourceManager()
                .getResource(location);
        if (resource.isEmpty()) {
            // Distortion effects are left out by the asset tool on purpose; anything else missing is worth saying.
            LOGGER.debug("X-Ray particles: {} not found", location);
            return Optional.empty();
        }
        try (InputStream in = resource.get().open()) {
            return Optional.of(new String(in.readAllBytes(), StandardCharsets.ISO_8859_1).replace("\r", ""));
        } catch (IOException e) {
            LOGGER.warn("X-Ray particles: could not read {}", location, e);
            return Optional.empty();
        }
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.0F : Math.min((now - lastNanos) / 1.0E9F, 0.25F);
        lastNanos = now;
        if (mc.level == null || PLAYING.isEmpty()) {
            return;
        }
        RandomSource random = mc.level.random;
        if (!mc.isPaused()) {
            for (Playing p : PLAYING) {
                p.carry += dt;
                int steps = 0;
                while (p.carry >= STEP && steps < MAX_STEPS) {
                    p.carry -= STEP;
                    p.step(random);
                    steps++;
                }
                p.carry = Math.min(p.carry, STEP);
            }
            PLAYING.removeIf(Playing::finished);
        }
        render(event, mc);
    }

    private static void render(RenderLevelStageEvent event, Minecraft mc) {
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f up = camera.getUpVector();
        Vector3f left = camera.getLeftVector();
        float[] top = {up.x, up.y, up.z};
        float[] right = {-left.x, -left.y, -left.z};
        Vector3f look = camera.getLookVector();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = pose.last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Map<RenderType, List<Runnable>> batches = new LinkedHashMap<>();
        for (Playing playing : PLAYING) {
            List<Running> all = new ArrayList<>(playing.effects);
            all.addAll(playing.children);
            for (Running r : all) {
                if (r.count == 0 || r.def.texture() == null || !r.def.has(XrDefs.SPRITE)) {
                    continue;
                }
                RenderType type = WFRenderTypes.xray(r.def.texture(), r.def.blend());
                batches.computeIfAbsent(type, k -> new ArrayList<>()).add(() -> {
                    VertexConsumer out = buffers.getBuffer(type);
                    for (int i = 0; i < r.count; i++) {
                        sprite(out, matrix, playing, r, r.particles[i], top, right, look);
                    }
                });
            }
        }
        for (Map.Entry<RenderType, List<Runnable>> batch : batches.entrySet()) {
            batch.getValue().forEach(Runnable::run);
            buffers.endBatch(batch.getKey());
        }
        pose.popPose();
    }

    /** {@code ParticleRenderStream} + {@code FillSprite}: one particle's quad. */
    private static void sprite(VertexConsumer out, Matrix4f matrix, Playing playing, Running run, P p,
                               float[] camTop, float[] camRight, Vector3f look) {
        Effect def = run.def;
        float scale = playing.scale;
        float px = (float) playing.origin.x + (run.ox + p.x) * scale;
        float py = (float) playing.origin.y + (run.oy + p.y) * playing.height;
        float pz = (float) playing.origin.z + (run.oz + p.z) * scale;
        float r1 = p.sx * 0.5F * playing.height;
        float r2 = p.sy * 0.5F * playing.height;
        float[] t = camTop;
        float[] r = camRight;
        if (def.has(XrDefs.ALIGN_TO_PATH)) {
            float speed = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy + p.vz * p.vz);
            if (speed < 1.0E-4F && def.has(XrDefs.WORLD_ALIGN)) {
                float[][] m = hpb(def.defaultRotation()[1], def.defaultRotation()[0], def.defaultRotation()[2]);
                t = m[2];
                r = m[0];
            } else {
                float[] dir = speed >= 1.0E-4F ? new float[]{p.vx / speed, p.vy / speed, p.vz / speed}
                        : hpb(def.defaultRotation()[1], def.defaultRotation()[0], def.defaultRotation()[2])[2];
                t = dir;
                // R = T x camera direction, normalised.
                float[] rr = {t[1] * look.z - t[2] * look.y, t[2] * look.x - t[0] * look.z,
                        t[0] * look.y - t[1] * look.x};
                float len = (float) Math.sqrt(rr[0] * rr[0] + rr[1] * rr[1] + rr[2] * rr[2]);
                r = len > 1.0E-6F ? new float[]{rr[0] / len, rr[1] / len, rr[2] / len} : camRight;
            }
        }
        float sin = (float) Math.sin(p.rot);
        float cos = (float) Math.cos(p.rot);
        float[] vr = new float[3];
        float[] vt = new float[3];
        for (int i = 0; i < 3; i++) {
            vr[i] = r1 * (t[i] * sin + r[i] * cos);
            vt[i] = r2 * (t[i] * cos - r[i] * sin);
        }
        float u0 = 0.0F;
        float v0 = 0.0F;
        float u1 = 1.0F;
        float v1 = 1.0F;
        if (def.has(XrDefs.FRAMED)) {
            int frame = (int) p.frame;
            u0 = (frame % Math.max(def.frameDimX(), 1)) * def.frameU();
            v0 = (frame / Math.max(def.frameDimX(), 1)) * def.frameV();
            u1 = u0 + def.frameU();
            v1 = v0 + def.frameV();
        }
        float cr = clamp(p.r);
        float cg = clamp(p.g);
        float cb = clamp(p.b);
        float ca = clamp(p.a);
        // d (-vt-vr), a (vt-vr), b (vt+vr), c (-vt+vr): lt/rb as FillSprite assigns them.
        out.addVertex(matrix, px - vt[0] - vr[0], py - vt[1] - vr[1], pz - vt[2] - vr[2]).setUv(u0, v1)
                .setColor(cr, cg, cb, ca);
        out.addVertex(matrix, px + vt[0] - vr[0], py + vt[1] - vr[1], pz + vt[2] - vr[2]).setUv(u0, v0)
                .setColor(cr, cg, cb, ca);
        out.addVertex(matrix, px + vt[0] + vr[0], py + vt[1] + vr[1], pz + vt[2] + vr[2]).setUv(u1, v0)
                .setColor(cr, cg, cb, ca);
        out.addVertex(matrix, px - vt[0] + vr[0], py - vt[1] + vr[1], pz - vt[2] + vr[2]).setUv(u1, v1)
                .setColor(cr, cg, cb, ca);
    }

    private static float clamp(float v) {
        return v < 0.0F ? 0.0F : Math.min(v, 1.0F);
    }

    /** {@code Fmatrix::setHPB}: rows i, j, k. */
    static float[][] hpb(float h, float p, float b) {
        float sh = (float) Math.sin(h);
        float ch = (float) Math.cos(h);
        float sp = (float) Math.sin(p);
        float cp = (float) Math.cos(p);
        float sb = (float) Math.sin(b);
        float cb = (float) Math.cos(b);
        float cc = ch * cb;
        float cs = ch * sb;
        float sc = sh * cb;
        float ss = sh * sb;
        return new float[][]{{cc - sp * ss, -cp * sb, sp * cs + sc}, {sp * sc + cs, cp * cb, ss - sp * cc},
                {-cp * sh, sp, cp * ch}};
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            PLAYING.clear();
        }
    }

    /** For debug output: playing groups, live particles. */
    public static String report() {
        int particles = 0;
        for (Playing p : PLAYING) {
            for (Running r : p.effects) {
                particles += r.count;
            }
            for (Running r : p.children) {
                particles += r.count;
            }
        }
        return "xray particles: " + PLAYING.size() + " group(s), " + particles + " particle(s), "
                + EFFECTS.size() + " effect(s) loaded";
    }

    /** Perlin-style gradient noise, summed in octaves as {@code fractalsum3} does (frequency x 2.059 each). */
    static final class Noise {
        private static final int[] PERM = new int[512];

        static {
            RandomSource r = RandomSource.create(1234L);
            int[] p = new int[256];
            for (int i = 0; i < 256; i++) {
                p[i] = i;
            }
            for (int i = 255; i > 0; i--) {
                int j = r.nextInt(i + 1);
                int tmp = p[i];
                p[i] = p[j];
                p[j] = tmp;
            }
            for (int i = 0; i < 512; i++) {
                PERM[i] = p[i & 255];
            }
        }

        private Noise() {
        }

        static float fractal(float x, float y, float z, float freq, int octaves) {
            float sum = 0.0F;
            float boost = freq;
            float f = freq;
            for (int i = 0; i < octaves; i++) {
                sum += noise(x * f, y * f, z * f) / f;
                f *= 2.059F;
            }
            return sum * boost;
        }

        static float noise(float x, float y, float z) {
            int xi = (int) Math.floor(x) & 255;
            int yi = (int) Math.floor(y) & 255;
            int zi = (int) Math.floor(z) & 255;
            float xf = x - (float) Math.floor(x);
            float yf = y - (float) Math.floor(y);
            float zf = z - (float) Math.floor(z);
            float u = fade(xf);
            float v = fade(yf);
            float w = fade(zf);
            int a = PERM[xi] + yi;
            int aa = PERM[a] + zi;
            int ab = PERM[a + 1] + zi;
            int b = PERM[xi + 1] + yi;
            int ba = PERM[b] + zi;
            int bb = PERM[b + 1] + zi;
            return lerp(w, lerp(v, lerp(u, grad(PERM[aa], xf, yf, zf), grad(PERM[ba], xf - 1, yf, zf)),
                            lerp(u, grad(PERM[ab], xf, yf - 1, zf), grad(PERM[bb], xf - 1, yf - 1, zf))),
                    lerp(v, lerp(u, grad(PERM[aa + 1], xf, yf, zf - 1), grad(PERM[ba + 1], xf - 1, yf, zf - 1)),
                            lerp(u, grad(PERM[ab + 1], xf, yf - 1, zf - 1), grad(PERM[bb + 1], xf - 1, yf - 1, zf - 1))));
        }

        private static float fade(float t) {
            return t * t * t * (t * (t * 6 - 15) + 10);
        }

        private static float lerp(float t, float a, float b) {
            return a + t * (b - a);
        }

        private static float grad(int hash, float x, float y, float z) {
            int h = hash & 15;
            float u = h < 8 ? x : y;
            float v = h < 4 ? y : h == 12 || h == 14 ? x : z;
            return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
        }
    }
}
