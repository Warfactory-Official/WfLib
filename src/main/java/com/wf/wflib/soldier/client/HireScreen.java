package com.wf.wflib.soldier.client;

import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.soldier.RecruitPackets.Offers;
import com.wf.wflib.soldier.RecruitPackets.View;
import com.wf.wflib.ui.api.Signals;
import com.wf.wflib.ui.api.Ui;
import com.wf.wflib.ui.api.Subject;
import com.wf.wflib.ui.api.UiContext;
import com.wf.wflib.ui.api.UiLayout;
import com.wf.wflib.ui.api.UiScreen;
import com.wf.wflib.ui.api.WidgetDef;
import com.wf.wflib.ui.api.widget.Box;
import com.wf.wflib.ui.api.widget.Button;
import com.wf.wflib.ui.api.widget.Point;
import com.wf.wflib.ui.api.widget.Text;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import static com.wf.wflib.ui.api.Ui.at;
import static com.wf.wflib.ui.api.Ui.clip;
import static com.wf.wflib.ui.api.Ui.list;
import static com.wf.wflib.ui.api.Ui.place;
import static com.wf.wflib.ui.api.Ui.stack;
import static com.wf.wflib.ui.api.Ui.text;
import static com.wf.wflib.ui.api.Ui.when;

/** The barracks menu: what can be hired, what it costs and how long it takes. Click a row to hire. */
final class HireScreen extends UiScreen {
    private static final float W = 360, H = 240, ROW = 34, LIST_W = W - 16, LIST_H = H - 34 - 22;
    private static final Subject<Offers> BARRACKS = Subject.register("barracks", Offers.class, RecruitClient::barracks);
    private static UiLayout layout;

    private HireScreen() {
        super(build(), UiContext.EMPTY, Component.literal("Barracks"), Options.DEFAULT);
    }

    static void open() {
        net.minecraft.client.Minecraft.getInstance().setScreen(new HireScreen());
    }

    static void register() {
        Signals.items(BARRACKS, "offers", Offers::list, HireApp.OFFER);
        Signals.num(BARRACKS, "count", o -> o.list().size());
        Signals.bool(BARRACKS, "on", Offers::on);
        Signals.bool(BARRACKS, "has_note", o -> !o.note().isEmpty());
        Signals.text(BARRACKS, "header", 120, o -> o.header().toLowerCase(java.util.Locale.ROOT));
        Signals.text(BARRACKS, "footer", 120, o -> o.note().isEmpty() ? "Click a soldier to hire" : o.note());
    }

    static UiLayout build() {
        if (layout == null) {
            float rowW = LIST_W;
            WidgetDef offer = new Button(i -> RecruitClient.hire(RecruitClient.barracks().where(), ((View) i).id()), "true",
                    "pda_hire_offer.ok ? #33FFFFFF : #33802020", "pda_hire_offer.ok ? #66FFFFFF : #33802020",
                    "pda_hire_offer.ok ? #66FFFFFF : #33802020", "0", 0, 0, 0, 0, rowW, ROW - 2, Point.TOP_LEFT,
                    stack(at("6", "3", text("{pda_hire_offer.label}", "pda_hire_offer.ok ? #FFFFFFFF : #FFA06060")),
                            at("6", "13", text("{pda_hire_offer.kit}", "#FF9A9A9A")),
                            at("6", "23", text("{pda_hire_offer.gear}", "#FF7A7A7A")),
                            place(Float.toString(rowW - 6), "3", Point.TOP_RIGHT, text("{pda_hire_offer.cost}",
                                    "pda_hire_offer.ok ? #FF58C458 : #FFD83A3A")),
                            place(Float.toString(rowW - 6), "13", Point.TOP_RIGHT, text("{pda_hire_offer.upkeep}", "#FFE0B040"))));
            WidgetDef panel = new Box("#EE101010", "#FF606060", 1, 0, 0, 0, W, H, Point.TOP_LEFT, stack(
                    at("8", "8", text("Barracks", "#FFFFFFFF")),
                    at("8", "20", new Text("{barracks.header}", "#FF8A8A8A", false, "1", Float.toString(W - 16))),
                    at("8", "34", list("barracks.offers", ROW, 0, Float.toString(LIST_W), Float.toString(LIST_H), "0", offer)),
                    when("barracks.count == 0", place(Float.toString(W / 2), Float.toString(H / 2), Point.TOP,
                            when("barracks.on", text("Nobody on offer", "#FF8A8A8A"),
                                    text("Recruiting here is switched off", "#FF8A8A8A")))),
                    at("8", Float.toString(H - 14), clip(W - 16, 10, text("{barracks.footer}",
                            "barracks.has_note ? #FFFFFFFF : #FF8A8A8A")))));
            layout = Ui.layout(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "barracks"), panel).absolute().withDesignSize(W, H);
        }
        return layout;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, 0x88000000);
    }
}
