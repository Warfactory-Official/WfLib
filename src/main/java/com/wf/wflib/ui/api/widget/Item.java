package com.wf.wflib.ui.api.widget;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.wf.gemrender.ui.Canvas;
import com.wf.wflib.ui.api.Binder;
import com.wf.wflib.ui.api.UiEval;
import com.wf.wflib.ui.api.Widget;
import com.wf.wflib.ui.api.WidgetDef;
import com.wf.wflib.ui.api.input.UiStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

/**
 * Item icon {@code size} units square (16 px cell scaled), optional count/durability decorations. Vanilla item
 * rendering => hosts with {@code GuiGraphics} only (HUD, {@code UiScreen}); without it draws nothing, logged once.
 */
public record Item(UiStack stack, float size, boolean decorations) implements WidgetDef {
    private static final Logger LOG = LogUtils.getLogger();
    private static volatile boolean warned;

    @Override
    public Widget create(Binder binder) {
        int itemSlot = binder.itemSlot();
        return new Widget() {
            @Override
            public void measure(UiEval ev) {
                width = size;
                height = size;
            }

            @Override
            public void draw(UiEval ev, float x, float y, float w, float h) {
                GuiGraphics graphics = ev.graphics;
                if (graphics == null) {
                    if (!warned) {
                        warned = true;
                        LOG.warn("item widget drawn without GuiGraphics (surface host): skipped");
                    }
                    return;
                }
                ItemStack s = stack.stack(ev.item(itemSlot));
                if (s == null || s.isEmpty()) {
                    return;
                }
                Canvas canvas = ev.canvas;
                canvas.suspend();
                PoseStack pose = graphics.pose();
                pose.pushPose();
                canvas.applyTo(pose);
                pose.translate(x, y, 0);
                pose.scale(size / 16, size / 16, 1);
                graphics.renderItem(s, 0, 0);
                if (decorations) {
                    graphics.renderItemDecorations(net.minecraft.client.Minecraft.getInstance().font, s, 0, 0);
                }
                pose.popPose();
                canvas.resume();
            }
        };
    }
}
