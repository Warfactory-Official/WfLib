package com.wf.wflib.ui.api.widget;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wf.gemrender.ui.Canvas;
import com.wf.wflib.ui.api.Binder;
import com.wf.wflib.ui.api.NumProp;
import com.wf.wflib.ui.api.UiEval;
import com.wf.wflib.ui.api.Widget;
import com.wf.wflib.ui.api.WidgetDef;
import com.wf.wflib.ui.api.input.UiEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Full-body entity in a {@code w x h} box, turned {@code yaw} degrees from facing the viewer, sized to fit the box.
 * Vanilla entity rendering => hosts with {@code GuiGraphics} only (HUD, {@code UiScreen}); else draws nothing.
 * Turn by drag: wrap in {@link DragArea}. Code-only.
 */
public record EntityView(UiEntity entity, String w, String h, String yaw) implements WidgetDef {
    private static final float BODY_HEIGHTS = 2.15f;

    @Override
    public Widget create(Binder binder) {
        NumProp pw = binder.num(w);
        NumProp ph = binder.num(h);
        NumProp turn = binder.num(yaw);
        int itemSlot = binder.itemSlot();
        return new Widget() {
            @Override
            public void measure(UiEval ev) {
                width = Math.max(0, pw.getFloat(ev));
                height = Math.max(0, ph.getFloat(ev));
            }

            @Override
            public void draw(UiEval ev, float x, float y, float bw, float bh) {
                GuiGraphics graphics = ev.graphics;
                LivingEntity living = graphics == null ? null : entity.entity(ev.item(itemSlot));
                if (living == null || !(width > 0) || !(height > 0)) {
                    return;
                }
                float facing = 180 + turn.getFloat(ev);
                living.yBodyRot = facing;
                living.setYRot(facing);
                living.yHeadRot = facing;
                living.yHeadRotO = facing;
                living.setXRot(0);
                Canvas canvas = ev.canvas;
                canvas.suspend();
                PoseStack pose = graphics.pose();
                pose.pushPose();
                canvas.applyTo(pose);
                InventoryScreen.renderEntityInInventory(graphics, x + width / 2, y + height * 0.525f,
                        Math.min(width * 0.62f, height / BODY_HEIGHTS), new Vector3f(0, 1f, 0),
                        new Quaternionf().rotateZ((float) Math.PI), new Quaternionf(), living);
                pose.popPose();
                canvas.resume();
            }
        };
    }
}
