package com.wf.wflib.ui.api.widget;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wf.gemrender.ui.Canvas;
import com.wf.wflib.ui.api.Binder;
import com.wf.wflib.ui.api.UiEval;
import com.wf.wflib.ui.api.Widget;
import com.wf.wflib.ui.api.WidgetDef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;

import java.util.UUID;
import java.util.function.Function;

/**
 * Face (head + hat layer) of the player or soldier {@code who} names, {@code size} units square; offline uuid =>
 * default skin by uuid. {@code GuiGraphics} hosts only (HUD, {@code UiScreen}); else draws nothing.
 */
public record PlayerFace(Function<Object, UUID> who, float size) implements WidgetDef {
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
                    return;
                }
                UUID id = who.apply(ev.item(itemSlot));
                var connection = Minecraft.getInstance().getConnection();
                var info = connection == null ? null : connection.getPlayerInfo(id);
                PlayerSkin skin = info != null ? info.getSkin() : DefaultPlayerSkin.get(id);
                Canvas canvas = ev.canvas;
                canvas.suspend();
                PoseStack pose = graphics.pose();
                pose.pushPose();
                canvas.applyTo(pose);
                PlayerFaceRenderer.draw(graphics, skin, Math.round(x), Math.round(y), Math.round(size));
                pose.popPose();
                canvas.resume();
            }
        };
    }
}
