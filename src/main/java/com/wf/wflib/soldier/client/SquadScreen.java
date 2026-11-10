package com.wf.wflib.soldier.client;

import com.wf.wflib.soldier.Companion;
import com.wf.wflib.soldier.SquadPackets.Member;
import com.wf.wflib.soldier.SquadPackets.TerritoryInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The squad's management screen ({@code Home}). Four fire-team columns of soldiers on the left, what they can be told to do on
 * the right. Click selects (a team's name selects the team), and drag does everything else: drop soldiers on another column to
 * change their fire team or put them in order, on an order or a territory to give it, on the discharge strip to let them go.
 * The Territories tab is the map-maker's: each territory's garrison is edited in place on its card.
 * Every drop is a request; the server checks the soldiers are the player's and the edit is allowed.
 */
final class SquadScreen extends Screen {
    private static final int ROW = 24, HEAD = 14, GAP = 6, DRAG_START = 4;
    private static final int RED = 0xFFD83A3A, ORANGE = 0xFFE8963C, BLUE = 0xFF4A90E0, GREEN = 0xFF58C458, DIM = 0xFF8A8A8A, WHITE = 0xFFFFFFFF;

    private enum Tab { SQUAD, TERRITORIES }

    private record Zone(int x0, int y0, int x1, int y1) {
        boolean has(double x, double y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }
    }

    private record Chip(Zone zone, Member member) {}

    /** A drop target on the right: an order, or a territory under the chosen mission mode. */
    private record Tile(Zone zone, String kind, String region, String label) {}

    private record Act(Zone zone, Runnable run) {}

    private Tab tab = Tab.SQUAD;
    private final Set<UUID> selected = new LinkedHashSet<>();
    private boolean patrol = true;
    private double scroll, tScroll;
    private int px, py, pw, ph, cx, cy, cw, ch;
    private final List<Act> acts = new ArrayList<>();
    private final List<Tile> tiles = new ArrayList<>();
    private Zone discharge;
    private String hint = "";
    private long hintAt;

    /** The press that may become a drag: a soldier, or a whole team by its name. */
    private Member pressed;
    private int pressedTeam = -1;
    private double pressX, pressY;
    private boolean dragging;
    private List<UUID> carried = List.of();

    SquadScreen() {
        super(Component.translatable("wflib.squad.manage"));
    }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 560);
        ph = Math.min(height - 16, 330);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        cx = px + 8;
        cy = py + 28;
        cw = pw - 16;
        ch = ph - 28 - 22;
        SquadClient.manage("list", List.of(), "", "", "", 0, 0);
    }

    // ---- helpers

    private List<Member> roster() {
        return SquadClient.members;
    }

    private List<Member> team(int t) {
        return roster().stream().filter(m -> m.team() == t).toList();
    }

    private int leftW() {
        return (int) (cw * 0.64);
    }

    private int colW() {
        return (leftW() - 3 * GAP) / 4;
    }

    private int colX(int t) {
        return cx + t * (colW() + GAP);
    }

    private int bodyTop() {
        return cy + HEAD + 2;
    }

    private Zone column(int t) {
        return new Zone(colX(t), cy, colX(t) + colW(), cy + ch);
    }

    private void say(String text) {
        hint = text;
        hintAt = minecraft == null || minecraft.level == null ? 0 : minecraft.level.getGameTime();
    }

    private List<Chip> chips() {
        List<Chip> out = new ArrayList<>();
        for (int t = 0; t < Companion.TEAMS.length; t++) {
            int i = 0;
            for (Member m : team(t)) {
                int y = bodyTop() + i++ * ROW - (int) scroll;
                out.add(new Chip(new Zone(colX(t), y, colX(t) + colW(), y + ROW - 2), m));
            }
        }
        return out;
    }

    private boolean visible(Zone z) {
        return z.y1() > bodyTop() && z.y0() < cy + ch;
    }

    private List<UUID> ordered(Set<UUID> ids) {
        return roster().stream().map(Member::uuid).filter(ids::contains).toList();
    }

    // ---- what the player can ask for

    private void order(List<UUID> who, String kind, String region) {
        List<UUID> ids = who.isEmpty() ? roster().stream().map(Member::uuid).toList() : who;
        if (ids.isEmpty()) return;
        SquadClient.manage("order", ids, kind, region, "", 0, 0);
    }

    private void drop(Tile t) {
        order(carried, t.kind(), t.region());
        say(carried.size() + (carried.size() == 1 ? " soldier: " : " soldiers: ") + t.label());
    }

    private void edit(TerritoryInfo t, int size, String mode, int delay, String units) {
        SquadClient.manage("garrison", List.of(), size <= 0 ? "" : mode, t.id(), units, size, delay);
    }

    private void step(TerritoryInfo t, int by) {
        int n = hasShiftDown() ? 10 : 1;
        edit(t, Math.max(0, Math.max(t.garrison(), 0) + by * n), t.mode().isEmpty() ? "patrol" : t.mode(), t.garrison() < 0 ? 60 : t.respawn(), t.units());
    }

    private void delay(TerritoryInfo t, int by) {
        if (t.garrison() >= 0) edit(t, t.garrison(), t.mode(), Math.max(0, t.respawn() + by * (hasShiftDown() ? 6 : 1)), t.units());
    }

    private void mode(TerritoryInfo t) {
        if (t.garrison() >= 0) edit(t, t.garrison(), t.mode().equals("patrol") ? "guard" : "patrol", t.respawn(), t.units());
    }

    /** Every saved variant mixed, then each one alone. */
    private void variant(TerritoryInfo t) {
        List<String> variants = SquadClient.world.variants();
        if (t.garrison() < 0 || variants.isEmpty()) return;
        int at = t.units().isEmpty() ? -1 : variants.indexOf(t.units());
        edit(t, t.garrison(), t.mode(), t.respawn(), at + 1 >= variants.size() ? "" : variants.get(at + 1));
    }

    // ---- drawing

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0x88000000);
        g.fill(px, py, px + pw, py + ph, 0xEE101010);
        SquadHud.frame(g, px, py, px + pw, py + ph, 0xFF606060);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        acts.clear();
        tiles.clear();
        discharge = null;
        tabs(g, mx, my);
        if (tab == Tab.SQUAD) squad(g, mx, my);
        else territories(g, mx, my);
        long age = minecraft.level == null ? 0 : minecraft.level.getGameTime() - hintAt;
        String line = !hint.isEmpty() && age < 120 ? hint : tab == Tab.SQUAD
                ? "Click to select, drag to give an order or change team" : SquadClient.world.canEdit() ? "Edit a garrison right on its card (shift: x10)" : "Only operators edit territories";
        g.drawString(font, line, cx, py + ph - 14, age < 120 && !hint.isEmpty() ? WHITE : DIM, false);
        if (dragging) ghost(g, mx, my);
    }

    private void tabs(GuiGraphics g, int mx, int my) {
        int x = cx;
        for (Tab t : Tab.values()) {
            String label = t == Tab.SQUAD ? "Squad" : "Territories";
            int w = font.width(label) + 14;
            Zone z = new Zone(x, py + 6, x + w, py + 24);
            boolean on = t == tab, over = z.has(mx, my);
            g.drawString(font, label, x + 7, py + 11, on || over ? WHITE : DIM, false);
            if (on) g.fill(x, py + 22, x + w, py + 24, RED);
            acts.add(new Act(z, () -> {
                tab = t;
                scroll = 0;
                tScroll = 0;
            }));
            x += w + 4;
        }
        String count = roster().size() + (roster().size() == 1 ? " soldier" : " soldiers");
        g.drawString(font, count, px + pw - 8 - font.width(count), py + 11, DIM, false);
    }

    private void squad(GuiGraphics g, int mx, int my) {
        int rows = 0;
        for (int t = 0; t < Companion.TEAMS.length; t++) rows = Math.max(rows, team(t).size());
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows * ROW - (ch - HEAD - 2))));
        Zone over = dragging ? columnAt(mx, my) : null;
        for (int t = 0; t < Companion.TEAMS.length; t++) {
            Zone col = column(t);
            List<Member> in = team(t);
            boolean mine = over != null && over.equals(col);
            g.fill(col.x0(), cy, col.x1(), cy + ch, mine ? 0x44D83A3A : 0x55000000);
            boolean all = !in.isEmpty() && in.stream().allMatch(m -> selected.contains(m.uuid()));
            Zone head = new Zone(col.x0(), cy, col.x1(), cy + HEAD);
            g.fill(head.x0(), head.y0(), head.x1(), head.y1(), all ? 0xAAD83A3A : head.has(mx, my) ? 0x66FFFFFF : 0x44FFFFFF);
            g.drawString(font, Companion.TEAMS[t] + " " + in.size(), head.x0() + 4, head.y0() + 3, WHITE, false);
        }
        g.enableScissor(cx, bodyTop(), cx + leftW(), cy + ch);
        int line = -1;
        Zone target = dragging ? columnAt(mx, my) : null;
        for (Chip c : chips()) {
            if (visible(c.zone())) chip(g, c, mx, my);
        }
        if (target != null) {                                           // where a drop would land
            int team = teamOf(target), idx = dropIndex(team, my);
            line = bodyTop() + idx * ROW - (int) scroll - 1;
            g.fill(target.x0() + 2, line, target.x1() - 2, line + 2, RED);
        }
        g.disableScissor();
        if (roster().isEmpty()) g.drawCenteredString(font, "No soldiers yet", cx + leftW() / 2, cy + ch / 2 - 4, DIM);
        right(g, mx, my);
    }

    private void chip(GuiGraphics g, Chip c, int mx, int my) {
        Zone z = c.zone();
        Member m = c.member();
        boolean sel = selected.contains(m.uuid()), lifted = dragging && carried.contains(m.uuid());
        g.fill(z.x0(), z.y0(), z.x1(), z.y1(), sel ? 0x88D83A3A : z.has(mx, my) && !dragging ? 0x55FFFFFF : 0x33FFFFFF);
        if (sel) SquadHud.frame(g, z.x0(), z.y0(), z.x1(), z.y1(), RED);
        if ((m.flags() & Member.FIGHTING) != 0) g.fill(z.x0(), z.y0(), z.x0() + 2, z.y1(), ORANGE);
        else if ((m.flags() & Member.RESUPPLYING) != 0) g.fill(z.x0(), z.y0(), z.x0() + 2, z.y1(), BLUE);
        var info = minecraft.getConnection() == null ? null : minecraft.getConnection().getPlayerInfo(m.uuid());
        PlayerFaceRenderer.draw(g, info != null ? info.getSkin() : DefaultPlayerSkin.get(m.uuid()), z.x0() + 4, z.y0() + 3, 14);
        boolean down = (m.flags() & Member.DOWNED) != 0;
        if (down) g.drawCenteredString(font, "X", z.x0() + 11, z.y0() + 6, 0xFFFF6060);
        int tx = z.x0() + 21, room = z.x1() - tx - 3;
        int alpha = lifted ? 0x66000000 : 0xFF000000;
        g.drawString(font, font.plainSubstrByWidth(m.name(), room), tx, z.y0() + 2, alpha | (down ? 0xFF6060 : 0xFFFFFF), false);
        g.drawString(font, font.plainSubstrByWidth(m.order(), room), tx, z.y0() + 12, alpha | ((m.flags() & Member.FIGHTING) != 0 ? 0xE8963C : 0x9A9A9A), false);
        int hp = (z.x1() - z.x0() - 2) * m.health() / 100;
        g.fill(z.x0() + 1, z.y1() - 2, z.x1() - 1, z.y1() - 1, 0xFF202020);
        g.fill(z.x0() + 1, z.y1() - 2, z.x0() + 1 + hp, z.y1() - 1, m.health() > 50 ? GREEN : m.health() > 25 ? 0xFFE0B040 : RED);
    }

    private void right(GuiGraphics g, int mx, int my) {
        int x0 = cx + leftW() + 10, x1 = cx + cw, y = cy;
        g.drawString(font, "Orders", x0, y + 3, DIM, false);
        y += HEAD;
        String who = selected.isEmpty() ? "everyone" : selected.size() + " selected";
        g.drawString(font, who, x1 - font.width(who), cy + 3, selected.isEmpty() ? DIM : WHITE, false);
        for (String[] o : new String[][]{{"follow", "Follow me"}, {"hold", "Hold here"}, {"regroup", "Regroup"}, {"resupply", "Resupply"}}) {
            Zone z = new Zone(x0, y, x1, y + 18);
            tile(g, z, o[1], mx, my);
            tiles.add(new Tile(z, o[0], "", o[1]));
            y += 20;
        }
        Zone gearZone = new Zone(x0, y, x1, y + 18);
        tile(g, gearZone, "Gear...", mx, my);
        acts.add(new Act(gearZone, () -> GearScreen.open(ordered(selected.isEmpty() ? roster().stream().map(Member::uuid).collect(java.util.stream.Collectors.toSet()) : selected))));
        y += 24;
        g.drawString(font, "Missions", x0, y + 3, DIM, false);
        Zone pz = new Zone(x1 - 84, y, x1 - 42, y + 12), gz = new Zone(x1 - 42, y, x1, y + 12);
        segment(g, pz, "Patrol", patrol, mx, my);
        segment(g, gz, "Guard", !patrol, mx, my);
        acts.add(new Act(pz, () -> patrol = true));
        acts.add(new Act(gz, () -> patrol = false));
        y += 15;
        int bottom = cy + ch - 24;
        List<TerritoryInfo> land = SquadClient.world.list();
        int total = land.size() * 20;
        tScroll = Math.max(0, Math.min(tScroll, Math.max(0, total - (bottom - y))));
        g.enableScissor(x0, y, x1, bottom);
        for (int i = 0; i < land.size(); i++) {
            TerritoryInfo t = land.get(i);
            int ty = y + i * 20 - (int) tScroll;
            Zone z = new Zone(x0, ty, x1, ty + 18);
            if (z.y1() < y || z.y0() > bottom) continue;
            String label = t.name() + (t.garrison() >= 0 ? "  " + t.alive() + "/" + t.garrison() : "");
            tile(g, z, label, mx, my);
            if (t.mine() > 0) g.drawString(font, Integer.toString(t.mine()), x1 - 4 - font.width(Integer.toString(t.mine())), ty + 5, GREEN, false);
            tiles.add(new Tile(new Zone(z.x0(), Math.max(z.y0(), y), z.x1(), Math.min(z.y1(), bottom)), patrol ? "patrol" : "guard", t.id(),
                    (patrol ? "Patrol " : "Guard ") + t.name()));
        }
        g.disableScissor();
        if (land.isEmpty()) g.drawString(font, "No territories", x0 + 4, y + 4, DIM, false);
        Zone d = new Zone(x0, cy + ch - 20, x1, cy + ch);
        discharge = d;
        boolean on = dragging && d.has(mx, my);
        g.fill(d.x0(), d.y0(), d.x1(), d.y1(), on ? 0xCCD83A3A : 0x33D83A3A);
        SquadHud.frame(g, d.x0(), d.y0(), d.x1(), d.y1(), on ? WHITE : 0xFF803030);
        g.drawCenteredString(font, dragging ? "Drop to discharge" : "Discharge", (d.x0() + d.x1()) / 2, d.y0() + 6, on ? WHITE : 0xFFC08080);
    }

    private void tile(GuiGraphics g, Zone z, String label, int mx, int my) {
        boolean over = z.has(mx, my);
        boolean target = dragging && over;
        g.fill(z.x0(), z.y0(), z.x1(), z.y1(), target ? 0xCC4A90E0 : over ? 0x66FFFFFF : 0x33FFFFFF);
        if (target) SquadHud.frame(g, z.x0(), z.y0(), z.x1(), z.y1(), WHITE);
        g.drawString(font, font.plainSubstrByWidth(label, z.x1() - z.x0() - 18), z.x0() + 5, z.y0() + 5, WHITE, false);
    }

    private void segment(GuiGraphics g, Zone z, String label, boolean on, int mx, int my) {
        g.fill(z.x0(), z.y0(), z.x1(), z.y1(), on ? 0xFF4A90E0 : z.has(mx, my) ? 0x66FFFFFF : 0x33FFFFFF);
        g.drawCenteredString(font, label, (z.x0() + z.x1()) / 2, z.y0() + 2, on ? WHITE : DIM);
    }

    private void ghost(GuiGraphics g, int mx, int my) {
        int shown = Math.min(3, carried.size());
        for (int i = shown - 1; i >= 0; i--) {
            UUID id = carried.get(i);
            var info = minecraft.getConnection() == null ? null : minecraft.getConnection().getPlayerInfo(id);
            g.fill(mx + 4 + i * 5, my + 4 + i * 5, mx + 24 + i * 5, my + 24 + i * 5, 0xCC000000);
            PlayerFaceRenderer.draw(g, info != null ? info.getSkin() : DefaultPlayerSkin.get(id), mx + 6 + i * 5, my + 6 + i * 5, 16);
        }
        if (carried.size() > 1) g.drawString(font, Integer.toString(carried.size()), mx + 26 + shown * 5, my + 8, WHITE, true);
    }

    // ---- territories tab

    private void territories(GuiGraphics g, int mx, int my) {
        List<TerritoryInfo> land = SquadClient.world.list();
        boolean ops = SquadClient.world.canEdit();
        int card = 40, y0 = cy, bottom = cy + ch - (ops ? 22 : 0);
        tScroll = Math.max(0, Math.min(tScroll, Math.max(0, land.size() * (card + 4) - (bottom - y0))));
        g.enableScissor(cx, y0, cx + cw, bottom);
        for (int i = 0; i < land.size(); i++) {
            TerritoryInfo t = land.get(i);
            int y = y0 + i * (card + 4) - (int) tScroll;
            if (y + card < y0 || y > bottom) continue;
            g.fill(cx, y, cx + cw, y + card, 0x44000000);
            g.drawString(font, t.name(), cx + 6, y + 5, WHITE, false);
            String where = "r" + t.radius() + "  at " + t.x() + ", " + t.y() + ", " + t.z() + (t.mine() > 0 ? "   " + t.mine() + " of yours" : "");
            g.drawString(font, where, cx + 6 + font.width(t.name()) + 10, y + 5, DIM, false);
            if (!ops) {
                g.drawString(font, t.garrison() < 0 ? "No garrison" : "Garrison " + t.alive() + "/" + t.garrison() + " " + t.mode(), cx + 6, y + 21, DIM, false);
                continue;
            }
            Zone x = new Zone(cx + cw - 16, y + 3, cx + cw - 4, y + 15);
            small(g, x, "x", mx, my, y >= y0 - 2);
            acts.add(new Act(clip(x, y0, bottom), () -> SquadClient.manage("territory_delete", List.of(), "", t.id(), "", 0, 0)));
            int cx0 = cx + 6, ry = y + 21;
            g.drawString(font, "Garrison", cx0, ry + 2, DIM, false);
            int sx = cx0 + font.width("Garrison") + 6;
            sx = stepper(g, sx, ry, t.garrison() < 0 ? "none" : t.alive() + "/" + t.garrison(), mx, my, y0, bottom, () -> step(t, -1), () -> step(t, 1));
            if (t.garrison() >= 0) {
                sx += 10;
                Zone pz = new Zone(sx, ry, sx + 40, ry + 12), gz = new Zone(sx + 40, ry, sx + 80, ry + 12);
                segment(g, pz, "Patrol", t.mode().equals("patrol"), mx, my);
                segment(g, gz, "Guard", t.mode().equals("guard"), mx, my);
                acts.add(new Act(clip(pz, y0, bottom), () -> {
                    if (!t.mode().equals("patrol")) mode(t);
                }));
                acts.add(new Act(clip(gz, y0, bottom), () -> {
                    if (!t.mode().equals("guard")) mode(t);
                }));
                sx += 90;
                g.drawString(font, "Reinforce", sx, ry + 2, DIM, false);
                sx = stepper(g, sx + font.width("Reinforce") + 6, ry, t.respawn() + "s", mx, my, y0, bottom, () -> delay(t, -1), () -> delay(t, 1));
                sx += 10;
                String unit = t.units().isEmpty() ? "all variants" : t.units();
                Zone uz = new Zone(sx, ry, Math.min(cx + cw - 8, sx + font.width(unit) + 10), ry + 12);
                g.fill(uz.x0(), uz.y0(), uz.x1(), uz.y1(), uz.has(mx, my) ? 0x66FFFFFF : 0x33FFFFFF);
                g.drawString(font, font.plainSubstrByWidth(unit, uz.x1() - uz.x0() - 6), uz.x0() + 4, ry + 2, WHITE, false);
                acts.add(new Act(clip(uz, y0, bottom), () -> variant(t)));
            }
        }
        g.disableScissor();
        if (land.isEmpty()) g.drawString(font, "No territories yet", cx + 6, y0 + 6, DIM, false);
        if (ops) {
            Zone z = new Zone(cx, cy + ch - 18, cx + 170, cy + ch);
            g.fill(z.x0(), z.y0(), z.x1(), z.y1(), z.has(mx, my) ? 0x66FFFFFF : 0x33FFFFFF);
            g.drawString(font, "+ New territory where you stand", z.x0() + 6, z.y0() + 5, WHITE, false);
            acts.add(new Act(z, () -> SquadClient.manage("territory_new", List.of(), "", "", "", 32, 0)));
        }
    }

    private static Zone clip(Zone z, int top, int bottom) {
        return new Zone(z.x0(), Math.max(z.y0(), top), z.x1(), Math.min(z.y1(), bottom));
    }

    private void small(GuiGraphics g, Zone z, String label, int mx, int my, boolean live) {
        g.fill(z.x0(), z.y0(), z.x1(), z.y1(), live && z.has(mx, my) ? 0xAAD83A3A : 0x33FFFFFF);
        g.drawCenteredString(font, label, (z.x0() + z.x1()) / 2, z.y0() + 2, WHITE);
    }

    /** A value with a minus and a plus either side; returns where it ends. */
    private int stepper(GuiGraphics g, int x, int y, String value, int mx, int my, int top, int bottom, Runnable minus, Runnable plus) {
        Zone a = new Zone(x, y, x + 12, y + 12), b = new Zone(x + 16 + font.width(value) + 4, y, x + 28 + font.width(value) + 4, y + 12);
        small(g, a, "-", mx, my, true);
        g.drawString(font, value, x + 16, y + 2, WHITE, false);
        small(g, b, "+", mx, my, true);
        acts.add(new Act(clip(a, top, bottom), minus));
        acts.add(new Act(clip(b, top, bottom), plus));
        return b.x1();
    }

    // ---- mouse

    private Zone columnAt(double mx, double my) {
        for (int t = 0; t < Companion.TEAMS.length; t++) if (column(t).has(mx, my)) return column(t);
        return null;
    }

    private int teamOf(Zone col) {
        for (int t = 0; t < Companion.TEAMS.length; t++) if (column(t).equals(col)) return t;
        return 0;
    }

    /** Where in a team a drop at height {@code my} lands: before the first soldier (not one being carried) whose middle is below it. */
    private int dropIndex(int team, double my) {
        int idx = 0, i = 0;
        for (Member m : team(team)) {
            int mid = bodyTop() + i++ * ROW - (int) scroll + ROW / 2;
            if (carried.contains(m.uuid())) continue;
            if (my < mid) return idx;
            idx++;
        }
        return idx;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        for (Act a : acts) {
            if (a.zone().has(mx, my)) {
                a.run().run();
                return true;
            }
        }
        if (tab != Tab.SQUAD) return false;
        pressX = mx;
        pressY = my;
        pressed = null;
        pressedTeam = -1;
        for (Chip c : chips()) {
            if (visible(c.zone()) && c.zone().has(mx, my) && my >= bodyTop()) {
                pressed = c.member();
                return true;
            }
        }
        for (int t = 0; t < Companion.TEAMS.length; t++) {
            if (new Zone(colX(t), cy, colX(t) + colW(), cy + HEAD).has(mx, my)) {
                pressedTeam = t;
                return true;
            }
        }
        for (Tile t : tiles) {
            if (t.zone().has(mx, my)) {
                order(ordered(selected), t.kind(), t.region());
                say((selected.isEmpty() ? "Everyone: " : selected.size() + (selected.size() == 1 ? " soldier: " : " soldiers: ")) + t.label());
                return true;
            }
        }
        selected.clear();
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!dragging && (pressed != null || pressedTeam >= 0) && Math.hypot(mx - pressX, my - pressY) > DRAG_START) {
            if (pressedTeam >= 0) {
                selected.clear();
                team(pressedTeam).forEach(m -> selected.add(m.uuid()));
            } else if (!selected.contains(pressed.uuid())) {
                selected.clear();
                selected.add(pressed.uuid());
            }
            carried = ordered(selected);
            dragging = !carried.isEmpty();
        }
        return dragging || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button != 0) return super.mouseReleased(mx, my, button);
        if (dragging) {
            Zone col = columnAt(mx, my);
            if (col != null && my > cy) {
                int team = teamOf(col);
                SquadClient.manage("move", carried, "", "", "", team, dropIndex(team, my));
                say(carried.size() + (carried.size() == 1 ? " soldier" : " soldiers") + " to " + Companion.TEAMS[team]);
            } else if (discharge != null && discharge.has(mx, my)) {
                SquadClient.manage("dismiss", carried, "", "", "", 0, 0);
                selected.removeAll(carried);
                say("Discharged " + carried.size() + "; they hold where they stand");
            } else {
                for (Tile t : tiles) {
                    if (t.zone().has(mx, my)) {
                        drop(t);
                        break;
                    }
                }
            }
        } else if (pressedTeam >= 0) {                                       // a click on a team's name: select the team
            if (!hasShiftDown() && !hasControlDown()) selected.clear();
            team(pressedTeam).forEach(m -> selected.add(m.uuid()));
        } else if (pressed != null) {                                        // a click on a soldier
            if (hasShiftDown() || hasControlDown()) {
                if (!selected.remove(pressed.uuid())) selected.add(pressed.uuid());
            } else {
                boolean only = selected.size() == 1 && selected.contains(pressed.uuid());
                selected.clear();
                if (!only) selected.add(pressed.uuid());
            }
        }
        dragging = false;
        pressed = null;
        pressedTeam = -1;
        carried = List.of();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        boolean right = mx > cx + leftW();
        if (tab == Tab.TERRITORIES || right) tScroll -= sy * 20;
        else scroll -= sy * ROW * 2;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (SquadClient.MANAGE.matches(key, scan)) {
            onClose();
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_A && hasControlDown()) {
            roster().forEach(m -> selected.add(m.uuid()));
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void tick() {
        if (minecraft != null && minecraft.player != null && minecraft.player.tickCount % 60 == 0) SquadClient.manage("list", List.of(), "", "", "", 0, 0);
        Set<UUID> alive = new LinkedHashSet<>();
        roster().forEach(m -> alive.add(m.uuid()));
        selected.retainAll(alive);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
