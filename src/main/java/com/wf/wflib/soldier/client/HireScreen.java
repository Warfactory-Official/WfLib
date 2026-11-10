package com.wf.wflib.soldier.client;

import com.wf.wflib.soldier.RecruitPackets.View;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** The barracks menu: what can be hired, what it costs and how long it takes. Click a row to hire. */
final class HireScreen extends Screen {
    private static final int ROW = 22;
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
        if (offers.arriving() > 0) {
            String on = offers.arriving() + " on the way";
            g.drawString(font, on, px + pw - 8 - font.width(on), py + 8, 0xFF58C458, false);
        }
        int top = py + 24, bottom = py + ph - 22;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, offers.list().size() * ROW - (bottom - top))));
        hits.clear();
        g.enableScissor(px + 8, top, px + pw - 8, bottom);
        for (int i = 0; i < offers.list().size(); i++) {
            View v = offers.list().get(i);
            int y = top + i * ROW - (int) scroll;
            boolean over = mx >= px + 8 && mx < px + pw - 8 && my >= Math.max(y, top) && my < Math.min(y + ROW - 2, bottom);
            g.fill(px + 8, y, px + pw - 8, y + ROW - 2, !v.affordable() ? 0x33802020 : over ? 0x66FFFFFF : 0x33FFFFFF);
            g.drawString(font, v.title(), px + 14, y + 3, v.affordable() ? 0xFFFFFFFF : 0xFFA06060, false);
            g.drawString(font, v.role() + (v.delay() > 0 ? "  arrives in " + v.delay() + " s" : ""), px + 14, y + 12, 0xFF8A8A8A, false);
            String cost = v.cost();
            g.drawString(font, cost, px + pw - 14 - font.width(cost), y + 7, v.affordable() ? 0xFF58C458 : 0xFFD83A3A, false);
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
