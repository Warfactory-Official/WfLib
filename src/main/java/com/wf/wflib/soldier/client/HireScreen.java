package com.wf.wflib.soldier.client;

import com.wf.wflib.soldier.RecruitPackets.View;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** The barracks menu: what can be hired, what it costs and how long it takes. Click a row to hire. */
final class HireScreen extends Screen {
    private static final int ROW = 34;
    private int px, py, pw, ph;
    private double scroll;
    private final List<int[]> hits = new ArrayList<>();

    HireScreen() {
        super(Component.literal("Barracks"));
    }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 360);
        ph = Math.min(height - 16, 240);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0x88000000);
        g.fill(px, py, px + pw, py + ph, 0xEE101010);
        SquadHud.frame(g, px, py, px + pw, py + ph, 0xFF606060);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        var offers = RecruitClient.barracks();
        g.drawString(font, "Barracks", px + 8, py + 8, 0xFFFFFFFF, false);
        if (!offers.header().isEmpty()) g.drawString(font, font.plainSubstrByWidth(offers.header().toLowerCase(), pw - 16), px + 8, py + 20, 0xFF8A8A8A, false);
        int top = py + 34, bottom = py + ph - 22;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, offers.list().size() * ROW - (bottom - top))));
        hits.clear();
        g.enableScissor(px + 8, top, px + pw - 8, bottom);
        for (int i = 0; i < offers.list().size(); i++) {
            View v = offers.list().get(i);
            int y = top + i * ROW - (int) scroll;
            boolean over = mx >= px + 8 && mx < px + pw - 8 && my >= Math.max(y, top) && my < Math.min(y + ROW - 2, bottom);
            g.fill(px + 8, y, px + pw - 8, y + ROW - 2, !v.affordable() ? 0x33802020 : over ? 0x66FFFFFF : 0x33FFFFFF);
            g.drawString(font, v.title() + "  " + v.role(), px + 14, y + 3, v.affordable() ? 0xFFFFFFFF : 0xFFA06060, false);
            g.drawString(font, v.kit(), px + 14, y + 13, 0xFF9A9A9A, false);
            g.drawString(font, v.gear() + (v.delay() > 0 ? "  -  arrives in " + v.delay() + " s" : ""), px + 14, y + 23, 0xFF7A7A7A, false);
            String cost = v.cost().isEmpty() ? "free" : v.cost();
            g.drawString(font, cost, px + pw - 14 - font.width(cost), y + 3, v.affordable() ? 0xFF58C458 : 0xFFD83A3A, false);
            if (!v.upkeep().isEmpty()) g.drawString(font, "upkeep " + v.upkeep(), px + pw - 14 - font.width("upkeep " + v.upkeep()), y + 13, 0xFFE0B040, false);
            hits.add(new int[]{px + 8, Math.max(y, top), px + pw - 8, Math.min(y + ROW - 2, bottom), i});
        }
        g.disableScissor();
        if (offers.list().isEmpty()) g.drawCenteredString(font, offers.on() ? "Nobody on offer" : "Recruiting here is switched off", px + pw / 2, py + ph / 2, 0xFF8A8A8A);
        g.drawString(font, offers.note().isEmpty() ? "Click a soldier to hire" : offers.note(), px + 8, py + ph - 14, offers.note().isEmpty() ? 0xFF8A8A8A : 0xFFFFFFFF, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int[] h : hits) {
            if (mx >= h[0] && mx < h[2] && my >= h[1] && my < h[3]) {
                RecruitClient.hire(RecruitClient.barracks().where(), RecruitClient.barracks().list().get(h[4]).id());
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll -= sy * ROW * 2;
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
