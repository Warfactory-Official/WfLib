package com.wf.wflib.soldier.client;

import com.wf.wflib.soldier.Companion;
import com.wf.wflib.soldier.SquadPackets.Member;
import com.wf.wflib.soldier.SquadPackets.TerritoryInfo;
import com.wf.wflib.ui.api.Signals;
import com.wf.wflib.ui.api.Subject;
import com.wf.wflib.ui.api.UiContext;
import com.wf.wflib.ui.api.UiHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * State and signals of {@link SquadScreen}. Every drop is a request; the server checks the soldiers are the player's and the
 * edit is allowed.
 */
final class SquadManage implements UiContext {
    static final int ROW = 24, HEAD = 14;
    private static final int HINT_TICKS = 120;

    enum Tab { SQUAD, TERRITORIES }

    static final class Team {
        final int index;
        private final SquadManage model;

        Team(int index, SquadManage model) {
            this.index = index;
            this.model = model;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Team t && t.index == index;
        }

        @Override
        public int hashCode() {
            return index;
        }
    }

    static final class Chip {
        final Member member;
        private final SquadManage model;

        Chip(Member member, SquadManage model) {
            this.member = member;
            this.model = model;
        }

        boolean has(int flag) {
            return (member.flags() & flag) != 0;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Chip c && c.member.uuid().equals(member.uuid());
        }

        @Override
        public int hashCode() {
            return member.uuid().hashCode();
        }
    }

    static final class Land {
        final TerritoryInfo info;

        Land(TerritoryInfo info) {
            this.info = info;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Land l && l.info.id().equals(info.id());
        }

        @Override
        public int hashCode() {
            return info.id().hashCode();
        }
    }

    static final Subject<SquadManage> UI = Subject.register("squad_ui", SquadManage.class);
    static final Subject<Team> TEAM = Subject.register("squad_team", Team.class);
    static final Subject<Chip> CHIP = Subject.register("squad_chip", Chip.class);
    static final Subject<Land> LAND = Subject.register("squad_land", Land.class);

    static {
        Signals.bool(UI, "squad_tab", m -> m.tab == Tab.SQUAD);
        Signals.bool(UI, "can_edit", m -> SquadClient.world.canEdit());
        Signals.bool(UI, "dragging", m -> !m.carried.isEmpty());
        Signals.bool(UI, "has_selection", m -> !m.selected.isEmpty());
        Signals.bool(UI, "empty", m -> SquadClient.members.isEmpty());
        Signals.bool(UI, "no_land", m -> SquadClient.world.list().isEmpty());
        Signals.bool(UI, "patrol", m -> m.patrol);
        Signals.bool(UI, "line_hot", m -> m.hintLive());
        Signals.num(UI, "scroll", m -> m.scroll());
        Signals.num(UI, "insert_team", m -> m.insertLive() ? m.insertTeam : -1);
        Signals.num(UI, "insert_y", m -> m.insertIdx * ROW - m.scroll() - 1);
        Signals.num(UI, "ghost_n", m -> m.carried.size());
        Signals.text(UI, "who", 24, m -> m.selected.isEmpty() ? "everyone" : m.selected.size() + " selected");
        Signals.text(UI, "count", 24, m -> plural(SquadClient.members.size(), "soldier"));
        Signals.text(UI, "line", 120, SquadManage::line);
        Signals.text(UI, "lamp", 16, m -> m.lampsOn() ? "Headlamp: on" : "Headlamp: off");
        Signals.items(UI, "teams", m -> m.teams(), TEAM);
        Signals.items(UI, "land", m -> m.lands(), LAND);
        Signals.items(UI, "ghost", m -> m.ghost(), CHIP);

        Signals.text(TEAM, "name", 24, t -> Companion.TEAMS[t.index] + " " + t.model.members(t.index).size());
        Signals.bool(TEAM, "all", t -> t.model.teamSelected(t.index));
        Signals.items(TEAM, "members", t -> t.model.chips(t.index), CHIP);

        Signals.text(CHIP, "name", 32, c -> c.member.name());
        Signals.text(CHIP, "order", 64, c -> c.member.order());
        Signals.num(CHIP, "health", c -> c.member.health());
        Signals.bool(CHIP, "sel", c -> c.model.selected.contains(c.member.uuid()));
        Signals.bool(CHIP, "lifted", c -> c.model.carried.contains(c.member.uuid()));
        Signals.bool(CHIP, "down", c -> c.has(Member.DOWNED));
        Signals.bool(CHIP, "fighting", c -> c.has(Member.FIGHTING));
        Signals.bool(CHIP, "resupplying", c -> c.has(Member.RESUPPLYING));

        Signals.text(LAND, "label", 64, l -> l.info.name() + (l.info.garrison() >= 0 ? "  " + l.info.alive() + "/" + l.info.garrison() : ""));
        Signals.text(LAND, "name", 48, l -> l.info.name());
        Signals.text(LAND, "where", 96, l -> "r" + l.info.radius() + "  at " + l.info.x() + ", " + l.info.y() + ", " + l.info.z()
                + (l.info.mine() > 0 ? "   " + l.info.mine() + " of yours" : ""));
        Signals.text(LAND, "status", 48, l -> l.info.garrison() < 0 ? "No garrison"
                : "Garrison " + l.info.alive() + "/" + l.info.garrison() + " " + l.info.mode());
        Signals.text(LAND, "value", 16, l -> l.info.garrison() < 0 ? "none" : l.info.alive() + "/" + l.info.garrison());
        Signals.text(LAND, "respawn", 8, l -> l.info.respawn() + "s");
        Signals.text(LAND, "variant", 64, l -> l.info.units().isEmpty() ? "all variants" : l.info.units());
        Signals.text(LAND, "mine_text", 8, l -> Integer.toString(l.info.mine()));
        Signals.bool(LAND, "has_mine", l -> l.info.mine() > 0);
        Signals.bool(LAND, "has_garrison", l -> l.info.garrison() >= 0);
        Signals.bool(LAND, "is_patrol", l -> l.info.mode().equals("patrol"));
        Signals.bool(LAND, "is_guard", l -> l.info.mode().equals("guard"));
    }

    private Tab tab = Tab.SQUAD;
    private final Set<UUID> selected = new LinkedHashSet<>();
    private boolean patrol = true;
    private double scroll;
    private List<UUID> carried = List.of();
    private String hint = "";
    private long hintAt;
    private int insertTeam = -1, insertIdx;
    private long insertFrame = Long.MIN_VALUE;

    private final List<Team> teams = new ArrayList<>();
    private List<Member> seenRoster;
    private final Map<Integer, List<Chip>> chips = new HashMap<>();
    private List<TerritoryInfo> seenLand;
    private List<Land> lands = List.of();
    private List<UUID> ghostFor = List.of();
    private List<Chip> ghost = List.of();

    SquadManage() {
        for (int t = 0; t < Companion.TEAMS.length; t++) teams.add(new Team(t, this));
    }

    @Override
    public Object subject(Subject<?> subject) {
        return subject == UI ? this : null;
    }

    // ---- signal sources

    private static String plural(int n, String noun) {
        return n + " " + noun + (n == 1 ? "" : "s");
    }

    private List<Team> teams() {
        return teams;
    }

    private void refresh() {
        List<Member> roster = SquadClient.members;
        if (roster == seenRoster) return;
        seenRoster = roster;
        chips.clear();
        for (int t = 0; t < Companion.TEAMS.length; t++) {
            int team = t;
            chips.put(t, roster.stream().filter(m -> m.team() == team).map(m -> new Chip(m, this)).toList());
        }
        ghostFor = List.of();
    }

    private List<Chip> chips(int team) {
        refresh();
        return chips.get(team);
    }

    private List<Member> members(int team) {
        return chips(team).stream().map(c -> c.member).toList();
    }

    private boolean teamSelected(int team) {
        List<Chip> in = chips(team);
        return !in.isEmpty() && in.stream().allMatch(c -> selected.contains(c.member.uuid()));
    }

    private List<Land> lands() {
        List<TerritoryInfo> now = SquadClient.world.list();
        if (now != seenLand) {
            seenLand = now;
            lands = now.stream().map(Land::new).toList();
        }
        return lands;
    }

    /** Up to three carried soldiers, bottom card first. */
    private List<Chip> ghost() {
        refresh();
        if (!carried.equals(ghostFor)) {
            ghostFor = carried;
            List<Chip> all = chips.values().stream().flatMap(List::stream).filter(c -> carried.contains(c.member.uuid())).toList();
            List<Chip> shown = new ArrayList<>(inOrder(all, carried).subList(0, Math.min(3, all.size())));
            Collections.reverse(shown);
            ghost = shown;
        }
        return ghost;
    }

    private static List<Chip> inOrder(List<Chip> all, List<UUID> order) {
        return order.stream().flatMap(id -> all.stream().filter(c -> c.member.uuid().equals(id))).toList();
    }

    private boolean hintLive() {
        return !hint.isEmpty() && hintAge() < HINT_TICKS;
    }

    private long hintAge() {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime() - hintAt;
    }

    private static String line(SquadManage m) {
        if (m.hintLive()) return m.hint;
        if (m.tab == Tab.SQUAD) return "Click to select, drag to give an order or change team";
        return SquadClient.world.canEdit() ? "Edit a garrison right on its card (shift: x10)" : "Only operators edit territories";
    }

    private boolean insertLive() {
        return insertTeam >= 0 && UiHud.frame() - insertFrame <= 1;
    }

    private boolean lampsOn() {
        return recipients().stream().anyMatch(m -> (m.flags() & Member.LAMP) != 0);
    }

    // ---- scroll

    private static int bodyHeight() {
        int gui = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        return Math.min(gui - 16, 330) - 50 - HEAD - 2;
    }

    private double scroll() {
        int rows = 0;
        for (int t = 0; t < Companion.TEAMS.length; t++) rows = Math.max(rows, chips(t).size());
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows * ROW - bodyHeight())));
        return scroll;
    }

    boolean wheelSquad(float amount) {
        if (tab != Tab.SQUAD) return false;
        scroll -= amount * ROW * 2;
        scroll();
        return true;
    }

    // ---- state changes

    void tab(Tab t) {
        tab = t;
        scroll = 0;
    }

    void patrol(boolean on) {
        patrol = on;
    }

    void clearSelection() {
        if (tab == Tab.SQUAD) selected.clear();
    }

    void selectAll() {
        SquadClient.members.forEach(m -> selected.add(m.uuid()));
    }

    void prune() {
        Set<UUID> alive = new LinkedHashSet<>();
        SquadClient.members.forEach(m -> alive.add(m.uuid()));
        selected.retainAll(alive);
    }

    private void say(String text) {
        hint = text;
        var level = Minecraft.getInstance().level;
        hintAt = level == null ? 0 : level.getGameTime();
    }

    // ---- what the player can ask for

    private List<Member> roster() {
        return SquadClient.members;
    }

    private List<UUID> ordered(Set<UUID> ids) {
        return roster().stream().map(Member::uuid).filter(ids::contains).toList();
    }

    /** The selection, else everyone. */
    private List<UUID> recipientIds() {
        return selected.isEmpty() ? roster().stream().map(Member::uuid).toList() : ordered(selected);
    }

    private List<Member> recipients() {
        return selected.isEmpty() ? roster() : roster().stream().filter(m -> selected.contains(m.uuid())).toList();
    }

    private void order(List<UUID> who, String kind, String region) {
        List<UUID> ids = who.isEmpty() ? roster().stream().map(Member::uuid).toList() : who;
        if (ids.isEmpty()) return;
        SquadClient.manage("order", ids, kind, region, "", 0, 0);
    }

    /** Click on an order or territory tile: the selection (everyone if none) takes it. */
    void tile(String kind, String region, String label) {
        order(ordered(selected), kind, region);
        say((selected.isEmpty() ? "Everyone: " : plural(selected.size(), "soldier") + ": ") + label);
    }

    void territoryTile(Land l) {
        tile(patrol ? "patrol" : "guard", l.info.id(), (patrol ? "Patrol " : "Guard ") + l.info.name());
    }

    void lamp() {
        SquadClient.manage("lamp", recipientIds(), "", "", "", lampsOn() ? 0 : 1, 0);
    }

    void gear() {
        GearScreen.open(recipientIds());
    }

    void newTerritory() {
        SquadClient.manage("territory_new", List.of(), "", "", "", 32, 0);
    }

    void deleteTerritory(Land l) {
        SquadClient.manage("territory_delete", List.of(), "", l.info.id(), "", 0, 0);
    }

    private void edit(TerritoryInfo t, int size, String mode, int delay, String units) {
        SquadClient.manage("garrison", List.of(), size <= 0 ? "" : mode, t.id(), units, size, delay);
    }

    void step(Land l, int by) {
        TerritoryInfo t = l.info;
        int n = Screen.hasShiftDown() ? 10 : 1;
        edit(t, Math.max(0, Math.max(t.garrison(), 0) + by * n), t.mode().isEmpty() ? "patrol" : t.mode(), t.garrison() < 0 ? 60 : t.respawn(), t.units());
    }

    void delay(Land l, int by) {
        TerritoryInfo t = l.info;
        if (t.garrison() >= 0) edit(t, t.garrison(), t.mode(), Math.max(0, t.respawn() + by * (Screen.hasShiftDown() ? 6 : 1)), t.units());
    }

    void mode(Land l, String wanted) {
        TerritoryInfo t = l.info;
        if (t.garrison() >= 0 && !t.mode().equals(wanted)) edit(t, t.garrison(), wanted, t.respawn(), t.units());
    }

    /** Every saved variant mixed, then each one alone. */
    void variant(Land l) {
        TerritoryInfo t = l.info;
        List<String> variants = SquadClient.world.variants();
        if (t.garrison() < 0 || variants.isEmpty()) return;
        int at = t.units().isEmpty() ? -1 : variants.indexOf(t.units());
        edit(t, t.garrison(), t.mode(), t.respawn(), at + 1 >= variants.size() ? "" : variants.get(at + 1));
    }

    // ---- drag and drop

    private List<UUID> carry() {
        carried = ordered(selected);
        return carried.isEmpty() ? null : carried;
    }

    /** Drag of a soldier: the selection (or just them). */
    Object beginChip(Chip c) {
        if (!selected.contains(c.member.uuid())) {
            selected.clear();
            selected.add(c.member.uuid());
        }
        return carry();
    }

    void clickChip(Chip c) {
        UUID id = c.member.uuid();
        if (Screen.hasShiftDown() || Screen.hasControlDown()) {
            if (!selected.remove(id)) selected.add(id);
        } else {
            boolean only = selected.size() == 1 && selected.contains(id);
            selected.clear();
            if (!only) selected.add(id);
        }
    }

    /** Drag of a team's name: the whole team. */
    Object beginTeam(Team t) {
        selected.clear();
        members(t.index).forEach(m -> selected.add(m.uuid()));
        return carry();
    }

    void clickTeam(Team t) {
        if (!Screen.hasShiftDown() && !Screen.hasControlDown()) selected.clear();
        members(t.index).forEach(m -> selected.add(m.uuid()));
    }

    void endCarry() {
        carried = List.of();
    }

    /** Where in a team a drop at column-local height {@code y} lands: before the first soldier, not one carried, whose middle is below it. */
    private int dropIndex(int team, double y) {
        int idx = 0, i = 0;
        for (Chip c : chips(team)) {
            double mid = HEAD + 2 + i++ * ROW - scroll() + ROW / 2.0;
            if (carried.contains(c.member.uuid())) continue;
            if (y < mid) return idx;
            idx++;
        }
        return idx;
    }

    void hoverColumn(Team t, float y) {
        insertTeam = t.index;
        insertIdx = dropIndex(t.index, y);
        insertFrame = UiHud.frame();
    }

    void dropColumn(Team t, List<UUID> ids, float y) {
        if (y <= 0) return;
        SquadClient.manage("move", ids, "", "", "", t.index, dropIndex(t.index, y));
        say(plural(ids.size(), "soldier") + " to " + Companion.TEAMS[t.index]);
    }

    void dropOrder(List<UUID> ids, String kind, String label) {
        order(ids, kind, "");
        say(plural(ids.size(), "soldier") + ": " + label);
    }

    void dropTerritory(List<UUID> ids, Land l) {
        order(ids, patrol ? "patrol" : "guard", l.info.id());
        say(plural(ids.size(), "soldier") + ": " + (patrol ? "Patrol " : "Guard ") + l.info.name());
    }

    void dropDischarge(List<UUID> ids) {
        SquadClient.manage("dismiss", ids, "", "", "", 0, 0);
        selected.removeAll(ids);
        say("Discharged " + ids.size() + "; they hold where they stand");
    }
}
