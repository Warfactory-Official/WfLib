package com.wf.wflib.soldier.client;

import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.pda.api.client.PdaApp;
import com.wf.wflib.pda.api.client.PdaApps;
import com.wf.wflib.pda.api.client.PdaShell;
import com.wf.wflib.soldier.Companion;
import com.wf.wflib.soldier.RecruitPackets.View;
import com.wf.wflib.soldier.SquadPackets.Member;
import com.wf.wflib.ui.api.Signals;
import com.wf.wflib.ui.api.Subject;
import com.wf.wflib.ui.api.WidgetDef;
import com.wf.wflib.ui.api.input.UiAction;
import com.wf.wflib.ui.api.widget.Button;
import com.wf.wflib.ui.api.widget.Point;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

import static com.wf.wflib.ui.api.Ui.at;
import static com.wf.wflib.ui.api.Ui.clip;
import static com.wf.wflib.ui.api.Ui.list;
import static com.wf.wflib.ui.api.Ui.rect;
import static com.wf.wflib.ui.api.Ui.stack;
import static com.wf.wflib.ui.api.Ui.text;
import static com.wf.wflib.ui.api.Ui.when;

/**
 * PDA "Forces": the HIRE tab lists what can be called in (role, kit, gear, price, upkeep, delay) and the FORCES tab your soldiers
 * (fire team, order, health, ammunition) with a button each to hold, follow or release them. Prices and upkeep only show when there
 * are some: they come from the server's config and from an economy mod's wallet, such as Pluginless.
 */
public final class HireApp implements PdaApp {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "reinforcements");
    private static final HireApp INSTANCE = new HireApp();
    private static final Subject<HireApp> APP = Subject.register("pda_hire", HireApp.class, () -> INSTANCE);
    private static final Subject<View> OFFER = Subject.register("pda_hire_offer", View.class);
    private static final Subject<Member> FORCE = Subject.register("pda_force", Member.class);
    private static volatile boolean forces;

    private HireApp() {}

    public static void register() {
        Signals.items(APP, "offers", a -> RecruitClient.pda().list(), OFFER);
        Signals.items(APP, "forces", a -> SquadClient.members, FORCE);
        Signals.num(APP, "count", a -> RecruitClient.pda().list().size());
        Signals.num(APP, "fcount", a -> SquadClient.members.size());
        Signals.bool(APP, "off", a -> !RecruitClient.pda().on());
        Signals.bool(APP, "forces_tab", a -> forces);
        Signals.text(APP, "header", 120, a -> RecruitClient.pda().header());
        Signals.text(APP, "status", 80, a -> !RecruitClient.pda().note().isEmpty() ? RecruitClient.pda().note()
                : forces ? "TAP HOLD / FOLLOW TO ORDER, RELEASE TO LET GO" : "TAP A SOLDIER TO CALL THEM IN");
        Signals.text(OFFER, "label", 44, v -> v.title() + "  " + v.role());
        Signals.text(OFFER, "kit", 70, View::kit);
        Signals.text(OFFER, "gear", 90, v -> v.gear() + (v.delay() > 0 ? "  -  ARRIVES IN " + v.delay() + "S" : ""));
        Signals.text(OFFER, "upkeep", 30, v -> v.upkeep().isEmpty() ? "" : "UPKEEP " + v.upkeep().toUpperCase());
        Signals.text(OFFER, "cost", 20, v -> v.cost().isEmpty() ? "FREE" : v.cost());
        Signals.bool(OFFER, "ok", View::affordable);
        Signals.text(FORCE, "title", 40, m -> m.name() + "  " + Companion.TEAMS[Math.floorMod(m.team(), Companion.TEAMS.length)] + "  " + m.role());
        Signals.text(FORCE, "info", 80, m -> m.order() + "  -  HP " + m.health() + "%" + (m.ammo() >= 0 ? "  -  AMMO " + m.ammo() + "% X" + m.mags() : "")
                + ((m.flags() & Member.DOWNED) != 0 ? "  -  DOWN" : (m.flags() & Member.FIGHTING) != 0 ? "  -  FIGHTING" : ""));
        PdaApps.register(INSTANCE, 860);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.literal("Forces");
    }

    @Override
    public void opened() {
        RecruitClient.request("pda");
    }

    /** Opens on the FORCES tab or the HIRE tab. */
    public static void tab(boolean showForces) {
        forces = showForces;
    }

    private static void order(Object item, String kind) {
        SquadClient.manage("order", List.of(((Member) item).uuid()), kind, "", "", 0, 0);
    }

    private static WidgetDef mini(String label, float w, UiAction action) {
        return new Button(action, "true", PdaShell.PANEL, PdaShell.HOVER, PdaShell.PRESS, PdaShell.EDGE, 1, 0, 0, 0, w, 12, Point.CENTER, text(label, PdaShell.TEXT));
    }

    private static WidgetDef tab(String label, float w, boolean forcesTab) {
        return new Button(i -> {
            forces = forcesTab;
            if (!forcesTab) RecruitClient.request("pda");
        }, "true", "pda_hire.forces_tab ? " + (forcesTab ? PdaShell.PRESS : "0") + " : " + (forcesTab ? "0" : PdaShell.PRESS),
                PdaShell.HOVER, PdaShell.PRESS, PdaShell.EDGE, 1, 0, 0, 0, w, 14, Point.CENTER, text(label, PdaShell.TEXT));
    }

    @Override
    public WidgetDef build() {
        float w = PdaShell.APP_WIDTH - 16;
        float h = PdaShell.APP_HEIGHT - 16;
        float rowW = w - 12;
        float listH = h - 32 - 18;
        WidgetDef offer = new Button(i -> RecruitClient.hire("pda", ((View) i).id()), "pda_hire_offer.ok", PdaShell.PANEL, PdaShell.HOVER,
                PdaShell.PRESS, PdaShell.EDGE, 1, 0, 0, 0, rowW, 38, Point.TOP_LEFT,
                stack(at("4", "3", clip(rowW - 160, 12, text("{pda_hire_offer.label}", PdaShell.TEXT))),
                        at("4", "14", clip(rowW - 160, 12, text("{pda_hire_offer.kit}", PdaShell.DIM))),
                        at("4", "25", clip(rowW - 8, 12, text("{pda_hire_offer.gear}", PdaShell.DIM))),
                        at(Float.toString(rowW - 150), "3", clip(146, 12, text("{pda_hire_offer.cost}",
                                "pda_hire_offer.ok ? " + PdaShell.ACCENT + " : " + PdaShell.ALERT))),
                        at(Float.toString(rowW - 150), "14", clip(146, 12, text("{pda_hire_offer.upkeep}", PdaShell.DIM)))));
        WidgetDef force = stack(
                at("0", "0", rect(rowW, 28, PdaShell.PANEL)),
                at("4", "3", clip(rowW - 150, 12, text("{pda_force.title}", PdaShell.TEXT))),
                at("4", "15", clip(rowW - 150, 12, text("{pda_force.info}", PdaShell.DIM))),
                at(Float.toString(rowW - 142), "8", mini("HOLD", 34, i -> order(i, "hold"))),
                at(Float.toString(rowW - 104), "8", mini("FOLLOW", 44, i -> order(i, "follow"))),
                at(Float.toString(rowW - 56), "8", mini("RELEASE", 52, i -> SquadClient.manage("dismiss", List.of(((Member) i).uuid()), "", "", "", 0, 0))));
        return stack(
                at("8", "8", PdaShell.panel(w, h)),
                at("12", "12", tab("HIRE", 44, false)),
                at("60", "12", tab("FORCES", 52, true)),
                at("118", "15", clip(w - 116, 12, text("{pda_hire.header}", PdaShell.DIM))),
                when("pda_hire.forces_tab",
                        stack(at("12", "32", list("pda_hire.forces", 30, 0, Float.toString(rowW), Float.toString(listH), PdaShell.EDGE, force)),
                                when("pda_hire.fcount == 0", at("14", "40", text("NO SOLDIERS YET", PdaShell.DIM)))),
                        stack(at("12", "32", list("pda_hire.offers", 40, 0, Float.toString(rowW), Float.toString(listH), PdaShell.EDGE, offer)),
                                when("pda_hire.off", at("14", "40", text("RECRUITING BY PDA IS OFF", PdaShell.ALERT)),
                                        when("pda_hire.count == 0", at("14", "40", text("NOBODY ON OFFER", PdaShell.DIM)))))),
                at("12", Float.toString(h - 10), clip(w - 8, 12, text("{pda_hire.status}", PdaShell.DIM))));
    }
}
