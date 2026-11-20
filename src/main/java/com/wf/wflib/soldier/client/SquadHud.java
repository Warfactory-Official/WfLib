package com.wf.wflib.soldier.client;

import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.soldier.Companion;
import com.wf.wflib.soldier.SquadPackets.Member;
import com.wf.wflib.ui.api.Signals;
import com.wf.wflib.ui.api.Ui;
import com.wf.wflib.ui.api.Subject;
import com.wf.wflib.ui.api.UiHud;
import com.wf.wflib.ui.api.UiInstance;
import com.wf.wflib.ui.api.UiLayout;
import com.wf.wflib.ui.api.WidgetDef;
import com.wf.wflib.ui.api.widget.PlayerFace;
import com.wf.wflib.ui.api.widget.Point;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import java.util.List;

import static com.wf.wflib.ui.api.Ui.at;
import static com.wf.wflib.ui.api.Ui.place;
import static com.wf.wflib.ui.api.Ui.rect;
import static com.wf.wflib.ui.api.Ui.repeat;
import static com.wf.wflib.ui.api.Ui.shadowed;
import static com.wf.wflib.ui.api.Ui.sized;
import static com.wf.wflib.ui.api.Ui.stack;
import static com.wf.wflib.ui.api.Ui.text;
import static com.wf.wflib.ui.api.Ui.when;

/**
 * The squad strip: one row per fire team, a card per soldier (face, health, ammunition, what it is doing). The selected
 * group has a red bar under its label and a red frame; a soldier in a fight has an orange frame, a downed one is struck out.
 */
final class SquadHud {
    private static final int CARD = 26, FACE = 20, GAP = 3, ROW = 34, LEFT = 6, TOP = 6, LABEL = 14;
    private static final String RED = "#FFD83A3A", ORANGE = "#FFE8963C", BLUE = "#FF4A90E0", PANEL = "#99000000", DIM = "#FF8A8A8A";
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "squad_hud");

    record Card(Member m, boolean selected) {}

    record Row(String label, boolean selected, boolean lit, int cells, int more, List<Card> cards) {}

    private static final SquadHud INSTANCE = new SquadHud();
    private static final Subject<SquadHud> HUD = Subject.register("squad_hud", SquadHud.class, () -> INSTANCE);
    private static final Subject<Row> ROWS = Subject.register("squad_row", Row.class);
    private static final Subject<Card> CARDS = Subject.register("squad_card", Card.class);

    private List<Member> builtFrom;
    private int builtGroup;
    private int builtWidth;
    private List<Row> rows = List.of();
    private UiInstance instance;

    private SquadHud() {}

    static void register() {
        Signals.items(HUD, "rows", SquadHud::rows, ROWS);
        Signals.bool(HUD, "toast_on", h -> toastAge() < 70);
        Signals.text(HUD, "toast", 120, h -> "> " + SquadClient.toast);
        Signals.num(HUD, "toast_alpha", h -> {
            long age = toastAge();
            return age < 50 ? 1 : (70 - age) / 20.0;
        });
        Signals.text(ROWS, "label", 2, Row::label);
        Signals.bool(ROWS, "selected", Row::selected);
        Signals.bool(ROWS, "lit", Row::lit);
        Signals.num(ROWS, "cells", Row::cells);
        Signals.num(ROWS, "more", Row::more);
        Signals.text(ROWS, "more_text", 6, r -> "+" + r.more());
        Signals.items(ROWS, "cards", Row::cards, CARDS);
        Signals.bool(CARDS, "downed", c -> (c.m().flags() & Member.DOWNED) != 0);
        Signals.bool(CARDS, "fighting", c -> (c.m().flags() & Member.FIGHTING) != 0);
        Signals.bool(CARDS, "resupplying", c -> (c.m().flags() & Member.RESUPPLYING) != 0);
        Signals.bool(CARDS, "selected", Card::selected);
        Signals.text(CARDS, "glyph", 2, c -> c.m().order().substring(0, 1).toUpperCase(java.util.Locale.ROOT));
        Signals.num(CARDS, "hp", c -> c.m().health());
        Signals.num(CARDS, "ammo", c -> c.m().ammo());
        Signals.bool(CARDS, "low_ammo", c -> c.m().mags() == 0 && c.m().ammo() < 30);
        Signals.text(CARDS, "mags", 6, c -> Integer.toString(c.m().mags()));
    }

    static void layer(RegisterGuiLayersEvent event) {
        UiHud.layer(event, LAYER, UiHud.Position.ABOVE_ALL, null, out -> {
            if (SquadClient.members.isEmpty() || Minecraft.getInstance().player == null) return;
            if (INSTANCE.instance == null) INSTANCE.instance = build().instantiate();
            out.add(INSTANCE.instance);
        });
    }

    private static long toastAge() {
        var level = Minecraft.getInstance().level;
        long age = level == null ? Long.MAX_VALUE : level.getGameTime() - SquadClient.toastAt;
        return age < 0 ? Long.MAX_VALUE : age;
    }

    /** A big squad does not run off the screen: the cards that fit, then how many more there are (the screen lists them all). */
    private List<Row> rows() {
        List<Member> all = SquadClient.members;
        int width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        if (all == builtFrom && SquadClient.group == builtGroup && width == builtWidth) return rows;
        builtFrom = all;
        builtGroup = SquadClient.group;
        builtWidth = width;
        int fit = Math.max(3, (int) (width * 0.6 - LEFT - LABEL) / (CARD + GAP));
        List<Row> out = new java.util.ArrayList<>();
        for (int team = 0; team < Companion.TEAMS.length; team++) {
            int t = team;
            List<Member> whole = all.stream().filter(m -> m.team() == t).toList();
            if (whole.isEmpty()) continue;
            List<Member> shown = whole.size() <= fit ? whole : whole.subList(0, fit - 1);
            int more = whole.size() - shown.size();
            boolean selected = SquadClient.group == team;
            List<Card> cards = shown.stream().map(m -> new Card(m, selected)).toList();
            out.add(new Row(Companion.TEAMS[team].substring(0, 1), selected, selected || SquadClient.group < 0,
                    cards.size() + (more > 0 ? 1 : 0), more, cards));
        }
        rows = List.copyOf(out);
        return rows;
    }

    static UiLayout build() {
        String rowW = LABEL + " + squad_row.cells * " + (CARD + GAP) + " + " + GAP;
        WidgetDef card = stack(
                at("1", "1", new PlayerFace(item -> ((Card) item).m().uuid(), FACE)),
                when("squad_card.downed", stack(
                        at("1", "1", rect(FACE, FACE, "#AA600000")),
                        place(Float.toString(1 + FACE / 2), "7", Point.TOP, shadowed("X", "#FFFFFFFF")))),
                when("squad_card.fighting", at("0", "0", outline(FACE + 2, ORANGE)),
                        when("squad_card.resupplying", at("0", "0", outline(FACE + 2, BLUE)))),
                at(Integer.toString(FACE - 6), "1", rect(8, 9, "#CC000000")),
                at(Integer.toString(FACE - 4), "1", text("{squad_card.glyph}", "squad_card.selected ? #FFFF6060 : #FFFFFFFF")),
                at("1", Integer.toString(FACE + 3), rect(FACE, 2, "#FF202020")),
                at("1", Integer.toString(FACE + 3), rect("floor(" + FACE + " * squad_card.hp / 100)", "2",
                        "squad_card.hp > 50 ? #FF58C458 : squad_card.hp > 25 ? #FFE0B040 : " + RED)),
                when("squad_card.ammo >= 0", stack(
                        at("1", Integer.toString(FACE + 6), rect(FACE, 1, "#FF202020")),
                        at("1", Integer.toString(FACE + 6), rect("floor(" + FACE + " * squad_card.ammo / 100)", "1",
                                "squad_card.low_ammo ? " + RED + " : #FFB0B0B0")),
                        place(Integer.toString(FACE + 1), Integer.toString(FACE - 8), Point.TOP_RIGHT,
                                shadowed("{squad_card.mags}", "#FFFFFFFF")))));
        WidgetDef row = stack(
                at("0", "0", rect(rowW, Integer.toString(ROW), PANEL)),
                at("0", "0", new com.wf.wflib.ui.api.widget.Rect(rowW, Integer.toString(ROW),
                        "squad_row.selected ? " + RED + " : 0", 0, 1)),
                at("4", "4", shadowed("{squad_row.label}", "squad_row.lit ? #FFFFFFFF : " + DIM)),
                when("squad_row.selected", at("2", "15", rect(LABEL - 4, 2, RED))),
                at(Integer.toString(LABEL + GAP), "3", repeat("squad_row.cards", false, GAP, card)),
                when("squad_row.more > 0", place(LABEL + GAP + CARD / 2 + " + (squad_row.cells - 1) * " + (CARD + GAP),
                        "10", Point.TOP, shadowed("{squad_row.more_text}", "#FFFFFFFF"))));
        return Ui.layout(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "squad_hud"), stack(
                at(Integer.toString(LEFT), Integer.toString(TOP), repeat("squad_hud.rows", true, 2, sized(0, ROW, row))),
                when("squad_hud.toast_on", place("ui.width / 2", "ui.height - 68", Point.TOP,
                        new com.wf.wflib.ui.api.widget.Box("alpha(#000000, squad_hud.toast_alpha * 0.6)", "0", 0, 0, 4, 3, 0, 0,
                                Point.TOP_LEFT, shadowed("{squad_hud.toast}", "alpha(#FFFFFF, squad_hud.toast_alpha)"))))))
                .absolute();
    }

    private static WidgetDef outline(int size, String colour) {
        return new com.wf.wflib.ui.api.widget.Rect(Integer.toString(size), Integer.toString(size), colour, 0, 1);
    }
}
