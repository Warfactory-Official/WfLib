package com.wf.wflib.soldier.client;

import com.wf.wflib.soldier.Companion;
import com.wf.wflib.soldier.SquadPackets.Member;
import com.wf.wflib.soldier.SquadPackets.TerritoryInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The squad's management screen ({@code B}): every soldier grouped by fire team, tick any number of them (a whole team with its
 * header) and give the lot an order, a new fire team or their discharge; and the world's territories, with the garrison each
 * raises for itself. Everything here is a request: the server checks the soldiers are yours and the edits are allowed.
 */
final class SquadScreen extends Screen {
    private static final int ROW = 13, RED = 0xFFD83A3A, PANEL = 0xEE101010, DIM = 0xFF8A8A8A, GREEN = 0xFF58C458, REFRESH = 60;

    private enum Tab { SQUAD, TERRITORIES }

    /** One line of a list: a fire team's header, a soldier, or a territory. */
    private record Row(int team, Member member, TerritoryInfo territory) {}

    private Tab tab = Tab.SQUAD;
    private final Set<UUID> selected = new HashSet<>();
    private String territory = "";
    private String filter = "";
    private double scroll;
    private boolean sure;
    private EditBox search;
    private int px, py, pw, ph, lx, ly, lw, lh;

    SquadScreen() {
        super(Component.translatable("wflib.squad.manage"));
    }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 460);
        ph = Math.min(height - 16, 300);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        lx = px + 8;
        ly = py + 30;
        lw = pw - 16;
        lh = ph - 30 - 62;
        addRenderableWidget(Button.builder(Component.literal("Squad"), b -> tab(Tab.SQUAD)).bounds(px + 8, py + 6, 64, 16).build());
        addRenderableWidget(Button.builder(Component.literal("Territories"), b -> tab(Tab.TERRITORIES)).bounds(px + 76, py + 6, 80, 16).build());
        search = new EditBox(font, px + pw - 128, py + 6, 120, 16, Component.literal("Search"));
        search.setHint(Component.literal("Search").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        search.setValue(filter);
        search.setResponder(v -> {
            filter = v.toLowerCase(Locale.ROOT);
            scroll = 0;
        });
        if (tab == Tab.SQUAD) {
            addRenderableWidget(search);
            squadActions();
        } else {
            territoryActions();
        }
        SquadClient.manage("list", List.of(), "", "", "", 0, 0);
    }

    private void tab(Tab t) {
        tab = t;
        scroll = 0;
        sure = false;
        rebuildWidgets();
    }

    /** Buttons spread evenly over a row of the panel. */
    private void row(int y, Object... pairs) {
        int n = pairs.length / 2, gap = 3, w = (lw - gap * (n - 1)) / n;
        for (int i = 0; i < n; i++) {
            Runnable action = (Runnable) pairs[i * 2 + 1];
            addRenderableWidget(Button.builder(Component.literal((String) pairs[i * 2]), b -> action.run()).bounds(lx + i * (w + gap), y, w, 16).build());
        }
    }

    private int top() {
        return py + ph - 56;
    }

    private void squadActions() {
        row(top(), "Select all", (Runnable) () -> members().forEach(m -> selected.add(m.uuid())), "None", (Runnable) selected::clear,
                "Follow", (Runnable) () -> order("follow", ""), "Hold", (Runnable) () -> order("hold", ""),
                "Regroup", (Runnable) () -> order("regroup", ""), "Resupply", (Runnable) () -> order("resupply", ""));
        List<TerritoryInfo> land = SquadClient.world.list();
        String label = territory.isEmpty() ? (land.isEmpty() ? "No territory" : "Pick territory") : territory;
        row(top() + 20, label + " >", (Runnable) this::nextTerritory, "Patrol", (Runnable) () -> order("patrol", territory),
                "Guard", (Runnable) () -> order("guard", territory),
                "Alpha", (Runnable) () -> team(0), "Bravo", (Runnable) () -> team(1), "Charlie", (Runnable) () -> team(2), "Delta", (Runnable) () -> team(3),
                sure ? "Sure?" : "Dismiss", (Runnable) this::dismiss);
    }

    private void territoryActions() {
        row(top(), "Patrol with selected", (Runnable) () -> order("patrol", territory), "Guard with selected", (Runnable) () -> order("guard", territory));
        if (!SquadClient.world.canEdit()) return;
        row(top() + 20, "Size -", (Runnable) () -> resize(-1), "Size +", (Runnable) () -> resize(1), "Mode", (Runnable) this::flipMode,
                "Delay -", (Runnable) () -> delay(-10), "Delay +", (Runnable) () -> delay(10), "Unit >", (Runnable) this::nextUnit,
                "New here", (Runnable) () -> SquadClient.manage("territory_new", List.of(), "", "", "", 32, 0),
                "Remove", (Runnable) () -> {
                    if (!territory.isEmpty()) SquadClient.manage("territory_delete", List.of(), "", territory, "", 0, 0);
                    territory = "";
                });
    }

    private List<Member> members() {
        return SquadClient.members.stream().filter(m -> filter.isEmpty() || (m.name() + " " + m.role() + " " + m.order()).toLowerCase(Locale.ROOT).contains(filter)).toList();
    }

    private void order(String kind, String region) {
        if (selected.isEmpty()) return;
        if ((kind.equals("patrol") || kind.equals("guard")) && region.isEmpty()) return;
        SquadClient.manage("order", selected, kind, region, "", 0, 0);
    }

    private void team(int t) {
        if (!selected.isEmpty()) SquadClient.manage("team", selected, "", "", "", t, 0);
    }

    private void dismiss() {
        if (selected.isEmpty()) return;
        if (!sure) {
            sure = true;
            rebuildWidgets();
            return;
        }
        SquadClient.manage("dismiss", selected, "", "", "", 0, 0);
        selected.clear();
        sure = false;
        rebuildWidgets();
    }

    private void nextTerritory() {
        List<TerritoryInfo> land = SquadClient.world.list();
        if (land.isEmpty()) return;
        int at = -1;
        for (int i = 0; i < land.size(); i++) if (land.get(i).id().equals(territory)) at = i;
        territory = land.get((at + 1) % land.size()).id();
        rebuildWidgets();
    }

    private TerritoryInfo chosen() {
        for (TerritoryInfo t : SquadClient.world.list()) if (t.id().equals(territory)) return t;
        return null;
    }

    /** Sends the picked territory's garrison with one thing changed: a new size, mode, delay or unit list. */
    private void garrison(int size, String mode, int delay, String units) {
        if (territory.isEmpty()) return;
        SquadClient.manage("garrison", List.of(), size <= 0 ? "" : mode, territory, units, size, delay);
    }

    private void resize(int by) {
        TerritoryInfo t = chosen();
        if (t == null) return;
        int step = hasShiftDown() ? 10 : 1;
        garrison(Math.max(0, Math.max(t.garrison(), 0) + by * step), t.mode().isEmpty() ? "patrol" : t.mode(), t.garrison() < 0 ? 60 : t.respawn(), t.units());
    }

    private void flipMode() {
        TerritoryInfo t = chosen();
        if (t == null || t.garrison() < 0) return;
        garrison(t.garrison(), t.mode().equals("patrol") ? "guard" : "patrol", t.respawn(), t.units());
    }

    private void delay(int by) {
        TerritoryInfo t = chosen();
        if (t == null || t.garrison() < 0) return;
        garrison(t.garrison(), t.mode(), Math.max(0, t.respawn() + by), t.units());
    }

    /** All saved variants (mixed), then each one alone, then mixed again. */
    private void nextUnit() {
        TerritoryInfo t = chosen();
        List<String> variants = SquadClient.world.variants();
        if (t == null || t.garrison() < 0 || variants.isEmpty()) return;
        int at = t.units().isEmpty() ? -1 : variants.indexOf(t.units());
        String next = at + 1 >= variants.size() ? "" : variants.get(at + 1);
        garrison(t.garrison(), t.mode(), t.respawn(), next);
    }

    private List<Row> rows() {
        List<Row> out = new ArrayList<>();
        if (tab == Tab.SQUAD) {
            for (int team = 0; team < Companion.TEAMS.length; team++) {
                int t = team;
                List<Member> in = members().stream().filter(m -> m.team() == t).toList();
                if (in.isEmpty()) continue;
                out.add(new Row(t, null, null));
                in.forEach(m -> out.add(new Row(t, m, null)));
            }
        } else {
            SquadClient.world.list().stream().filter(t -> filter.isEmpty() || t.name().toLowerCase(Locale.ROOT).contains(filter)).forEach(t -> out.add(new Row(-1, null, t)));
        }
        return out;
    }

    @Override
    public void tick() {
        if (minecraft != null && minecraft.player != null && minecraft.player.tickCount % REFRESH == 0) SquadClient.manage("list", List.of(), "", "", "", 0, 0);
        Set<UUID> alive = new HashSet<>();
        SquadClient.members.forEach(m -> alive.add(m.uuid()));
        selected.retainAll(alive);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0x88000000);
        g.fill(px, py, px + pw, py + ph, PANEL);
        SquadHud.frame(g, px, py, px + pw, py + ph, 0xFF606060);
        g.fill(lx, ly, lx + lw, ly + lh, 0x66000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        List<Row> rows = rows();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() * ROW - lh)));
        g.enableScissor(lx, ly, lx + lw, ly + lh);
        for (int i = 0; i < rows.size(); i++) {
            int y = ly + i * ROW - (int) scroll;
            if (y + ROW < ly || y > ly + lh) continue;
            Row r = rows.get(i);
            if (r.territory() != null) territoryRow(g, r.territory(), y, mx, my);
            else if (r.member() == null) header(g, r.team(), y);
            else memberRow(g, r.member(), y, mx, my);
        }
        g.disableScissor();
        if (rows.isEmpty()) {
            String none = tab == Tab.SQUAD ? "No soldiers. Sneak and use a friendly soldier with an empty hand to recruit it." : "No territories.";
            g.drawCenteredString(font, none, lx + lw / 2, ly + lh / 2 - 4, DIM);
        }
        String status = tab == Tab.SQUAD ? selected.size() + " of " + SquadClient.members.size() + " selected"
                : (territory.isEmpty() ? "Pick a territory" : territory + ": " + garrisonText(chosen()));
        g.drawString(font, status, lx, py + ph - 12, 0xFFB0B0B0, false);
        if (tab == Tab.TERRITORIES && SquadClient.world.canEdit())
            g.drawString(font, "shift: x10", px + pw - 8 - font.width("shift: x10"), py + ph - 12, DIM, false);
    }

    private void header(GuiGraphics g, int team, int y) {
        boolean all = SquadClient.members.stream().filter(m -> m.team() == team).allMatch(m -> selected.contains(m.uuid()));
        g.fill(lx, y, lx + lw, y + ROW - 1, 0x55FFFFFF);
        box(g, lx + 3, y + 2, all);
        long n = SquadClient.members.stream().filter(m -> m.team() == team).count();
        g.drawString(font, Companion.TEAMS[team] + "  (" + n + ")", lx + 16, y + 2, 0xFFFFFFFF, false);
    }

    private void memberRow(GuiGraphics g, Member m, int y, int mx, int my) {
        boolean on = selected.contains(m.uuid());
        if (on) g.fill(lx, y, lx + lw, y + ROW - 1, 0x44D83A3A);
        else if (mx >= lx && mx < lx + lw && my >= y && my < y + ROW - 1 && my >= ly && my < ly + lh) g.fill(lx, y, lx + lw, y + ROW - 1, 0x22FFFFFF);
        box(g, lx + 3, y + 2, on);
        var info = minecraft.getConnection() == null ? null : minecraft.getConnection().getPlayerInfo(m.uuid());
        PlayerFaceRenderer.draw(g, info != null ? info.getSkin() : DefaultPlayerSkin.get(m.uuid()), lx + 16, y + 1, 10);
        boolean downed = (m.flags() & Member.DOWNED) != 0;
        g.drawString(font, m.name(), lx + 30, y + 2, downed ? 0xFFFF6060 : 0xFFFFFFFF, false);
        int c2 = lx + Math.min(120, lw / 4);
        g.drawString(font, m.role(), c2 + 12, y + 2, DIM, false);
        g.drawString(font, m.order(), c2 + 62, y + 2, (m.flags() & Member.FIGHTING) != 0 ? 0xFFE8963C : 0xFFC8C8C8, false);
        int bx = lx + lw - 62;
        g.fill(bx, y + 3, bx + 30, y + 6, 0xFF202020);
        g.fill(bx, y + 3, bx + 30 * m.health() / 100, y + 6, m.health() > 50 ? GREEN : m.health() > 25 ? 0xFFE0B040 : RED);
        if (m.ammo() >= 0) {
            g.fill(bx, y + 7, bx + 30, y + 9, 0xFF202020);
            g.fill(bx, y + 7, bx + 30 * m.ammo() / 100, y + 9, m.mags() == 0 && m.ammo() < 30 ? RED : 0xFFB0B0B0);
            g.drawString(font, Integer.toString(m.mags()), bx + 34, y + 2, 0xFFB0B0B0, false);
        }
    }

    private void territoryRow(GuiGraphics g, TerritoryInfo t, int y, int mx, int my) {
        boolean on = t.id().equals(territory);
        if (on) g.fill(lx, y, lx + lw, y + ROW - 1, 0x44D83A3A);
        else if (mx >= lx && mx < lx + lw && my >= y && my < y + ROW - 1 && my >= ly && my < ly + lh) g.fill(lx, y, lx + lw, y + ROW - 1, 0x22FFFFFF);
        g.drawString(font, t.name(), lx + 4, y + 2, 0xFFFFFFFF, false);
        g.drawString(font, "r" + t.radius(), lx + lw / 3, y + 2, DIM, false);
        g.drawString(font, garrisonText(t), lx + lw / 3 + 40, y + 2, t.garrison() >= 0 && t.alive() < t.garrison() ? 0xFFE0B040 : 0xFFC8C8C8, false);
        if (t.mine() > 0) g.drawString(font, t.mine() + " yours", lx + lw - 8 - font.width(t.mine() + " yours"), y + 2, GREEN, false);
    }

    private static String garrisonText(TerritoryInfo t) {
        if (t == null) return "";
        if (t.garrison() < 0) return "no garrison";
        return t.alive() + "/" + t.garrison() + " " + t.mode() + ", +" + t.respawn() + "s" + (t.units().isEmpty() ? ", mixed" : ", " + t.units());
    }

    private void box(GuiGraphics g, int x, int y, boolean on) {
        SquadHud.frame(g, x, y, x + 9, y + 9, 0xFFB0B0B0);
        if (on) g.fill(x + 2, y + 2, x + 7, y + 7, RED);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (mx < lx || mx >= lx + lw || my < ly || my >= ly + lh) return false;
        List<Row> rows = rows();
        int i = (int) ((my - ly + scroll) / ROW);
        if (i < 0 || i >= rows.size()) return false;
        Row r = rows.get(i);
        if (r.territory() != null) {
            territory = r.territory().id();
            rebuildWidgets();
        } else if (r.member() == null) {
            List<UUID> ids = SquadClient.members.stream().filter(m -> m.team() == r.team()).map(Member::uuid).toList();
            if (selected.containsAll(ids)) ids.forEach(selected::remove);
            else selected.addAll(ids);
        } else if (!selected.remove(r.member().uuid())) {
            selected.add(r.member().uuid());
        }
        if (sure) {
            sure = false;
            rebuildWidgets();
        }
        return true;
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
