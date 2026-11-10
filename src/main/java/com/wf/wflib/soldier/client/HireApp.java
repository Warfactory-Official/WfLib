package com.wf.wflib.soldier.client;

import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.pda.api.client.PdaApp;
import com.wf.wflib.pda.api.client.PdaApps;
import com.wf.wflib.pda.api.client.PdaShell;
import com.wf.wflib.soldier.RecruitPackets.View;
import com.wf.wflib.ui.api.Signals;
import com.wf.wflib.ui.api.Subject;
import com.wf.wflib.ui.api.WidgetDef;
import com.wf.wflib.ui.api.widget.Button;
import com.wf.wflib.ui.api.widget.Point;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import static com.wf.wflib.ui.api.Ui.at;
import static com.wf.wflib.ui.api.Ui.clip;
import static com.wf.wflib.ui.api.Ui.list;
import static com.wf.wflib.ui.api.Ui.stack;
import static com.wf.wflib.ui.api.Ui.text;
import static com.wf.wflib.ui.api.Ui.when;

/** PDA "Reinforcements": pick a soldier, pay, and they walk to wherever you are after the delay. */
public final class HireApp implements PdaApp {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "reinforcements");
    private static final HireApp INSTANCE = new HireApp();
    private static final Subject<HireApp> APP = Subject.register("pda_hire", HireApp.class, () -> INSTANCE);
    private static final Subject<View> OFFER = Subject.register("pda_hire_offer", View.class);

    private HireApp() {}

    public static void register() {
        Signals.items(APP, "offers", a -> RecruitClient.pda().list(), OFFER);
        Signals.num(APP, "count", a -> RecruitClient.pda().list().size());
        Signals.bool(APP, "off", a -> !RecruitClient.pda().on());
        Signals.text(APP, "status", 56, a -> !RecruitClient.pda().note().isEmpty() ? RecruitClient.pda().note()
                : RecruitClient.pda().arriving() > 0 ? RecruitClient.pda().arriving() + " ON THE WAY" : "TAP A SOLDIER TO CALL THEM IN");
        Signals.text(OFFER, "label", 22, View::title);
        Signals.text(OFFER, "cost", 18, View::cost);
        Signals.text(OFFER, "info", 30, v -> v.role() + (v.delay() > 0 ? "  " + v.delay() + "S" : ""));
        Signals.bool(OFFER, "ok", View::affordable);
        PdaApps.register(INSTANCE, 860);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.literal("Reinforcements");
    }

    @Override
    public void opened() {
        RecruitClient.request("pda");
    }

    @Override
    public WidgetDef build() {
        float w = PdaShell.APP_WIDTH - 16;
        float h = PdaShell.APP_HEIGHT - 16;
        float rowW = w - 12;
        WidgetDef row = new Button(i -> RecruitClient.hire("pda", ((View) i).id()), "pda_hire_offer.ok", PdaShell.PANEL, PdaShell.HOVER,
                PdaShell.PRESS, PdaShell.EDGE, 1, 0, 0, 0, rowW, 24, Point.TOP_LEFT,
                stack(at("4", "3", clip(rowW - 70, 12, text("{pda_hire_offer.label}", PdaShell.TEXT))),
                        at("4", "13", clip(rowW - 70, 12, text("{pda_hire_offer.info}", PdaShell.DIM))),
                        at(Float.toString(rowW - 64), "8", clip(60, 12, text("{pda_hire_offer.cost}",
                                "pda_hire_offer.ok ? " + PdaShell.ACCENT + " : " + PdaShell.ALERT)))));
        return stack(
                at("8", "8", PdaShell.panel(w, h)),
                at("12", "12", text("REINFORCEMENTS", PdaShell.DIM)),
                at("12", "26", list("pda_hire.offers", 26, 0, Float.toString(rowW), Float.toString(h - 44), PdaShell.EDGE, row)),
                when("pda_hire.off", at("14", "34", text("RECRUITING BY PDA IS OFF", PdaShell.ALERT)),
                        when("pda_hire.count == 0", at("14", "34", text("NOBODY ON OFFER", PdaShell.DIM)))),
                at("12", Float.toString(h - 10), clip(w - 8, 12, text("{pda_hire.status}", PdaShell.DIM))));
    }
}
