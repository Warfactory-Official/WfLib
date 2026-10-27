package com.wf.wflib.anomaly.client;

import com.wf.wflib.WFLib;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * A Flash taking the local player: the world whites out, darkens into a blackout and comes back hours later (the
 * server says how many, on the action bar). Over everything but chat.
 */
@EventBusSubscriber(modid = WFLib.MODID, value = Dist.CLIENT)
public final class TimeSlipOverlay implements LayeredDraw.Layer {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "time_slip");
    /** Seconds: white in, white to black, black held, black out. */
    private static final float WHITE = 0.25F;
    private static final float DARKEN = 1.0F;
    private static final float HOLD = 1.5F;
    private static final float RETURN = 2.0F;

    private static long start = -1L;

    private TimeSlipOverlay() {
    }

    static void start() {
        start = System.nanoTime();
    }

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerBelow(VanillaGuiLayers.CHAT, LAYER, new TimeSlipOverlay());
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        if (start < 0L) {
            return;
        }
        float t = (System.nanoTime() - start) / 1.0E9F;
        float white;
        float black;
        if (t < WHITE) {
            white = t / WHITE;
            black = 0.0F;
        } else if (t < WHITE + DARKEN) {
            float k = (t - WHITE) / DARKEN;
            white = 1.0F - k;
            black = k;
        } else if (t < WHITE + DARKEN + HOLD) {
            white = 0.0F;
            black = 1.0F;
        } else if (t < WHITE + DARKEN + HOLD + RETURN) {
            white = 0.0F;
            black = 1.0F - (t - WHITE - DARKEN - HOLD) / RETURN;
        } else {
            start = -1L;
            return;
        }
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        // Black over the whole screen, white (warm, as the Flash's light) over that.
        graphics.fill(0, 0, w, h, (int) (Mth.clamp(black, 0.0F, 1.0F) * 255.0F) << 24);
        graphics.fill(0, 0, w, h, (int) (Mth.clamp(white, 0.0F, 1.0F) * 255.0F) << 24 | 0xFFF4E0);
    }
}
