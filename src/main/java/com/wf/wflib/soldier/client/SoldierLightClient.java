package com.wf.wflib.soldier.client;

import com.wf.gemrender.light.LightSink;
import com.wf.gemrender.light.Lights;
import com.wf.wflib.core.api.WFSystems;
import com.wf.wflib.ragdoll.api.client.RagdollView;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** A shadowed white spot, and its lens flare, at the head of every soldier the server says has its light on. */
public final class SoldierLightClient {
    /** Ticks a soldier stays lit without being told again (the server repeats every 40). */
    private static final int LAPSE = 100;
    static final float RANGE = 40, INNER = 5, OUTER = 22;
    /**
     * Peak irradiance at 1 m before falloff, and the near-field softening (m): a wall 4 m off was clipped to white out to
     * 1.3 m of its 1.6 m pool, so the focused centre and the taper read as one disc; this keeps the centre white and the
     * taper visible from 2 m to 30 m (the pool itself already grows with distance: it is angular).
     */
    private static final float INTENSITY = 140, SOFTNESS = 2.0F;
    static final int COLOR = 0xFFF4E6;
    /** At the headlamp model's lens: ahead of the eye and above it. */
    private static final float AHEAD = 0.44F, RISE = 0.16F;
    private static final Map<Integer, Long> LIT = new HashMap<>();

    /** A light's smoothed frame: what is drawn, so an aim jitter on the server never reaches the eye as a shaking beam. */
    static final class Frame {
        double x, y, z;
        float fx, fy, fz, ux, uy, uz;
        long nanos;
        boolean init;

        /** Eases toward the target. {@code tau}: seconds; a jump past 3 m (a teleport) lands at once. */
        void ease(double tx, double ty, double tz, Vector3f look, Vector3f up, float tau) {
            long now = System.nanoTime();
            float dt = init ? Math.min((now - nanos) * 1.0e-9F, 0.5F) : 1;
            nanos = now;
            double dx = tx - x, dy = ty - y, dz = tz - z;
            float a = !init || dx * dx + dy * dy + dz * dz > 9 ? 1 : 1 - (float) Math.exp(-dt / tau);
            init = true;
            x += dx * a;
            y += dy * a;
            z += dz * a;
            fx += (look.x - fx) * a;
            fy += (look.y - fy) * a;
            fz += (look.z - fz) * a;
            ux += (up.x - ux) * a;
            uy += (up.y - uy) * a;
            uz += (up.z - uz) * a;
            float l = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
            if (l > 1.0e-4F) {
                fx /= l;
                fy /= l;
                fz /= l;
            }
        }
    }

    private static final Map<Integer, Frame> FRAMES = new HashMap<>();

    private SoldierLightClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(FMLClientSetupEvent.class, e -> e.enqueueWork(() -> {
            Lights.register(SoldierLightClient::collect);
            com.wf.wflib.core.api.NightKey.UNHANDLED.set(com.wf.wflib.core.api.WFLib.MODID, SoldierLightClient::flipOwnLamp);
            NeoForge.EVENT_BUS.addListener(LampFlare::onRenderLevel);
        }));
    }

    /** The night-vision key with no goggles on: switches the lamp the player wears, if any. */
    private static boolean flipOwnLamp() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null || !com.wf.wflib.soldier.Headlamp.wearing(mc.player)
                || !com.wf.wflib.soldier.SoldierLights.canToggle(mc.getConnection())) return false;
        com.wf.wflib.soldier.SoldierLights.requestToggle();
        return true;
    }

    /** Whether the server has said this entity's light is on. */
    static boolean lit(int entity) {
        return LIT.containsKey(entity);
    }

    public static void set(int entity, boolean on) {
        Minecraft mc = Minecraft.getInstance();
        if (!on || mc.level == null) {
            LIT.remove(entity);
            FRAMES.remove(entity);
        }
        else LIT.put(entity, mc.level.getGameTime() + LAPSE);
    }

    private static void collect(LightSink sink, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            LIT.clear();
            FRAMES.clear();
            return;
        }
        long now = mc.level.getGameTime();
        LampFlare.clear();
        for (Iterator<Map.Entry<Integer, Long>> it = LIT.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Long> e = it.next();
            Entity who = mc.level.getEntity(e.getKey());
            if (e.getValue() < now || who != null && who.isRemoved()) {
                FRAMES.remove(e.getKey());
                it.remove();
                continue;
            }
            if (who == null) continue;
            // Ragdolled (downed, dying): the lamp rides the head bone, so a head turned to the ceiling lights the ceiling.
            double[][] head = WFSystems.enabled("ragdoll") ? RagdollView.head(who, partialTick) : null;
            // Else the lens as the lamp model drew it (head bone and body rig included); else the entity's eye.
            HeadlampLayer.Lens lens = head == null ? HeadlampLayer.lens(who.getId(), now) : null;
            double x, y, z;
            Vector3f look, up;
            if (lens != null) {
                x = lens.x(); y = lens.y(); z = lens.z();
                look = lens.forward(); up = lens.up();
            } else {
                net.minecraft.world.phys.Vec3 eye = who.getEyePosition(partialTick);
                look = head != null ? new Vector3f((float) head[1][0], (float) head[1][1], (float) head[1][2])
                        : who.getViewVector(partialTick).toVector3f();
                up = head != null ? new Vector3f((float) head[2][0], (float) head[2][1], (float) head[2][2])
                        : who.getUpVector(partialTick).toVector3f();
                double ox = head != null ? head[0][0] : eye.x, oy = head != null ? head[0][1] : eye.y,
                        oz = head != null ? head[0][2] : eye.z;
                x = ox + look.x * AHEAD + up.x * RISE;
                y = oy + look.y * AHEAD + up.y * RISE;
                z = oz + look.z * AHEAD + up.z * RISE;
            }
            // The drawn lens (and a ragdoll's head) is already smooth; the entity's own eye and view are not: the AI re-rolls
            // an aim error every few ticks and snaps its head up to 10 degrees a tick, which an off-screen lamp would show.
            Frame frame = FRAMES.computeIfAbsent(e.getKey(), k -> new Frame());
            frame.ease(x, y, z, look, up, lens != null || head != null ? 0.05F : 0.35F);
            Vector3f beam = new Vector3f(frame.fx, frame.fy, frame.fz), top = new Vector3f(frame.ux, frame.uy, frame.uz);
            sink.spot(frame.x, frame.y, frame.z, beam, top, RANGE, INNER, OUTER, COLOR, INTENSITY, null, true, false, SOFTNESS);
            if (who != mc.player || !mc.options.getCameraType().isFirstPerson()) LampFlare.add(frame.x, frame.y, frame.z, beam);
        }
    }
}
