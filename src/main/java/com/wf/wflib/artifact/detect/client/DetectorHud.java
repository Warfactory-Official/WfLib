package com.wf.wflib.artifact.detect.client;

import com.wf.wflib.artifact.detect.DetectorTier;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The detector's face, bottom left above the hotbar while one is out: the Echo's LED blinking with its beeps, the
 * Bear's ring of LEDs lit toward the nearest artefact, the Veles' screen of blips. shortcut: a HUD stand-in for the
 * device's own face; moves onto the model when the MineAssault viewmodel draws the detector in hand.
 */
final class DetectorHud implements LayeredDraw.Layer {
    private static final int SIZE = 56;

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        DetectorTier tier = DetectorClient.held;
        Minecraft mc = Minecraft.getInstance();
        if (tier == null || mc.player == null || mc.options.hideGui) {
            return;
        }
        // Right of the hotbar: clear of chat, the offhand slot and the hand.
        int x0 = g.guiWidth() / 2 + 91 + 10;
        int y0 = g.guiHeight() - SIZE - 4;
        int cx = x0 + SIZE / 2;
        int cy = y0 + SIZE / 2;
        g.fill(x0 - 2, y0 - 2, x0 + SIZE + 2, y0 + SIZE + 2, 0xC0101010);
        g.fill(x0, y0, x0 + SIZE, y0 + SIZE, 0xC0202422);
        float flash = DetectorClient.flash;
        switch (tier.display) {
            case LED -> {
                int on = 0xFF000000 | ((int) (80 + 175 * flash) << 16) | (int) (20 + 30 * flash) << 8;
                g.fill(cx - 5, cy - 5, cx + 5, cy + 5, on);
            }
            case DIRECTION -> {
                // Eight LEDs round the face; the one toward the nearest artefact lit, brighter on the beep.
                int lit = -1;
                if (!DetectorClient.heard.isEmpty()) {
                    Vec3 to = DetectorClient.heard.get(0).subtract(mc.player.position());
                    double bearing = Math.toDegrees(Math.atan2(-to.x, to.z)) - mc.player.getYRot();
                    lit = Math.floorMod(Math.round((float) (bearing / 45.0D)), 8);
                }
                for (int i = 0; i < 8; i++) {
                    double a = Math.toRadians(i * 45.0D);
                    int lx = cx + (int) Math.round(Math.sin(a) * -18.0D);
                    int ly = cy + (int) Math.round(-Math.cos(a) * 18.0D);
                    int c = i == lit ? 0xFF000000 | (int) (140 + 115 * flash) << 8 | 0x20 : 0xFF203020;
                    g.fill(lx - 3, ly - 3, lx + 3, ly + 3, c);
                }
                g.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0xFF506050);
            }
            case SCREEN -> {
                // Up the screen is ahead; every artefact heard a blip, scaled to the detector's reach.
                g.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFB0FFB0);
                double yaw = Math.toRadians(mc.player.getYRot());
                for (Vec3 p : DetectorClient.heard) {
                    Vec3 to = p.subtract(mc.player.position());
                    double right = -to.x * Math.cos(yaw) - to.z * Math.sin(yaw);
                    double ahead = -to.x * Math.sin(yaw) + to.z * Math.cos(yaw);
                    // Rotate world into view: x right, y ahead (screen up).
                    double k = (SIZE / 2.0D - 3.0D) / tier.radius;
                    int bx = cx + (int) Math.round(Mth.clamp(-right * k, -SIZE / 2.0D + 2, SIZE / 2.0D - 2));
                    int by = cy - (int) Math.round(Mth.clamp(ahead * k, -SIZE / 2.0D + 2, SIZE / 2.0D - 2));
                    int c = 0xFF000000 | (int) (120 + 135 * flash) << 8 | 0x30;
                    g.fill(bx - 2, by - 2, bx + 2, by + 2, c);
                }
            }
        }
        g.drawString(mc.font, tier.name(), x0 + 2, y0 + SIZE - 9, 0xFF90A090, false);
    }
}
