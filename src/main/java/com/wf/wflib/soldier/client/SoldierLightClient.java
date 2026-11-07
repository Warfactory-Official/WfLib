package com.wf.wflib.soldier.client;

import com.wf.gemrender.light.LightSink;
import com.wf.gemrender.light.Lights;
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
    private static final float INTENSITY = 500;
    static final int COLOR = 0xFFF4E6;
    /** A little ahead of the eye and above it, on the forehead where a headlamp sits, blocks. */
    private static final float AHEAD = 0.3F, RISE = 0.12F;
    private static final Map<Integer, Long> LIT = new HashMap<>();

    private SoldierLightClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(FMLClientSetupEvent.class, e -> e.enqueueWork(() -> {
            Lights.register(SoldierLightClient::collect);
            NeoForge.EVENT_BUS.addListener(LampFlare::onRenderLevel);
        }));
    }

    public static void set(int entity, boolean on) {
        Minecraft mc = Minecraft.getInstance();
        if (!on || mc.level == null) LIT.remove(entity);
        else LIT.put(entity, mc.level.getGameTime() + LAPSE);
    }

    private static void collect(LightSink sink, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            LIT.clear();
            return;
        }
        long now = mc.level.getGameTime();
        LampFlare.clear();
        for (Iterator<Map.Entry<Integer, Long>> it = LIT.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Long> e = it.next();
            Entity who = mc.level.getEntity(e.getKey());
            if (e.getValue() < now || who != null && who.isRemoved()) {
                it.remove();
                continue;
            }
            if (who == null) continue;
            var eye = who.getEyePosition(partialTick);
            Vector3f look = who.getViewVector(partialTick).toVector3f();
            Vector3f up = who.getUpVector(partialTick).toVector3f();
            double x = eye.x + look.x * AHEAD + up.x * RISE, y = eye.y + look.y * AHEAD + up.y * RISE,
                    z = eye.z + look.z * AHEAD + up.z * RISE;
            sink.spot(x, y, z, look, up, RANGE, INNER, OUTER, COLOR, INTENSITY, null, true);
            LampFlare.add(x, y, z, look);
        }
    }
}
