package com.wf.wflib.ui.api.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.wf.gemrender.ui.Canvas;
import com.wf.wflib.ui.SkinShader;
import com.wf.wflib.ui.api.Binder;
import com.wf.wflib.ui.api.NumProp;
import com.wf.wflib.ui.api.PlayerSkins;
import com.wf.wflib.ui.api.UiEval;
import com.wf.wflib.ui.api.Widget;
import com.wf.wflib.ui.api.WidgetDef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

/**
 * Front face of one body part of a player's skin ({@code entity} < 0 = local player), base + overlay layer, stained
 * by {@code health} [0, 1] (desaturate + blood tint) via {@code wflib:skin_damage}. No {@code GuiGraphics} host or no
 * shader => plain two-layer sprite, unstained.
 */
public record SkinPart(Part part, String entity, String health, float w, float h) implements WidgetDef {
    /** 64x64 skin regions, px {@code {u, v, w, h}}: base, overlay; slim arms 3 px wide. */
    public enum Part {
        HEAD(8, 8, 40, 8, 8, 8, false),
        TORSO(20, 20, 20, 36, 8, 12, false),
        RIGHT_ARM(44, 20, 44, 36, 4, 12, true),
        LEFT_ARM(36, 52, 52, 52, 4, 12, true),
        RIGHT_LEG(4, 20, 4, 36, 4, 12, false),
        LEFT_LEG(20, 52, 4, 52, 4, 12, false);

        private final int[] wide;
        private final int[] slim;

        Part(int bu, int bv, int ou, int ov, int width, int height, boolean arm) {
            wide = new int[]{bu, bv, ou, ov, width, height};
            slim = new int[]{bu, bv, ou, ov, arm ? width - 1 : width, height};
        }

        /** {@code {u, v, w, h}} px. */
        public int[] region(boolean slimArms, boolean overlay) {
            int[] r = slimArms ? slim : wide;
            return new int[]{overlay ? r[2] : r[0], overlay ? r[3] : r[1], r[4], r[5]};
        }

        int[] packed(boolean slimArms) {
            return slimArms ? slim : wide;
        }
    }

    private static final float SKIN = 64;

    @Override
    public Widget create(Binder binder) {
        NumProp who = binder.num(entity);
        NumProp hp = binder.num(health);
        return new Widget() {
            @Override
            public void measure(UiEval ev) {
                width = w;
                height = h;
            }

            @Override
            public void draw(UiEval ev, float x, float y, float bw, float bh) {
                PlayerSkins.Skin skin = PlayerSkins.forEntity((int) who.get(ev));
                int[] r = part.packed(skin.slim());
                Canvas canvas = ev.canvas;
                ShaderInstance shader = SkinShader.get();
                GuiGraphics graphics = ev.graphics;
                if (shader == null || graphics == null) {
                    canvas.sprite(skin.texture(), x, y, x + w, y + h, r[0] / SKIN, r[1] / SKIN, (r[0] + r[4]) / SKIN,
                            (r[1] + r[5]) / SKIN, 0xFFFFFFFF);
                    canvas.sprite(skin.texture(), x, y, x + w, y + h, r[2] / SKIN, r[3] / SKIN, (r[2] + r[4]) / SKIN,
                            (r[3] + r[5]) / SKIN, 0xFFFFFFFF);
                    return;
                }
                float health = (float) Math.clamp(hp.get(ev), 0, 1);
                canvas.suspend();
                PoseStack pose = graphics.pose();
                pose.pushPose();
                canvas.applyTo(pose);
                Matrix4f m = pose.last().pose();
                shader.setSampler("Skin", Minecraft.getInstance().getTextureManager().getTexture(skin.texture()));
                shader.safeGetUniform("uBase").set(r[0] / SKIN, r[1] / SKIN, r[4] / SKIN, r[5] / SKIN);
                shader.safeGetUniform("uOverlay").set(r[2] / SKIN, r[3] / SKIN, r[4] / SKIN, r[5] / SKIN);
                shader.safeGetUniform("uHealth").set(health);
                RenderSystem.setShader(() -> shader);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
                // v = 1 at the top: the fragment shader flips (ported as is).
                b.addVertex(m, x, y, 0).setUv(0, 1);
                b.addVertex(m, x, y + h, 0).setUv(0, 0);
                b.addVertex(m, x + w, y + h, 0).setUv(1, 0);
                b.addVertex(m, x + w, y, 0).setUv(1, 1);
                BufferUploader.drawWithShader(b.buildOrThrow());
                RenderSystem.disableBlend();
                pose.popPose();
                canvas.resume();
            }
        };
    }
}
