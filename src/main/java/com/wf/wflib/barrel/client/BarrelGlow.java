package com.wf.wflib.barrel.client;

import com.wf.gemrender.light.LightSink;
import com.wf.gemrender.light.Lights;
import com.wf.wflib.barrel.Barrels;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Red-hot barrels -> small unshadowed GemRender point lights, submitted per frame by the drawing mod, drained by the
 * next light poll. Gap: the mesh itself does not emit; the light lights it.
 */
public final class BarrelGlow {
    static final float RANGE = 1.6F, INTENSITY = 2.5F;

    private record Glow(double x, double y, double z, float glow) {
    }

    private static final List<Glow> PENDING = new ArrayList<>();

    private BarrelGlow() {
    }

    public static void register() {
        Lights.register(BarrelGlow::collect);
        Barrels.clientClock = () -> {
            var level = Minecraft.getInstance().level;
            return level == null ? -1 : level.getGameTime();
        };
    }

    /** Render thread, this frame. {@code glow} = {@link com.wf.wflib.barrel.BarrelState#glow}. */
    public static void submit(double x, double y, double z, float glow) {
        if (glow > 0 && Lights.shading() && PENDING.size() < Lights.MAX) {
            PENDING.add(new Glow(x, y, z, glow));
        }
    }

    /** Dull red 0xFF2808 at glow 0 -> orange 0xFF7A20 at 1. */
    static int rgb(float glow) {
        int r = 0xFF, g = Math.round(0x28 + (0x7A - 0x28) * glow), b = Math.round(0x08 + (0x20 - 0x08) * glow);
        return r << 16 | g << 8 | b;
    }

    private static void collect(LightSink sink, float partialTick) {
        for (Glow g : PENDING) {
            sink.point(g.x, g.y, g.z, RANGE, rgb(g.glow), INTENSITY * g.glow * g.glow, false);
        }
        PENDING.clear();
    }
}
