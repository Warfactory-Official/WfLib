package com.wf.wflib.client.xray;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * STALKER (X-Ray) particle definitions as their SDK text exports them: {@code .pe} effects and {@code .pg} groups
 * (copied by devtools/xray-assets.py). An action's parameters are numbered per kind ({@code flt_0000}, {@code vec_0000},
 * {@code domain_..._0000}, {@code bool_0000}, {@code int_0000}) in the alphabetical order of their names, as the
 * editor keeps them in sorted maps ({@code EParticleAction::Load2}); the orders below are those names sorted.
 */
public final class XrDefs {
    private XrDefs() {
    }

    // PAPI::PActionEnum.
    static final int GRAVITY = 8;
    static final int KILL_OLD = 10;
    static final int MOVE = 12;
    static final int RANDOM_ACCEL = 15;
    static final int RANDOM_DISPLACE = 16;
    static final int SOURCE = 21;
    static final int TARGET_COLOR = 23;
    static final int TARGET_SIZE = 24;
    static final int TARGET_ROTATE = 25;
    static final int TARGET_VELOCITY = 27;
    static final int TURBULENCE = 30;

    // CPEDef flags.
    static final int SPRITE = 1;
    static final int FRAMED = 1 << 10;
    static final int ANIMATED = 1 << 11;
    static final int RANDOM_FRAME = 1 << 12;
    static final int RANDOM_PLAYBACK = 1 << 13;
    static final int TIME_LIMIT = 1 << 14;
    static final int ALIGN_TO_PATH = 1 << 15;
    static final int WORLD_ALIGN = 1 << 20;
    static final int FACE_ALIGN = 1 << 21;

    /** {@code particles\add}: ONE, ONE; {@code particles\alpha_add}: SRC_ALPHA, ONE; {@code particles\blend}: over. */
    public enum Blend {
        ADD, ALPHA_ADD, BLEND
    }

    /** Ini sections, {@code [name]} -> key -> value; later keys win. */
    static Map<String, Map<String, String>> ini(String text) {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        Map<String, String> section = null;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            int comment = line.indexOf(';');
            if (comment >= 0) {
                line = line.substring(0, comment).trim();
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                section = out.computeIfAbsent(line.substring(1, line.length() - 1), k -> new LinkedHashMap<>());
            } else if (section != null && line.contains("=")) {
                int eq = line.indexOf('=');
                section.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
        }
        return out;
    }

    static float f(Map<String, String> s, String key, float fallback) {
        String v = s.get(key);
        return v == null || v.isEmpty() ? fallback : Float.parseFloat(v);
    }

    static float[] v(Map<String, String> s, String key) {
        String v = s.get(key);
        float[] out = new float[3];
        if (v != null) {
            String[] parts = v.split(",");
            for (int i = 0; i < 3 && i < parts.length; i++) {
                out[i] = Float.parseFloat(parts[i].trim());
            }
        }
        return out;
    }

    static boolean b(Map<String, String> s, String key) {
        String v = s.get(key);
        return v != null && (v.equalsIgnoreCase("on") || v.equalsIgnoreCase("true") || v.equals("1"));
    }

    /** {@code PAPI::pDomain}: a region to pick points from, built from the editor's v0, v1, v2 (a0..a8). */
    public record Domain(int type, float[] a) {
        static Domain of(@Nullable Map<String, String> s) {
            if (s == null) {
                return new Domain(0, new float[9]);
            }
            float[] a = new float[9];
            float[][] vs = {v(s, "v0"), v(s, "v1"), v(s, "v2")};
            for (int i = 0; i < 3; i++) {
                System.arraycopy(vs[i], 0, a, i * 3, 3);
            }
            return new Domain((int) f(s, "type", 0.0F), a);
        }

        /** {@code pDomain::Generate}, into {@code out}. */
        void generate(RandomSource r, float[] out) {
            switch (type) {
                case 1 -> { // line: a0..2 to a3..5
                    float t = r.nextFloat();
                    for (int i = 0; i < 3; i++) {
                        out[i] = a[i] + (a[3 + i] - a[i]) * t;
                    }
                }
                case 2 -> { // triangle
                    float r1 = r.nextFloat();
                    float r2 = r.nextFloat();
                    if (r1 + r2 >= 1.0F) {
                        r1 = 1.0F - r1;
                        r2 = 1.0F - r2;
                    }
                    for (int i = 0; i < 3; i++) {
                        out[i] = a[i] + (a[3 + i] - a[i]) * r1 + (a[6 + i] - a[i]) * r2;
                    }
                }
                case 4 -> { // box
                    for (int i = 0; i < 3; i++) {
                        float lo = Math.min(a[i], a[3 + i]);
                        float hi = Math.max(a[i], a[3 + i]);
                        out[i] = lo + (hi - lo) * r.nextFloat();
                    }
                }
                case 5 -> { // sphere: centre a0..2, radii a3, a4
                    float x = r.nextFloat() - 0.5F;
                    float y = r.nextFloat() - 0.5F;
                    float z = r.nextFloat() - 0.5F;
                    float len = (float) Math.sqrt(x * x + y * y + z * z);
                    if (len > 1.0E-6F) {
                        x /= len;
                        y /= len;
                        z /= len;
                    }
                    float outer = Math.max(a[3], a[4]);
                    float inner = Math.min(a[3], a[4]);
                    float rad = outer == inner ? outer : inner + r.nextFloat() * (outer - inner);
                    out[0] = a[0] + x * rad;
                    out[1] = a[1] + y * rad;
                    out[2] = a[2] + z * rad;
                }
                case 6, 7 -> { // cylinder, cone (apex a0..2): axis to a3..5, radii a6, a7
                    float[] n = {a[3] - a[0], a[4] - a[1], a[5] - a[2]};
                    float[][] uv = frame(n);
                    float dist = r.nextFloat();
                    float theta = r.nextFloat() * (float) (Math.PI * 2.0);
                    float outer = Math.max(a[6], a[7]);
                    float inner = Math.min(a[6], a[7]);
                    float rad = inner + r.nextFloat() * (outer - inner);
                    float x = rad * (float) Math.cos(theta);
                    float y = rad * (float) Math.sin(theta);
                    if (type == 7) {
                        x *= dist;
                        y *= dist;
                    }
                    for (int i = 0; i < 3; i++) {
                        out[i] = a[i] + n[i] * dist + uv[0][i] * x + uv[1][i] * y;
                    }
                }
                case 8 -> { // blob: centre a0..2, deviation a3
                    for (int i = 0; i < 3; i++) {
                        out[i] = a[i] + (float) r.nextGaussian() * a[3];
                    }
                }
                case 9 -> { // disc: centre a0..2, normal a3..5, radii a6, a7
                    float[][] uv = frame(new float[]{a[3], a[4], a[5]});
                    float theta = r.nextFloat() * (float) (Math.PI * 2.0);
                    float outer = Math.max(a[6], a[7]);
                    float inner = Math.min(a[6], a[7]);
                    float rad = inner + r.nextFloat() * (outer - inner);
                    float x = rad * (float) Math.cos(theta);
                    float y = rad * (float) Math.sin(theta);
                    for (int i = 0; i < 3; i++) {
                        out[i] = a[i] + uv[0][i] * x + uv[1][i] * y;
                    }
                }
                case 10 -> { // rectangle: corner a0..2, edges a3..5, a6..8
                    float s = r.nextFloat();
                    float t = r.nextFloat();
                    for (int i = 0; i < 3; i++) {
                        out[i] = a[i] + a[3 + i] * s + a[6 + i] * t;
                    }
                }
                default -> System.arraycopy(a, 0, out, 0, 3); // point (and plane)
            }
        }

        /** Two unit vectors square to {@code n} ({@code pDomain}'s u, v). */
        private static float[][] frame(float[] n) {
            float len = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
            float[] nn = len > 1.0E-6F ? new float[]{n[0] / len, n[1] / len, n[2] / len} : new float[]{0, 1, 0};
            float[] basis = Math.abs(nn[0]) > 0.999F ? new float[]{0, 1, 0} : new float[]{1, 0, 0};
            float d = basis[0] * nn[0] + basis[1] * nn[1] + basis[2] * nn[2];
            float[] u = {basis[0] - nn[0] * d, basis[1] - nn[1] * d, basis[2] - nn[2] * d};
            float ul = (float) Math.sqrt(u[0] * u[0] + u[1] * u[1] + u[2] * u[2]);
            u = new float[]{u[0] / ul, u[1] / ul, u[2] / ul};
            float[] v = {nn[1] * u[2] - nn[2] * u[1], nn[2] * u[0] - nn[0] * u[2], nn[0] * u[1] - nn[1] * u[0]};
            return new float[][]{u, v};
        }
    }

    /** One action, its values unpacked by kind; {@link XrParticles} runs it by {@code type}. */
    public record Action(int type, float[] floats, float[][] vectors, boolean[] bools, Domain[] domains, int[] ints) {
        static Action of(String sectionName, Map<String, String> s, Map<String, Map<String, String>> all) {
            int type = (int) XrDefs.f(s, "action_type", -1.0F);
            List<Float> floats = new ArrayList<>();
            List<float[]> vectors = new ArrayList<>();
            List<Boolean> bools = new ArrayList<>();
            List<Integer> ints = new ArrayList<>();
            for (int i = 0; s.containsKey(String.format(Locale.ROOT, "flt_%04d", i)); i++) {
                floats.add(XrDefs.f(s, String.format(Locale.ROOT, "flt_%04d", i), 0.0F));
            }
            for (int i = 0; s.containsKey(String.format(Locale.ROOT, "vec_%04d", i)); i++) {
                vectors.add(XrDefs.v(s, String.format(Locale.ROOT, "vec_%04d", i)));
            }
            for (int i = 0; s.containsKey(String.format(Locale.ROOT, "bool_%04d", i)); i++) {
                bools.add(XrDefs.b(s, String.format(Locale.ROOT, "bool_%04d", i)));
            }
            for (int i = 0; s.containsKey(String.format(Locale.ROOT, "int_%04d", i)); i++) {
                ints.add((int) XrDefs.f(s, String.format(Locale.ROOT, "int_%04d", i), 0.0F));
            }
            List<Domain> domains = new ArrayList<>();
            for (int i = 0; ; i++) {
                Map<String, String> d = all.get(String.format(Locale.ROOT, "domain_%s_%04d", sectionName, i));
                if (d == null) {
                    break;
                }
                domains.add(Domain.of(d));
            }
            float[] fa = new float[floats.size()];
            for (int i = 0; i < fa.length; i++) {
                fa[i] = floats.get(i);
            }
            boolean[] ba = new boolean[bools.size()];
            for (int i = 0; i < ba.length; i++) {
                ba[i] = bools.get(i);
            }
            return new Action(type, fa, vectors.toArray(new float[0][]), ba, domains.toArray(new Domain[0]),
                    ints.stream().mapToInt(Integer::intValue).toArray());
        }

        float f(int i) {
            return i < floats.length ? floats[i] : 0.0F;
        }

        float[] v(int i) {
            return i < vectors.length ? vectors[i] : new float[3];
        }

        boolean b(int i) {
            return i < bools.length && bools[i];
        }

        Domain d(int i) {
            return i < domains.length ? domains[i] : new Domain(0, new float[9]);
        }
    }

    /** A {@code .pe}: sprite, frames, actions. */
    public record Effect(String name, int flags, int maxParticles, float timeLimit, Blend blend,
                         @Nullable ResourceLocation texture, int frameDimX, int frameCount, float frameSpeed,
                         float frameU, float frameV, float[] defaultRotation, List<Action> actions) {
        static Effect parse(String name, String text) {
            Map<String, Map<String, String>> ini = ini(text);
            Map<String, String> e = ini.getOrDefault("_effect", Map.of());
            Map<String, String> sprite = ini.getOrDefault("sprite", Map.of());
            Map<String, String> frame = ini.getOrDefault("frame", Map.of());
            String shader = sprite.getOrDefault("shader", "").toLowerCase(Locale.ROOT);
            Blend blend = shader.endsWith("\\add") ? Blend.ADD : shader.endsWith("blend") ? Blend.BLEND
                    : Blend.ALPHA_ADD;
            // "sprite, distortion map": the sprite only.
            String tex = sprite.get("texture") == null ? null : sprite.get("texture").split(",")[0].trim();
            ResourceLocation texture = tex == null || tex.isEmpty() ? null : ResourceLocation.fromNamespaceAndPath(
                    "wflib", "textures/xray/" + tex.replace('\\', '/').toLowerCase(Locale.ROOT) + ".png");
            float[] texSize = frame.containsKey("tex_size") ? split2(frame.get("tex_size")) : new float[]{1, 1};
            List<Action> actions = new ArrayList<>();
            int count = (int) f(e, "action_count", 0.0F);
            for (int i = 0; i < count; i++) {
                String section = String.format(Locale.ROOT, "action_%04d", i);
                Map<String, String> s = ini.get(section);
                if (s != null) {
                    actions.add(Action.of(section, s, ini));
                }
            }
            int flags = (int) Long.parseLong(e.getOrDefault("flags", "0"));
            return new Effect(name, flags, (int) f(e, "max_particles", 0.0F),
                    (flags & TIME_LIMIT) != 0 ? f(ini.getOrDefault("timelimit", Map.of()), "value", 0.0F) : 0.0F,
                    blend, texture, (int) f(frame, "dim_x", 1.0F), (int) f(frame, "frame_count", 1.0F),
                    f(frame, "speed", 0.0F), texSize[0], texSize[1],
                    v(ini.getOrDefault("align_to_path", Map.of()), "default_rotation"), List.copyOf(actions));
        }

        private static float[] split2(String s) {
            String[] parts = s.split(",");
            return new float[]{Float.parseFloat(parts[0].trim()), Float.parseFloat(parts[1].trim())};
        }

        boolean has(int flag) {
            return (flags & flag) != 0;
        }
    }

    /**
     * One entry of a {@code .pg}: an effect, started at {@code time0}, stopped at {@code time1} (0: never), and the
     * children its particles start ({@code CPGDef::SEffect}): {@code birthChild} once where each is born,
     * {@code playChild} following each while it lives, {@code deadChild} once where each dies (null: none).
     */
    public record GroupEntry(String effect, float time0, float time1, @Nullable String birthChild,
                             @Nullable String playChild, @Nullable String deadChild) {
    }

    /** A {@code .pg}: its enabled effects (flags bit 2) and how long it runs (0: until stopped). */
    public record Group(float timeLimit, List<GroupEntry> entries) {
        static Group parse(String text) {
            Map<String, Map<String, String>> ini = ini(text);
            List<GroupEntry> entries = new ArrayList<>();
            for (Map.Entry<String, Map<String, String>> section : ini.entrySet()) {
                Map<String, String> s = section.getValue();
                if (!section.getKey().startsWith("effect_") || ((int) f(s, "flags", 0.0F) & 4) == 0) {
                    continue;
                }
                String name = s.get("effect_name");
                int flags = (int) f(s, "flags", 0.0F);
                if (name != null && !name.isEmpty()) {
                    entries.add(new GroupEntry(name, f(s, "time0", 0.0F), f(s, "time1", 0.0F),
                            child(s, "on_birth_child", flags, 1 << 5), child(s, "on_play_child", flags, 1 << 1),
                            child(s, "on_death_child", flags, 1 << 6)));
                }
            }
            return new Group(f(ini.getOrDefault("_group", Map.of()), "timelimit", 0.0F), List.copyOf(entries));
        }
    }

    @Nullable
    private static String child(Map<String, String> s, String key, int flags, int flag) {
        String name = s.get(key);
        return (flags & flag) != 0 && name != null && !name.isEmpty() ? name : null;
    }

    /** {@code effect\name} -> {@code wflib:xray/particles/effect/name.pe} (lower case, as the asset tool writes). */
    static ResourceLocation location(String name, String extension) {
        return ResourceLocation.fromNamespaceAndPath("wflib",
                "xray/particles/" + name.replace('\\', '/').toLowerCase(Locale.ROOT) + "." + extension);
    }
}
