package com.wf.wflib.artifact.api.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wf.gemrender.gltf.blend.AnimationBlend;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.GltfAnimation;
import com.wf.gemrender.gltf.GltfPose;
import com.wf.gemrender.gltf.morph.GltfMorphLayout;
import com.wf.wflib.artifact.api.DetectorTier;
import com.wf.wflib.view.api.HandClip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.Map;
import com.wf.wflib.core.api.WFLib;

/**
 * The detector in first person, as GAMMA plays it (Devices of Anomaly Redone): one state at a time, each a baked
 * hand clip ({@code detector/hands/<tier>/<state>.json}: fists and the device body, view space) and a device clip
 * (the parts on {@code models/detector/lowpoly/<tier>.glb}), crossfading between states. A viewmodel calls
 * {@link #update} each frame, then draws the left arm on {@link #left}, the device at {@link #device} posed by
 * {@link #blend}, and faces on {@link #bone}s. Client, render thread.
 */
public final class DetectorAnim {
    /** DAR's {@code anm_*} states used here; {@code loops} until left. */
    public enum State {
        SHOW("anm_show", false), HIDE("anm_hide", false), IDLE("anm_idle", true), MOVING("anm_idle_moving", true),
        CROUCH_MOVING("anm_idle_moving_crouch", true), SPRINT("anm_idle_sprint", true),
        ZOOM_IN("anm_zoom_in", false), ZOOM("anm_idle_zoom", true), ZOOM_OUT("anm_zoom_out", false),
        THROW_START("anm_throw_start", false), THROW("anm_throw", true), THROW_END("anm_throw_end", false);

        final String key;
        final boolean loops;

        State(String key, boolean loops) {
            this.key = key;
            this.loops = loops;
        }
    }

    /** {@code speed}: DAR's state multiplier, already in the baked hand clip; the device clip takes it here. */
    private record Clip(HandClip hands, String device, float speed) {
    }

    private static final float CROSSFADE = 0.15F;
    private static final Map<DetectorTier, Map<State, Clip>> CLIPS = new EnumMap<>(DetectorTier.class);
    private static final Map<DetectorTier, float[]> AIM = new EnumMap<>(DetectorTier.class);

    @Nullable
    private static DetectorTier tier;
    private static State state = State.IDLE;
    private static float seconds;
    @Nullable
    private static DetectorTier fromTier;
    private static State from = State.IDLE;
    private static float fromSeconds;
    /** Left of the crossfade from {@link #from}, seconds. */
    private static float fade;
    /** 0..1: how far into the zoomed placement (aim_hud_offset_pos). */
    private static float zoomed;

    private static final AnimationBlend BLEND = new AnimationBlend(2);
    private static final GltfPose.Scratch SCRATCH = new GltfPose.Scratch();
    private static Matrix4f[] palette = new Matrix4f[0];
    private static final Vector3f V = new Vector3f();
    private static final Vector3f V2 = new Vector3f();
    private static final Quaternionf Q = new Quaternionf();
    private static final Quaternionf Q2 = new Quaternionf();

    private DetectorAnim() {
    }

    /**
     * One frame: {@code held} the detector out (null: none), {@code zoom} raised to read, {@code throwing} the other
     * hand throwing a bolt, {@code dt} seconds since the last frame.
     */
    public static void update(LocalPlayer player, @Nullable DetectorTier held, boolean zoom, boolean throwing,
                              float dt) {
        fade = Math.max(0.0F, fade - dt);
        fromSeconds += dt;
        if (tier == null) {
            if (held != null) {
                enter(held, State.SHOW);
            }
            return;
        }
        Clip clip = clip(tier, state);
        seconds += dt;
        boolean done = clip == null || seconds >= clip.hands.duration;
        if (held != tier && state != State.HIDE) {
            enter(tier, State.HIDE);
            return;
        }
        if (state == State.HIDE) {
            if (done) {
                tier = null;
                if (held != null) {
                    enter(held, State.SHOW);
                }
            }
            return;
        }
        zoomed = Math.clamp(zoomed + (zoom ? dt : -dt) / 0.3F, 0.0F, 1.0F);
        State next = next(player, zoom, throwing, done);
        if (next != state) {
            enter(tier, next);
        } else if (state.loops && done) {
            seconds -= clip.hands.duration;
        }
    }

    /** Where the state machine goes from {@link #state} this frame. */
    private static State next(LocalPlayer player, boolean zoom, boolean throwing, boolean done) {
        return switch (state) {
            case SHOW, ZOOM_OUT, THROW_END -> done ? rest(player) : state;
            case ZOOM_IN -> done ? State.ZOOM : state;
            case ZOOM -> zoom ? state : State.ZOOM_OUT;
            case THROW_START -> done ? State.THROW : state;
            case THROW -> throwing ? state : State.THROW_END;
            default -> zoom ? State.ZOOM_IN : throwing ? State.THROW_START : rest(player);
        };
    }

    private static State rest(LocalPlayer player) {
        boolean moving = player.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4D && player.onGround();
        if (!moving) {
            return State.IDLE;
        }
        return player.isSprinting() ? State.SPRINT : player.isCrouching() ? State.CROUCH_MOVING : State.MOVING;
    }

    private static void enter(DetectorTier to, State s) {
        if (tier != null) {
            fromTier = tier;
            from = state;
            fromSeconds = seconds;
            fade = CROSSFADE;
        }
        tier = to;
        state = s;
        seconds = 0.0F;
    }

    /** A detector to draw: one out, or one being put away. */
    @Nullable
    public static DetectorTier shown() {
        return tier;
    }

    public static State state() {
        return state;
    }

    /** Seconds into {@link #state}, on its own clock. */
    public static float seconds() {
        return seconds;
    }

    /** The left fist (view space, x right, y up, -z ahead) and hand frame ({@code ArmViewPose.solve}'s hand). */
    public static void left(Vector3f pos, Quaternionf rot) {
        track(null, pos, rot);
    }

    /**
     * The device's model matrix this frame from {@code view}: the body on its hand-clip track, its own clip's body
     * motion taken back out (the hand clip already carries it), so only its parts move on it.
     */
    public static Matrix4f device(Matrix4f view, Matrix4f out) {
        track("detector", V, Q);
        Vector3f c = bodyCentre(tier);
        out.set(view).translate(V).rotate(Q).translate(-c.x, -c.y, -c.z);
        GemRenderGltfModel model = DetectorClient.model(tier);
        if (model != null) {
            pose(model);
            int body = model.layout().nodeTable().slotOfName("wpn_body");
            if (body >= 0) {
                out.mul(rest(model)[body]).mul(new Matrix4f(palette[body]).invert());
            }
        }
        return out;
    }

    /** {@link #shown}'s device clips now, crossfaded: hand it to {@code DirectRenderer.submit}. */
    public static AnimationBlend blend() {
        BLEND.clear();
        GemRenderGltfModel model = DetectorClient.model(tier);
        if (model == null) {
            return BLEND;
        }
        float w = fade > 0.0F && fromTier == tier ? fade / CROSSFADE : 0.0F;
        Clip now = clip(tier, state);
        BLEND.override(now == null ? null : model.animation(now.device), deviceTime(model, now, state, seconds),
                1.0F - w);
        if (w > 0.0F) {
            Clip before = clip(tier, from);
            BLEND.override(before == null ? null : model.animation(before.device), deviceTime(model, before, from,
                    fromSeconds), w);
        }
        return BLEND;
    }

    /** Model-space matrix of node {@code name} under {@link #blend}, after {@link #device}; null if none such. */
    @Nullable
    public static Matrix4f bone(String name, Matrix4f out) {
        GemRenderGltfModel model = DetectorClient.model(tier);
        if (model == null) {
            return null;
        }
        pose(model);
        int slot = model.layout().nodeTable().slotOfName(name);
        return slot < 0 ? null : out.set(palette[slot]);
    }

    private static void pose(GemRenderGltfModel model) {
        if (palette.length != model.jointCount()) {
            palette = model.newPalette();
        }
        GltfPose.evaluate(model.layout(), blend(), palette, GltfMorphLayout.NONE, null, SCRATCH);
    }

    private static final Map<DetectorTier, Matrix4f[]> REST = new EnumMap<>(DetectorTier.class);

    private static Matrix4f[] rest(GemRenderGltfModel model) {
        return REST.computeIfAbsent(tier, t -> model.restPalette());
    }

    private static float deviceTime(GemRenderGltfModel model, @Nullable Clip clip, State s, float t) {
        GltfAnimation a = clip == null ? null : model.animation(clip.device);
        if (a == null || a.duration() <= 0.0F) {
            return 0.0F;
        }
        float at = t * clip.speed;
        return s.loops ? at % a.duration() : Math.min(at, a.duration());
    }

    /** Hand-clip track ({@code null}: the left fist) now, crossfaded, with the zoomed offset. */
    private static void track(@Nullable String name, Vector3f pos, Quaternionf rot) {
        sample(tier, state, seconds, name, pos, rot);
        if (fade > 0.0F && fromTier != null) {
            sample(fromTier, from, fromSeconds, name, V2, Q2);
            float w = fade / CROSSFADE;
            pos.lerp(V2, w);
            rot.nlerp(Q2, w);
        }
        float[] aim = AIM.get(tier);
        if (aim != null && zoomed > 0.0F) {
            pos.add(aim[0] * zoomed, aim[1] * zoomed, -aim[2] * zoomed);
        }
    }

    private static void sample(DetectorTier t, State s, float at, @Nullable String name, Vector3f pos,
                               Quaternionf rot) {
        Clip clip = clip(t, s);
        if (clip == null) {
            pos.zero();
            rot.identity();
            return;
        }
        float[][] track = name == null ? clip.hands.left : clip.hands.prop(name);
        float time = s.loops ? at % clip.hands.duration : Math.min(at, clip.hands.duration);
        clip.hands.sample(track, time, pos, rot);
    }

    /** A display on a bone (bone-local, m): centre, unit right and ahead in its plane, half its extent. */
    public record Face(String bone, Vector3fc centre, Vector3fc right, Vector3fc ahead, float half) {
    }

    private static final Face VELES_FACE = new Face("cover_power", new Vector3f(0.0009F, 0.0050F, -0.0009F),
            new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, 1.0F, 0.0F), 0.025F);
    private static final Face BEAR_FACE = new Face("screen_bone", new Vector3f(0.0170F, 0.0002F, -0.0127F),
            new Vector3f(0.559F, 0.0F, 0.829F), new Vector3f(0.829F, 0.0F, -0.559F), 0.010F);

    /** The Veles' screen ({@code cover_power}: DAR's detector_ui_bone), the Bear's ({@code screen_bone}); Echo none. */
    @Nullable
    public static Face face(DetectorTier t) {
        return switch (t) {
            case ECHO -> null;
            case BEAR -> BEAR_FACE;
            case VELES -> VELES_FACE;
        };
    }

    /**
     * {@code face}'s frame on the posed device ({@code device}: {@link #device}): x right, y out of the face, -z
     * ahead, origin its centre. Null if the bone is missing.
     */
    @Nullable
    public static Matrix4f faceFrame(Face face, Matrix4f device, Matrix4f out) {
        Matrix4f bone = bone(face.bone(), new Matrix4f());
        if (bone == null) {
            return null;
        }
        Vector3f normal = new Vector3f(face.right()).cross(face.ahead());
        Matrix4f basis = new Matrix4f().set(face.right().x(), face.right().y(), face.right().z(), 0.0F,
                normal.x, normal.y, normal.z, 0.0F, -face.ahead().x(), -face.ahead().y(), -face.ahead().z(), 0.0F,
                face.centre().x(), face.centre().y(), face.centre().z(), 1.0F);
        return out.set(device).mul(bone).mul(basis);
    }

    /**
     * The device body's centre in model space: where its hand-clip track points (the bake takes the body bone's
     * vertex bounds; these are the low-poly bodies' bounds).
     */
    private static Vector3f bodyCentre(DetectorTier t) {
        return switch (t) {
            case ECHO -> ECHO_BODY;
            case BEAR -> BEAR_BODY;
            case VELES -> VELES_BODY;
        };
    }

    private static final Vector3f ECHO_BODY = new Vector3f(-0.0012F, -0.0292F, 0.0057F);
    private static final Vector3f BEAR_BODY = new Vector3f(0.0F, -0.0010F, 0.0F);
    private static final Vector3f VELES_BODY = new Vector3f(-0.0022F, -0.0041F, 0.0300F);

    @Nullable
    private static Clip clip(DetectorTier t, State s) {
        return CLIPS.computeIfAbsent(t, DetectorAnim::load).get(s);
    }

    /** {@code states.json} (DAR: state -> [hand clip, device clip, speed]) and the baked hand clips. */
    private static Map<State, Clip> load(DetectorTier t) {
        var resources = Minecraft.getInstance().getResourceManager();
        Map<State, Clip> out = new EnumMap<>(State.class);
        ResourceLocation states = ResourceLocation.fromNamespaceAndPath(WFLib.MODID,
                "models/detector/lowpoly/states.json");
        try (Reader in = resources.openAsReader(states)) {
            JsonObject tierJson = JsonParser.parseReader(in).getAsJsonObject().getAsJsonObject(t.id);
            JsonObject rows = tierJson.getAsJsonObject("states");
            for (State s : State.values()) {
                JsonArray row = rows.getAsJsonArray(s.key);
                ResourceLocation hands = ResourceLocation.fromNamespaceAndPath(WFLib.MODID,
                        "detector/hands/" + t.id + "/" + s.key + ".json");
                if (row != null && resources.getResource(hands).isPresent()) {
                    out.put(s, new Clip(HandClip.load(resources, hands), row.get(1).getAsString(),
                            row.size() > 2 ? row.get(2).getAsFloat() : 1.0F));
                }
            }
            String[] aim = tierJson.getAsJsonObject("hud").get("aim_hud_offset_pos").getAsString().split(",");
            AIM.put(t, new float[] {Float.parseFloat(aim[0].trim()), Float.parseFloat(aim[1].trim()),
                    Float.parseFloat(aim[2].trim())});
        } catch (IOException e) {
            throw new UncheckedIOException(states.toString(), e);
        }
        return out;
    }
}
