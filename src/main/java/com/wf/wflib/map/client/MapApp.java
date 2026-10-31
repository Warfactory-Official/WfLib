package com.wf.wflib.map.client;

import com.wf.gemrender.ui.Canvas;
import com.wf.wflib.map.api.client.WFMaps;
import com.wf.wflib.ui.api.EmbeddedScreen;
import com.wf.wflib.ui.api.Binder;
import com.wf.wflib.ui.api.UiEval;
import com.wf.wflib.ui.api.Widget;
import com.wf.wflib.ui.api.WidgetDef;
import com.wf.wflib.ui.api.input.Interactive;
import com.wf.wflib.ui.api.input.UiInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.wf.wflib.pda.api.client.PdaApp;
import com.wf.wflib.pda.api.client.PdaApps;
import com.wf.wflib.pda.api.client.PdaClient;
import com.wf.wflib.pda.api.client.PdaShell;
import com.wf.wflib.pda.api.client.PdaRenderer;
import com.wf.wflib.core.api.WFLib;

/**
 * The installed map mod's fullscreen map inside the PDA ({@link EmbeddedScreen}): overlays + tools via {@link WFMaps}
 * as on the real screen; cursor = its mouse, right button = its menu. Hidden without a map mod.
 */
public final class MapApp implements PdaApp {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "map");

    private EmbeddedScreen embedded;

    public static void register() {
        PdaApps.register(new MapApp(), 990);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.translatable("wflib.pda.app.map");
    }

    @Override
    public boolean visible(LocalPlayer player) {
        return WFMaps.hasFullscreen();
    }

    @Override
    public boolean ownsSecondary() {
        return true;
    }

    @Override
    public void opened() {
        if (embedded == null) {
            embedded = new EmbeddedScreen();
        }
        size();
        embedded.open(WFMaps::openFullscreen);
    }

    @Override
    public void closed() {
        embedded.close();
    }

    /** App area at the PDA screen's px density. */
    private void size() {
        int k = PdaRenderer.pixelsPerUnit();
        embedded.size(Math.round(PdaShell.APP_WIDTH * k), Math.round(PdaShell.APP_HEIGHT * k));
    }

    @Override
    public WidgetDef build() {
        return new View();
    }

    /** Embedded map over the app area; pointer forwarded in its GUI px. */
    private final class View implements WidgetDef {
        @Override
        public Widget create(Binder binder) {
            return new Node();
        }
    }

    private final class Node extends Widget implements Interactive {
        private double lastX;
        private double lastY;

        @Override
        public void measure(UiEval ev) {
            width = PdaShell.APP_WIDTH;
            height = PdaShell.APP_HEIGHT;
        }

        @Override
        public void draw(UiEval ev, float x, float y, float w, float h) {
            if (embedded == null || !embedded.open()) {
                // Map closed itself (its close button).
                PdaClient.home();
                return;
            }
            size();
            Canvas canvas = ev.canvas;
            canvas.sprite(embedded.request(), x, y, x + width, y + height, 0, 1, 1, 0, 0xFFFFFFFF);
            UiInput input = ev.input;
            if (input != null) {
                input.hit(x, y, x + width, y + height, this, null);
                // Absolute shell, root at 0: host units = local units.
                float px = input.pointerX() - x;
                float py = input.pointerY() - y;
                if (px >= 0 && py >= 0 && px < width && py < height) {
                    embedded.pointer(guiX(px), guiY(py));
                }
            }
        }

        private double guiX(float local) {
            return local / width * embedded.guiWidth();
        }

        private double guiY(float local) {
            return local / height * embedded.guiHeight();
        }

        @Override
        public void press(Object item, float x, float y) {
            lastX = guiX(x);
            lastY = guiY(y);
            embedded.pointer(lastX, lastY);
            embedded.press(0);
        }

        @Override
        public void drag(Object item, float x, float y) {
            if (Float.isNaN(x) || Float.isNaN(y)) {
                return;
            }
            double gx = guiX(x);
            double gy = guiY(y);
            if (gx != lastX || gy != lastY) {
                embedded.pointer(gx, gy);
                embedded.drag(0, gx - lastX, gy - lastY);
                lastX = gx;
                lastY = gy;
            }
        }

        @Override
        public void release(Object item, float x, float y, boolean inside) {
            embedded.release(0);
        }

        @Override
        public void secondary(Object item, float x, float y) {
            embedded.pointer(guiX(x), guiY(y));
            embedded.press(1);
            embedded.release(1);
        }

        @Override
        public boolean wheel(Object item, float amount) {
            embedded.scroll(amount);
            return true;
        }
    }
}
