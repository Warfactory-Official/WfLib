package com.wf.wflib.ui.api;

import com.mojang.blaze3d.platform.Window;
import com.wf.gemrender.ui.Canvas;
import com.wf.wflib.ui.api.input.UiInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * {@link Screen} hosting one interactive {@link UiInstance}: pointer, wheel, tooltips ({@code tooltip} widgets),
 * Esc closes. Absolute layout with a design size + {@code centre} => that box centred; else the whole GUI.
 */
public class UiScreen extends Screen {
    /** {@code tick}: once per client tick while open; {@code onClose}: once when closed or replaced. */
    public record Options(boolean pause, boolean centre, @Nullable Runnable tick, @Nullable Runnable onClose) {
        public static final Options DEFAULT = new Options(false, true, null, null);
    }

    private final UiLayout layout;
    private final UiInstance instance;
    private final UiInput input;
    private final Options options;
    private final float[] box = new float[4];
    private int buttons;

    protected UiScreen(UiLayout layout, UiContext context, Component title, Options options) {
        super(title);
        this.layout = layout;
        this.instance = layout.instantiate().context(context);
        this.input = instance.input();
        this.options = options;
    }

    public static UiScreen open(UiLayout layout, UiContext context, Component title, Options options) {
        UiScreen screen = new UiScreen(layout, context, title, options);
        Minecraft.getInstance().setScreen(screen);
        return screen;
    }

    public UiLayout layout() {
        return layout;
    }

    /** Host box {@code {x, y, w, h}} in GUI px. */
    static void box(float screenW, float screenH, UiLayout layout, boolean centre, float[] out) {
        float dw = layout.designWidth();
        float dh = layout.designHeight();
        if (centre && dw > 0 && dh > 0) {
            out[0] = (float) Math.floor((screenW - dw) / 2);
            out[1] = (float) Math.floor((screenH - dh) / 2);
            out[2] = dw;
            out[3] = dh;
        } else {
            out[0] = 0;
            out[1] = 0;
            out[2] = screenW;
            out[3] = screenH;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        box(width, height, layout, options.centre(), box);
        input.pointer(mouseX, mouseY, buttons);
        Window window = Minecraft.getInstance().getWindow();
        float zoom = (float) (window.getGuiScale() / window.calculateScale(0, Minecraft.getInstance().isEnforceUnicode()));
        Canvas canvas = Canvas.shared().begin(graphics);
        try {
            instance.render(canvas, graphics, box[0], box[1], box[2], box[3], partialTick, zoom);
        } finally {
            canvas.end();
        }
        List<Component> tip = input.tooltip();
        if (tip != null && !tip.isEmpty()) {
            graphics.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        buttons |= bit(button);
        input.button((float) mouseX, (float) mouseY, buttons);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        buttons &= ~bit(button);
        input.button((float) mouseX, (float) mouseY, buttons);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        input.wheel((float) scrollY);
        return true;
    }

    private static int bit(int glfwButton) {
        return glfwButton == 0 ? UiInput.PRIMARY : glfwButton == 1 ? UiInput.SECONDARY : 0;
    }

    @Override
    public void tick() {
        if (options.tick() != null) {
            options.tick().run();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return options.pause();
    }

    @Override
    public void removed() {
        input.reset();
        buttons = 0;
        if (options.onClose() != null) {
            options.onClose().run();
        }
    }
}
