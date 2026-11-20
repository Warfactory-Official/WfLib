package com.wf.wflib.soldier.client;

import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.soldier.client.SquadManage.Chip;
import com.wf.wflib.soldier.client.SquadManage.Land;
import com.wf.wflib.soldier.client.SquadManage.Team;
import com.wf.wflib.ui.api.UiLayout;
import com.wf.wflib.ui.api.UiScreen;
import com.wf.wflib.ui.api.WidgetDef;
import com.wf.wflib.ui.api.input.UiAction;
import com.wf.wflib.ui.api.input.UiDragSource;
import com.wf.wflib.ui.api.input.UiDrop;
import com.wf.wflib.ui.api.widget.Align;
import com.wf.wflib.ui.api.widget.Button;
import com.wf.wflib.ui.api.widget.DragGhost;
import com.wf.wflib.ui.api.widget.DragSource;
import com.wf.wflib.ui.api.widget.PlayerFace;
import com.wf.wflib.ui.api.widget.Point;
import com.wf.wflib.ui.api.widget.Sized;
import com.wf.wflib.ui.api.widget.Text;
import com.wf.wflib.ui.api.widget.Tile;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.UUID;

import static com.wf.wflib.ui.api.Ui.at;
import static com.wf.wflib.ui.api.Ui.list;
import static com.wf.wflib.ui.api.Ui.offset;
import static com.wf.wflib.ui.api.Ui.place;
import static com.wf.wflib.ui.api.Ui.rect;
import static com.wf.wflib.ui.api.Ui.repeat;
import static com.wf.wflib.ui.api.Ui.row;
import static com.wf.wflib.ui.api.Ui.shadowed;
import static com.wf.wflib.ui.api.Ui.stack;
import static com.wf.wflib.ui.api.Ui.text;
import static com.wf.wflib.ui.api.Ui.when;

/**
 * The squad's management screen ({@code Home}). Four fire-team columns of soldiers on the left, what they can be told to do on
 * the right. Click selects (a team's name selects the team), and drag does everything else: drop soldiers on another column to
 * change their fire team or put them in order, on an order or a territory to give it, on the discharge strip to let them go.
 * The Territories tab is the map-maker's: each territory's garrison is edited in place on its card.
 */
final class SquadScreen extends UiScreen {
    private static final String RED = "#FFD83A3A", ORANGE = "#FFE8963C", BLUE = "#FF4A90E0", GREEN = "#FF58C458",
            DIM = "#FF8A8A8A", WHITE = "#FFFFFFFF", FAINT = "#33FFFFFF", HOVER = "#66FFFFFF";

    private static final String PW = "min(ui.width - 16, 560)", PH = "min(ui.height - 16, 330)";
    private static final String CW = "(" + PW + " - 16)", CH = "(" + PH + " - 50)";
    private static final String LEFTW = "floor(" + CW + " * 0.64)", COLW = "floor((" + LEFTW + " - 18) / 4)";
    private static final String RW = "(" + CW + " - " + LEFTW + " - 10)";
    private static final int CARD = 40;

    private static UiLayout layout;
    private static SquadScreen current;

    private final SquadManage model;

    SquadScreen() {
        this(new SquadManage());
    }

    private SquadScreen(SquadManage model) {
        super(build(), model, Component.translatable("wflib.squad.manage"), new Options(false, false, null, null));
        this.model = model;
    }

    private static SquadManage cur() {
        return current.model;
    }

    @SuppressWarnings("unchecked")
    private static List<UUID> ids(Object payload) {
        return (List<UUID>) payload;
    }

    @Override
    protected void init() {
        current = this;
        SquadClient.manage("list", List.of(), "", "", "", 0, 0);
    }

    @Override
    public void removed() {
        super.removed();
        if (current == this) current = null;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (SquadClient.MANAGE.matches(key, scan)) {
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_A && hasControlDown()) {
            model.selectAll();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void tick() {
        super.tick();
        if (minecraft != null && minecraft.player != null && minecraft.player.tickCount % 60 == 0) {
            SquadClient.manage("list", List.of(), "", "", "", 0, 0);
        }
        model.prune();
    }

    // ---- layout

    static UiLayout build() {
        if (layout == null) {
            layout = new UiLayout(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "squad_manage"), root(), 0);
        }
        return layout;
    }

    private static WidgetDef root() {
        return stack(
                rect("ui.width", "ui.height", "#88000000"),
                at("floor((ui.width - " + PW + ") / 2)", "floor((ui.height - " + PH + ") / 2)", panel()),
                new DragGhost(0, 0, ghost()));
    }

    private static WidgetDef panel() {
        return stack(
                new Tile(PW, PH, "#EE101010", "#EE101010", "#EE101010", "#FF606060", "#FF606060", 1,
                        i -> cur().clearSelection(), (i, amount) -> cur().wheelSquad(amount), null, null),
                at("8", "6", row(4, tab("Squad", 44, SquadManage.Tab.SQUAD), tab("Territories", 72, SquadManage.Tab.TERRITORIES))),
                place(PW + " - 8", "11", Point.TOP_RIGHT, text("{squad_ui.count}", DIM)),
                when("squad_ui.squad_tab", squadTab(), territoriesTab()),
                at("8", PH + " - 14", text("{squad_ui.line}", "squad_ui.line_hot ? " + WHITE + " : " + DIM)));
    }

    private static WidgetDef tab(String label, float width, SquadManage.Tab tab) {
        String on = tab == SquadManage.Tab.SQUAD ? "squad_ui.squad_tab" : "!squad_ui.squad_tab";
        UiAction action = i -> cur().tab(tab);
        return stack(
                new Button(action, "true", "0", "#22FFFFFF", "#22FFFFFF", "0", 0, 0, 7, 0, width, 18, Point.CENTER,
                        text(label, on + " ? " + WHITE + " : " + DIM)),
                at("0", "16", when(on, rect(width, 2, RED))));
    }

    private static WidgetDef squadTab() {
        return stack(
                at("8", "28", repeat("squad_ui.teams", false, 6, column())),
                when("squad_ui.empty", place("8 + " + LEFTW + " / 2", "28 + " + CH + " / 2", Point.CENTER, text("No soldiers yet", DIM))),
                at("8 + " + LEFTW + " + 10", "28", new Sized(RW, CH, Point.TOP_LEFT, false, orders())));
    }

    private static final UiDragSource CHIP_SOURCE = new UiDragSource() {
        @Override
        public Object begin(Object item) {
            return cur().beginChip((Chip) item);
        }

        @Override
        public void click(Object item) {
            cur().clickChip((Chip) item);
        }

        @Override
        public void end(Object payload) {
            cur().endCarry();
        }
    };

    private static final UiDragSource TEAM_SOURCE = new UiDragSource() {
        @Override
        public Object begin(Object item) {
            return cur().beginTeam((Team) item);
        }

        @Override
        public void click(Object item) {
            cur().clickTeam((Team) item);
        }

        @Override
        public void end(Object payload) {
            cur().endCarry();
        }
    };

    private static final UiDrop COLUMN_DROP = new UiDrop() {
        @Override
        public void hover(Object item, Object payload, float x, float y) {
            cur().hoverColumn((Team) item, y);
        }

        @Override
        public void drop(Object item, Object payload, float x, float y) {
            cur().dropColumn((Team) item, ids(payload), y);
        }
    };

    private static WidgetDef column() {
        WidgetDef header = new DragSource(COLW, "14", "squad_team.all ? #AAD83A3A : #44FFFFFF",
                "squad_team.all ? #AAD83A3A : " + HOVER, "0", 0, 4, TEAM_SOURCE,
                at("4", "3", text("{squad_team.name}", WHITE)));
        WidgetDef body = at("0", "16", new Sized(COLW, CH + " - 16", Point.TOP_LEFT, true, stack(
                offset("0", "-squad_ui.scroll", repeat("squad_team.members", true, 2, chip())),
                when("squad_ui.insert_team == index", at("2", "squad_ui.insert_y", rect(COLW + " - 4", "2", RED))))));
        return new Tile(COLW, CH, "#55000000", "#55000000", "#44D83A3A", "0", "0", 0, null, null, COLUMN_DROP,
                stack(header, body));
    }

    private static WidgetDef chip() {
        String lifted = "squad_chip.lifted";
        String name = lifted + " ? (squad_chip.down ? #66FF6060 : #66FFFFFF) : (squad_chip.down ? #FFFF6060 : " + WHITE + ")";
        String order = "squad_chip.fighting ? (" + lifted + " ? #66E8963C : " + ORANGE + ") : (" + lifted + " ? #669A9A9A : #FF9A9A9A)";
        String health = "squad_chip.health";
        return new DragSource(COLW, "22", "squad_chip.sel ? #88D83A3A : #33FFFFFF", "squad_chip.sel ? #88D83A3A : #55FFFFFF",
                "squad_chip.sel ? " + RED + " : 0", 1, 4, CHIP_SOURCE, stack(
                when("squad_chip.fighting", rect(2, 22, ORANGE), when("squad_chip.resupplying", rect(2, 22, BLUE))),
                at("4", "3", new PlayerFace(o -> ((Chip) o).member.uuid(), 14)),
                when("squad_chip.down", place("11", "6", Point.TOP, text("X", "#FFFF6060"))),
                at("21", "2", new Text("{squad_chip.name}", name, false, "1", COLW + " - 24")),
                at("21", "12", new Text("{squad_chip.order}", order, false, "1", COLW + " - 24")),
                at("1", "20", rect(COLW + " - 2", "1", "#FF202020")),
                at("1", "20", rect("(" + COLW + " - 2) * " + health + " / 100", "1",
                        health + " > 50 ? " + GREEN + " : " + health + " > 25 ? #FFE0B040 : " + RED))));
    }

    private static WidgetDef ghost() {
        String shift = "(count - 1 - index) * 5";
        WidgetDef card = com.wf.wflib.ui.api.Ui.sized(1, 1, stack(
                at("4 + " + shift, "4 + " + shift, rect(20, 20, "#CC000000")),
                at("6 + " + shift, "6 + " + shift, new PlayerFace(o -> ((Chip) o).member.uuid(), 16))));
        return stack(repeat("squad_ui.ghost", false, -1, card),
                when("squad_ui.ghost_n > 1", at("26 + min(3, squad_ui.ghost_n) * 5", "8", shadowed("{squad_ui.ghost_n}", WHITE))));
    }

    private static WidgetDef orders() {
        WidgetDef follow = order("follow", "Follow me", 14);
        WidgetDef hold = order("hold", "Hold here", 34);
        WidgetDef regroup = order("regroup", "Regroup", 54);
        WidgetDef resupply = order("resupply", "Resupply", 74);
        WidgetDef lamp = at("0", "94", new Tile(RW, "18", FAINT, HOVER, FAINT, "0", "0", 0, i -> cur().lamp(), null, null,
                at("5", "5", text("{squad_ui.lamp}", WHITE))));
        WidgetDef gear = at("0", "114", new Tile(RW, "18", FAINT, HOVER, FAINT, "0", "0", 0, i -> cur().gear(), null, null,
                at("5", "5", text("Gear...", WHITE))));
        WidgetDef missions = stack(
                at("0", "141", text("Missions", DIM)),
                at(RW + " - 84", "138", segment("Patrol", "squad_ui.patrol", true)),
                at(RW + " - 42", "138", segment("Guard", "!squad_ui.patrol", false)));
        WidgetDef territories = at("0", "153", list("squad_ui.land", 18, 2, RW, "(" + CH + " - 177)", "0", territoryTile()));
        WidgetDef discharge = at("0", CH + " - 20", new Tile(RW, "20", "#33D83A3A", "#33D83A3A", "#CCD83A3A", "#FF803030", WHITE, 1,
                null, null, (item, payload, x, y) -> cur().dropDischarge(ids(payload)),
                place(RW + " / 2", "6", Point.TOP, when("squad_ui.dragging",
                        text("Drop to discharge", WHITE), text("Discharge", "#FFC08080")))));
        return stack(
                at("0", "3", text("Orders", DIM)),
                place(RW, "3", Point.TOP_RIGHT, text("{squad_ui.who}", "squad_ui.has_selection ? " + WHITE + " : " + DIM)),
                follow, hold, regroup, resupply, lamp, gear, missions, territories,
                when("squad_ui.no_land", at("4", "157", text("No territories", DIM))),
                discharge);
    }

    private static WidgetDef order(String kind, String label, int y) {
        return at("0", Integer.toString(y), new Tile(RW, "18", FAINT, HOVER, "#CC4A90E0", "0", WHITE, 1,
                i -> cur().tile(kind, "", label), null,
                (item, payload, px, py) -> cur().dropOrder(ids(payload), kind, label),
                at("5", "5", new Text(label, WHITE, false, "1", RW + " - 18"))));
    }

    private static WidgetDef segment(String label, String on, boolean patrol) {
        return new Tile("42", "12", on + " ? " + BLUE + " : " + FAINT, on + " ? " + BLUE + " : " + HOVER, "0", "0", "0", 0,
                i -> cur().patrol(patrol), null, null,
                place("21", "2", Point.TOP, text(label, on + " ? " + WHITE + " : " + DIM)));
    }

    private static WidgetDef territoryTile() {
        return new Tile(RW, "18", FAINT, HOVER, "#CC4A90E0", "0", WHITE, 1, i -> cur().territoryTile((Land) i), null,
                (item, payload, x, y) -> cur().dropTerritory(ids(payload), (Land) item),
                stack(at("5", "5", new Text("{squad_land.label}", WHITE, false, "1", RW + " - 18")),
                        when("squad_land.has_mine", place(RW + " - 4", "5", Point.TOP_RIGHT, text("{squad_land.mine_text}", GREEN)))));
    }

    private static WidgetDef territoriesTab() {
        WidgetDef card = stack(
                rect(CW, Integer.toString(CARD), "#44000000"),
                at("6", "5", row(10, text("{squad_land.name}", WHITE), text("{squad_land.where}", DIM))),
                when("squad_ui.can_edit", operatorRow(), at("6", "21", text("{squad_land.status}", DIM))));
        return stack(
                at("8", "28", list("squad_ui.land", CARD, 4, CW, CH + " - (squad_ui.can_edit ? 22 : 0)", "0", card)),
                when("squad_ui.no_land", at("14", "34", text("No territories yet", DIM))),
                when("squad_ui.can_edit", at("8", "28 + " + CH + " - 18", new Tile("170", "18", FAINT, HOVER, FAINT, "0", "0", 0,
                        i -> cur().newTerritory(), null, null, at("6", "5", text("+ New territory where you stand", WHITE))))));
    }

    private static WidgetDef operatorRow() {
        WidgetDef garrison = row(6, Align.CENTER, text("Garrison", DIM),
                stepper("{squad_land.value}", i -> cur().step((Land) i, -1), i -> cur().step((Land) i, 1)));
        WidgetDef modes = row(0, small(40, "Patrol", "squad_land.is_patrol", i -> cur().mode((Land) i, "patrol")),
                small(40, "Guard", "squad_land.is_guard", i -> cur().mode((Land) i, "guard")));
        WidgetDef reinforce = row(6, Align.CENTER, text("Reinforce", DIM),
                stepper("{squad_land.respawn}", i -> cur().delay((Land) i, -1), i -> cur().delay((Land) i, 1)));
        WidgetDef variant = new Button(i -> cur().variant((Land) i), "true", FAINT, HOVER, HOVER, "0", 0, 0, 5, 0, 0, 12,
                Point.CENTER, new Text("{squad_land.variant}", WHITE, false, "1", CW + " - 380"));
        return stack(
                at(CW + " - 16", "3", button("x", i -> cur().deleteTerritory((Land) i))),
                at("6", "21", row(10, Align.CENTER, garrison, when("squad_land.has_garrison", row(10, Align.CENTER, modes, reinforce, variant)))));
    }

    private static WidgetDef stepper(String value, UiAction minus, UiAction plus) {
        return row(4, Align.CENTER, button("-", minus), text(value, WHITE), button("+", plus));
    }

    private static WidgetDef button(String label, UiAction action) {
        return new Button(action, "true", FAINT, "#AAD83A3A", "#AAD83A3A", "0", 0, 0, 0, 0, 12, 12, Point.CENTER, text(label, WHITE));
    }

    private static WidgetDef small(float width, String label, String on, UiAction action) {
        return new Button(action, "true", on + " ? " + BLUE + " : " + FAINT, on + " ? " + BLUE + " : " + HOVER,
                on + " ? " + BLUE + " : " + HOVER, "0", 0, 0, 0, 0, width, 12, Point.CENTER,
                text(label, on + " ? " + WHITE + " : " + DIM));
    }
}
