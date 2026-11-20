package com.wf.wflib.soldier.client;

import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.soldier.GearBook;
import com.wf.wflib.ui.api.UiLayout;
import com.wf.wflib.ui.api.WidgetDef;
import com.wf.wflib.ui.api.input.TextBox;
import com.wf.wflib.ui.api.input.UiAction;
import com.wf.wflib.ui.api.input.UiDrag;
import com.wf.wflib.ui.api.widget.Align;
import com.wf.wflib.ui.api.widget.Button;
import com.wf.wflib.ui.api.widget.DragArea;
import com.wf.wflib.ui.api.widget.EntityView;
import com.wf.wflib.ui.api.widget.Hover;
import com.wf.wflib.ui.api.widget.Item;
import com.wf.wflib.ui.api.widget.Point;
import com.wf.wflib.ui.api.widget.Rect;
import com.wf.wflib.ui.api.widget.Sized;
import com.wf.wflib.ui.api.widget.TextBoxField;
import com.wf.wflib.ui.api.widget.Text;
import com.wf.wflib.ui.api.widget.Tooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

import static com.wf.wflib.ui.api.Ui.at;
import static com.wf.wflib.ui.api.Ui.column;
import static com.wf.wflib.ui.api.Ui.list;
import static com.wf.wflib.ui.api.Ui.place;
import static com.wf.wflib.ui.api.Ui.rect;
import static com.wf.wflib.ui.api.Ui.repeat;
import static com.wf.wflib.ui.api.Ui.row;
import static com.wf.wflib.ui.api.Ui.sized;
import static com.wf.wflib.ui.api.Ui.spacer;
import static com.wf.wflib.ui.api.Ui.stack;
import static com.wf.wflib.ui.api.Ui.text;
import static com.wf.wflib.ui.api.Ui.when;

/** Gear screen layout, 660 x 300 units at most; model column only from 600 wide. */
final class GearLayout {
    static final TextBox SEARCH = TextBox.text(48);
    static final TextBox NAME = TextBox.text(32);

    private static final String BG = "#F0101418", PANEL = "#FF1A2129", HEAD = "#FF222B35", INK = "#FFE4EDF7",
            DIM = "#FF8C9BAA", ACCENT = "#FF6ED5BD", HAVE = "#FF7BD88F", NONE = "#FFE06060", ROW = "#FF202A34",
            ROW_ON = "#FF2C4A47", ROW_HOVER = "#FF28333F", MUTED = "#FF5E6B78", WARN = "#FFE0B040", EDGE = "#FF3A4654",
            BAR = "#FF55657A", RAIL_HOVER = "#FF2A3541", CLEAR = "#00000000", WORD_HOVER = "#1F6ED5BD";

    private static final String PW = "min(ui.width - 16, 660)", PH = "min(ui.height - 16, 300)";
    private static final String RIGHT_X = "(" + PW + " - 184)", MODEL_X = "(" + PW + " - 340)";
    private static final String LIST_W = "(" + PW + " >= 600 ? " + PW + " - 380 : " + PW + " - 224)";
    private static final String LIST_H = "(" + PH + " - 84)";
    private static final String BODY_H = "(" + PH + " - 54)";
    private static final float LIST_X = 34, RIGHT_W = 178, ROW_H = 20, PRESET_H = 30;

    private static final TextBoxField.Colours FIELD = new TextBoxField.Colours(0xFF0A0F14, 0xFF3A4654, 0xFF6ED5BD,
            0xFFE06060, 0xFFE4EDF7, 0xFF5E6B78, 0x805080A0);

    private static UiLayout layout;

    private GearLayout() {
    }

    static UiLayout layout() {
        if (layout == null) {
            layout = com.wf.wflib.ui.api.Ui.layout(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "gear_screen"),
                    stack(rect("ui.width", "ui.height", "#99000000"),
                            place("ui.width / 2", "ui.height / 2", Point.CENTER, new Sized(PW, PH, Point.TOP_LEFT, false, panel()))));
        }
        return layout;
    }

    private static String f(float v) {
        return Float.toString(v);
    }

    private static WidgetDef panel() {
        return stack(
                rect(PW, PH, BG),
                new Rect(PW, PH, EDGE, 0, 1),
                at("1", "1", rect(PW + " - 2", "21", HEAD)),
                at("8", "7", text("{gear.title}", INK)),
                place(PW + " - 8", "7", Point.TOP_RIGHT, text("{gear.storage}", DIM)),
                at("4", "26", repeat("gear.tabs", true, 2, tab())),
                when("!gear.presets_tab", itemList(), presetList()),
                when(PW + " >= 600", modelPanel()),
                loadout(),
                at("1", PH + " - 24", rect(PW + " - 2", "23", HEAD)),
                at("5", PH + " - 24", row(8, Align.START, word("CLOSE", i -> GearScreen.current.close()),
                        word("CLEAR", i -> GearScreen.current.clear()), word("SAVE PRESET", i -> GearScreen.current.save()))),
                place(PW + " - 5", PH + " - 24", Point.TOP_RIGHT, row(11, Align.CENTER,
                        new Text("{gear.message}", WARN, false, "1", "max(40, " + PW + " - 360)"),
                        wordTemplate("{gear.assign}", i -> GearScreen.current.assign()))));
    }

    private static WidgetDef word(String label, UiAction action) {
        return wordTemplate(label, action);
    }

    private static WidgetDef wordTemplate(String template, UiAction action) {
        return new Button(action, "true", CLEAR, WORD_HOVER, WORD_HOVER, CLEAR, 0, 0, 3, 0, 0, 24, Point.CENTER,
                text(template, INK));
    }

    private static WidgetDef tab() {
        return stack(
                new Tooltip(i -> List.of(Component.literal(((GearScreen.Tab) i).label())),
                        new Button(i -> GearScreen.current.tab(((GearScreen.Tab) i).index()), "true",
                                "gear_tab.on ? " + ROW_ON + " : " + PANEL, "gear_tab.on ? " + ROW_ON + " : " + RAIL_HOVER,
                                ROW_ON, CLEAR, 0, 0, 0, 0, 26, 26, Point.CENTER,
                                new Item(i -> ((GearScreen.Tab) i).icon(), 16, false))),
                when("gear_tab.on", rect(2, 26, ACCENT)));
    }

    private static List<Component> tip(ItemStack stack) {
        return Screen.getTooltipFromItem(Minecraft.getInstance(), stack);
    }

    private static WidgetDef search() {
        return new TextBoxField(SEARCH, "Search", LIST_W + " - 4", 14, FIELD);
    }

    private static WidgetDef itemList() {
        WidgetDef itemRow = new Tooltip(i -> tip(((GearScreen.Row) i).choice().stack()),
                new Hover(i -> GearScreen.current.hover(((GearScreen.Row) i).choice()),
                        new Button(i -> GearScreen.current.pick(((GearScreen.Row) i).choice(), true), "true",
                                "gear_row.chosen ? " + ROW_ON + " : " + ROW, "gear_row.chosen ? " + ROW_ON + " : " + ROW_HOVER,
                                ROW_HOVER, CLEAR, 0, 0, 0, 0, 0, 0, Point.TOP_LEFT,
                                new Sized(LIST_W, "19", Point.TOP_LEFT, false, stack(
                                        at("3", "2", new Item(i -> ((GearScreen.Row) i).choice().stack(), 16, false)),
                                        place("56", "6", Point.TOP_RIGHT, text("{gear_row.qty}", "gear_row.have ? " + HAVE + " : " + NONE)),
                                        at("64", "6", new Text("{gear_row.label}",
                                                "gear_row.have ? " + INK + " : " + DIM, false, "1",
                                                LIST_W + " - 64 - (gear_row.counted ? 24 : 6)")),
                                        place(LIST_W + " - 4", "6", Point.TOP_RIGHT, text("{gear_row.count}", ACCENT)))),
                                CLEAR, 0, i -> GearScreen.current.pick(((GearScreen.Row) i).choice(), false))));
        return stack(
                at(f(LIST_X), "24", search()),
                at(f(LIST_X), "42", rect(LIST_W, "14", HEAD)),
                at(f(LIST_X + 24), "46", text("Qty", DIM)),
                at(f(LIST_X + 64), "46", text("Name", DIM)),
                at(f(LIST_X), "56", rect(LIST_W, LIST_H, PANEL)),
                when("gear.rows_empty", place(LIST_X + " + " + LIST_W + " / 2", "68", Point.TOP,
                        text("Nothing installed for this slot", DIM))),
                at(f(LIST_X), "56", list("gear.rows", ROW_H, 0, LIST_W, LIST_H, BAR, itemRow)));
    }

    private static WidgetDef presetList() {
        WidgetDef presetRow = stack(
                at("0", "0", new Button(i -> GearScreen.current.load((GearBook.Preset) i), "true",
                        ROW, ROW_HOVER, ROW_HOVER, CLEAR, 0, 0, 0, 0, 0, 0, Point.TOP_LEFT,
                        new Sized(LIST_W + " - 22", "28", Point.TOP_LEFT, false, stack(
                                at("6", "5", new Text("{gear_preset.name}", INK, false, "1", LIST_W + " - 30")),
                                at("6", "16", new Text("{gear_preset.summary}", DIM, false, "1", LIST_W + " - 30")))))),
                at(LIST_W + " - 22", "0", new Button(i -> GearScreen.current.deletePreset((GearBook.Preset) i),
                        "true", ROW, ROW_HOVER, ROW_HOVER, CLEAR, 0, 0, 0, 0, 22, 28, Point.CENTER, text("x", NONE))));
        return stack(
                at(f(LIST_X), "26", rect(LIST_W, BODY_H, PANEL)),
                when("gear.presets_empty", stack(
                        place(LIST_X + " + " + LIST_W + " / 2", "40", Point.TOP, text("No saved presets", DIM)),
                        place(LIST_X + " + " + LIST_W + " / 2", "54", Point.TOP, text("Build a loadout, name it, and save it", DIM)))),
                at(f(LIST_X), "26", list("gear.presets", PRESET_H, 0, LIST_W, BODY_H, BAR, presetRow)));
    }

    private static WidgetDef modelPanel() {
        return stack(
                at(MODEL_X, "26", rect("150", BODY_H, PANEL)),
                at(MODEL_X + " + 6", "30", text("Preview", ACCENT)),
                place(MODEL_X + " + 144", "30", Point.TOP_RIGHT, text("drag to turn", MUTED)),
                at(MODEL_X, "42", new DragArea("150", PH + " - 70", new UiDrag() {
                    @Override
                    public void drag(Object item, float dx, float dy) {
                        GearScreen.current.yaw += dx * 1.6f;
                    }
                }, new EntityView(i -> GearScreen.current == null ? null : GearScreen.current.previewEntity(),
                        "150", PH + " - 70", "gear.yaw"))));
    }

    private static WidgetDef remove(UiAction action) {
        return new Button(action, "true", CLEAR, ROW_HOVER, ROW_HOVER, CLEAR, 0, 0, 0, 0, 12, 12, Point.CENTER, text("x", NONE));
    }

    private static WidgetDef slotLine() {
        WidgetDef filled = stack(
                at("44", "0", new Item(i -> ((GearScreen.Line) i).choice().stack(), 16, false)),
                at("44", "0", new Tooltip(i -> tip(((GearScreen.Line) i).choice().stack()), spacer(116, 16))),
                place("160", "2", Point.TOP_RIGHT, text("{gear_line.have}", "gear_line.ok ? " + HAVE + " : " + NONE)),
                at("62", "2", new Text("{gear_line.name}", INK, false, "1", "88")),
                at("164", "0", remove(i -> GearScreen.current.clearSlot(((GearScreen.Line) i).slot()))));
        return sized(RIGHT_W, 17, stack(
                at("6", "2", text("{gear_line.label}", DIM)),
                when("gear_line.set", filled, at("50", "2", text("unchanged", MUTED)))));
    }

    private static WidgetDef kindLine() {
        return sized(RIGHT_W, 11, stack(
                at("10", "0", new Text("{gear_kind.label}", INK, false, "1", "128")),
                place("160", "0", Point.TOP_RIGHT, text("{gear_kind.have}", "gear_kind.ok ? " + HAVE + " : " + NONE)),
                at("164", "-1", remove(i -> GearScreen.current.dropGrenades(((GearScreen.Kind) i).key())))));
    }

    private static WidgetDef stepper(String template, UiAction next, UiAction prev) {
        return new Button(next, "true", "#FF28333F", "#FF33414F", "#FF33414F", CLEAR, 0, 0, 4, 0, 122, 12, Point.LEFT,
                text(template, INK), CLEAR, 0, prev);
    }

    private static WidgetDef loadout() {
        WidgetDef column = column(0,
                repeat("gear.slots", true, 0, slotLine()),
                sized(RIGHT_W, 11, at("6", "2", text("Grenades", DIM))),
                when("gear.kinds_empty", sized(RIGHT_W, 11, at("10", "0", text("unchanged", MUTED)))),
                repeat("gear.kinds", true, 0, kindLine()),
                spacer(RIGHT_W, 4),
                sized(RIGHT_W, 16, stack(at("6", "2", text("Role", DIM)),
                        at("50", "0", stepper("{gear.role_text}", i -> GearScreen.current.stepRole(1), i -> GearScreen.current.stepRole(-1))))),
                sized(RIGHT_W, 18, stack(at("6", "2", text("Mags", DIM)),
                        at("50", "0", stepper("{gear.mags_text}", i -> GearScreen.current.stepSpares(1), i -> GearScreen.current.stepSpares(-1))))),
                when("gear.needs_any", column(0,
                        sized(RIGHT_W, 10, at("6", "0", text("Still to collect", DIM))),
                        repeat("gear.needs", true, 0, sized(RIGHT_W, 9,
                                at("8", "0", new Text("{gear_need.line}", WARN, false, "1", "166")))))));
        return stack(
                at(RIGHT_X, "26", rect(f(RIGHT_W), BODY_H, PANEL)),
                at(RIGHT_X + " + 6", "30", text("Loadout", ACCENT)),
                at(RIGHT_X, "44", new Sized(f(RIGHT_W), PH + " - 96", Point.TOP_LEFT, true, column)),
                at(RIGHT_X + " + 2", PH + " - 44", new TextBoxField(NAME, "Preset name", f(RIGHT_W - 4), 14, FIELD)));
    }
}
