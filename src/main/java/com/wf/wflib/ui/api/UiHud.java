package com.wf.wflib.ui.api;

import com.wf.gemrender.ui.Canvas;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.List;
import com.wf.wflib.core.api.OrderedRegistry;
import com.wf.wflib.ui.UiSignals;
import com.wf.wflib.ui.UiStats;
import com.wf.wflib.core.api.WFLib;

/**
 * Hosts every HUD {@link UiInstance} in two GUI layers: {@link Layer#BACK} under all vanilla layers
 * (full-screen sight overlays), {@link Layer#MAIN} under chat. Skipped while the GUI is hidden.
 * Relative layouts zoomed by GUI scale / auto GUI scale.
 */
@EventBusSubscriber(modid = WFLib.MODID, value = Dist.CLIENT)
public final class UiHud {
    public static final ResourceLocation BACK_LAYER = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "hud_back");
    public static final ResourceLocation MAIN_LAYER = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "hud");

    public enum Layer {
        BACK,
        MAIN
    }

    /** Called every frame; adds the instances to draw now, in order. */
    @FunctionalInterface
    public interface Source {
        void collect(Collector out);
    }

    public interface Collector {
        void add(UiInstance instance);
    }

    private static final OrderedRegistry<Source> BACK = OrderedRegistry.create("ui", "ui.hud.back");
    private static final OrderedRegistry<Source> MAIN = OrderedRegistry.create("ui", "ui.hud.main");
    private static final Drawer DRAWER = new Drawer();
    private static long frame;

    private UiHud() {
    }

    /** Descending priority; startup-only. */
    public static void addSource(Layer layer, ResourceLocation id, int priority, Source source) {
        (layer == Layer.BACK ? BACK : MAIN).register(id, priority, source);
    }

    @SubscribeEvent
    static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerBelowAll(BACK_LAYER, (graphics, delta) -> render(graphics, delta, BACK.values()));
        event.registerBelow(VanillaGuiLayers.CHAT, MAIN_LAYER, (graphics, delta) -> render(graphics, delta, MAIN.values()));
    }

    /** Where {@link #layer} sits: above / below {@code anchor}, or above every layer ({@code anchor} unused). */
    public enum Position {
        ABOVE,
        BELOW,
        ABOVE_ALL
    }

    /**
     * Own GUI layer {@code id} drawing {@code source}'s instances, for z-order against a vanilla or other mod's layer;
     * call from a {@code RegisterGuiLayersEvent} listener.
     */
    public static void layer(RegisterGuiLayersEvent event, ResourceLocation id, Position position,
                             ResourceLocation anchor, Source source) {
        List<Source> one = List.of(source);
        LayeredDraw.Layer layer = (graphics, delta) -> render(graphics, delta, one);
        switch (position) {
            case ABOVE -> event.registerAbove(anchor, id, layer);
            case BELOW -> event.registerBelow(anchor, id, layer);
            case ABOVE_ALL -> event.registerAboveAll(id, layer);
        }
    }

    /** Increments once per rendered frame; per-frame memo key. */
    public static long frame() {
        return frame;
    }

    @SubscribeEvent
    static void onFrame(RenderFrameEvent.Pre event) {
        frame++;
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(UiSignals::register);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta, List<Source> sources) {
        // Overlay up => UiResources not yet published; quick-play joins during the fade-out.
        if (sources.isEmpty() || Minecraft.getInstance().options.hideGui || Minecraft.getInstance().getOverlay() != null) {
            return;
        }
        UiStats.begin();
        DRAWER.graphics = graphics;
        DRAWER.width = graphics.guiWidth();
        DRAWER.height = graphics.guiHeight();
        DRAWER.partialTick = delta.getGameTimeDeltaPartialTick(true);
        Window window = Minecraft.getInstance().getWindow();
        DRAWER.zoom = (float) (window.getGuiScale()
                / window.calculateScale(0, Minecraft.getInstance().isEnforceUnicode()));
        try {
            for (int i = 0; i < sources.size(); i++) {
                sources.get(i).collect(DRAWER);
            }
        } finally {
            UiStats.mark(UiStats.COLLECT);
            if (DRAWER.canvas != null) {
                DRAWER.canvas.end();
                DRAWER.canvas = null;
            }
            DRAWER.graphics = null;
            UiStats.end();
        }
    }

    /** Begins the canvas at the first instance so a frame with nothing to draw touches no GL. */
    private static final class Drawer implements Collector {
        GuiGraphics graphics;
        Canvas canvas;
        float width;
        float height;
        float partialTick;
        float zoom;

        @Override
        public void add(UiInstance instance) {
            UiStats.mark(UiStats.COLLECT);
            if (canvas == null) {
                canvas = Canvas.shared().begin(graphics);
                UiStats.mark(UiStats.BEGIN);
            }
            instance.render(canvas, graphics, 0, 0, width, height, partialTick, zoom);
        }
    }
}
