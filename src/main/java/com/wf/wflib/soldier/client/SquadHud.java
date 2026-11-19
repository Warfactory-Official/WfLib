package com.wf.wflib.soldier.client;

import com.wf.wflib.soldier.Companion;
import com.wf.wflib.soldier.SquadPackets.Member;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The squad strip: one row per fire team, a card per soldier (face, health, ammunition, what it is doing). The selected
 * group has a red bar under its label and a red frame; a soldier in a fight has an orange frame, a downed one is struck out.
 */
final class SquadHud implements LayeredDraw.Layer {
    private static final int CARD = 26, FACE = 20, GAP = 3, ROW = 34, LEFT = 6, TOP = 6, LABEL = 14;
    private static final int RED = 0xFFD83A3A, ORANGE = 0xFFE8963C, BLUE = 0xFF4A90E0, PANEL = 0x99000000, DIM = 0xFF8A8A8A;

    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        var mc = Minecraft.getInstance();
        List<Member> all = SquadClient.members;
        if (all.isEmpty() || mc.options.hideGui || mc.player == null) return;
        int y = TOP;
        for (int team = 0; team < Companion.TEAMS.length; team++) {
            int t = team;
            List<Member> whole = all.stream().filter(m -> m.team() == t).toList();
            if (whole.isEmpty()) continue;
            // a big squad does not run off the screen: the cards that fit, then how many more there are (the screen lists them all)
            int fit = Math.max(3, (int) (g.guiWidth() * 0.6 - LEFT - LABEL) / (CARD + GAP));
            List<Member> row = whole.size() <= fit ? whole : whole.subList(0, fit - 1);
            int more = whole.size() - row.size();
            int cells = row.size() + (more > 0 ? 1 : 0);
            boolean selected = SquadClient.group == team;
            boolean everyone = SquadClient.group < 0;
            g.fill(LEFT, y, LEFT + LABEL + cells * (CARD + GAP) + GAP, y + ROW, PANEL);
            g.drawString(mc.font, Companion.TEAMS[team].substring(0, 1), LEFT + 4, y + 4, selected || everyone ? 0xFFFFFFFF : DIM, true);
            if (selected) g.fill(LEFT + 2, y + 15, LEFT + LABEL - 2, y + 17, RED);       // the red bar of the selected group
            int x = LEFT + LABEL;
            for (Member m : row) {
                card(g, mc, m, x + GAP, y + 3, selected);
                x += CARD + GAP;
            }
            if (more > 0) g.drawCenteredString(mc.font, "+" + more, x + GAP + CARD / 2, y + 10, 0xFFFFFFFF);
            if (selected) frame(g, LEFT, y, LEFT + LABEL + cells * (CARD + GAP) + GAP, y + ROW, RED);
            y += ROW + 2;
        }
        long age = mc.level == null ? Long.MAX_VALUE : mc.level.getGameTime() - SquadClient.toastAt;
        if (age >= 0 && age < 70) {
            int alpha = age < 50 ? 255 : (int) (255 * (70 - age) / 20f);
            Component text = Component.literal("> " + SquadClient.toast);
            int w = mc.font.width(text);
            int tx = (g.guiWidth() - w) / 2, ty = g.guiHeight() - 68;
            g.fill(tx - 4, ty - 3, tx + w + 4, ty + 11, (alpha * 0x99 / 255) << 24);
            g.drawString(mc.font, text, tx, ty, (alpha << 24) | 0xFFFFFF, true);
        }
    }

    private static void card(GuiGraphics g, Minecraft mc, Member m, int x, int y, boolean selected) {
        var info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(m.uuid());
        var skin = info != null ? info.getSkin() : DefaultPlayerSkin.get(m.uuid());
        PlayerFaceRenderer.draw(g, skin, x + 1, y + 1, FACE);
        boolean downed = (m.flags() & Member.DOWNED) != 0;
        if (downed) {
            g.fill(x + 1, y + 1, x + 1 + FACE, y + 1 + FACE, 0xAA600000);
            g.drawCenteredString(mc.font, "X", x + 1 + FACE / 2, y + 7, 0xFFFFFFFF);
        }
        if ((m.flags() & Member.FIGHTING) != 0) frame(g, x, y, x + FACE + 2, y + FACE + 2, ORANGE);
        else if ((m.flags() & Member.RESUPPLYING) != 0) frame(g, x, y, x + FACE + 2, y + FACE + 2, BLUE);
        // what it is doing, in the corner: F follow, H hold, M move, A attack, S suppress, P patrol, G guard, R resupply
        String glyph = m.order().substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        g.fill(x + FACE - 6, y + 1, x + FACE + 2, y + 10, 0xCC000000);
        g.drawString(mc.font, glyph, x + FACE - 4, y + 1, selected ? 0xFFFF6060 : 0xFFFFFFFF, false);
        int hp = FACE * m.health() / 100;
        g.fill(x + 1, y + FACE + 3, x + 1 + FACE, y + FACE + 5, 0xFF202020);
        g.fill(x + 1, y + FACE + 3, x + 1 + hp, y + FACE + 5, m.health() > 50 ? 0xFF58C458 : m.health() > 25 ? 0xFFE0B040 : RED);
        if (m.ammo() >= 0) {
            g.fill(x + 1, y + FACE + 6, x + 1 + FACE, y + FACE + 7, 0xFF202020);
            g.fill(x + 1, y + FACE + 6, x + 1 + FACE * m.ammo() / 100, y + FACE + 7, m.mags() == 0 && m.ammo() < 30 ? RED : 0xFFB0B0B0);
            g.drawString(mc.font, Integer.toString(m.mags()), x + 2 + FACE - mc.font.width(Integer.toString(m.mags())) - 1, y + FACE - 8, 0xFFFFFFFF, true);
        }
    }

    static void frame(GuiGraphics g, int x0, int y0, int x1, int y1, int colour) {
        g.fill(x0, y0, x1, y0 + 1, colour);
        g.fill(x0, y1 - 1, x1, y1, colour);
        g.fill(x0, y0, x0 + 1, y1, colour);
        g.fill(x1 - 1, y0, x1, y1, colour);
    }
}
