package com.wf.wflib.contract.client;

import static com.wf.wflib.ui.api.Ui.at;
import static com.wf.wflib.ui.api.Ui.list;
import static com.wf.wflib.ui.api.Ui.stack;
import static com.wf.wflib.ui.api.Ui.text;
import static com.wf.wflib.ui.api.Ui.when;

import com.wf.wflib.contract.pda.Snapshot;
import com.wf.wflib.pda.api.client.PdaApp;
import com.wf.wflib.pda.api.client.PdaShell;
import com.wf.wflib.ui.api.Signals;
import com.wf.wflib.ui.api.Subject;
import com.wf.wflib.ui.api.WidgetDef;
import com.wf.wflib.ui.api.widget.Button;
import com.wf.wflib.ui.api.widget.Point;
import com.wf.wflib.ui.api.widget.Sized;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The PDA contracts terminal: four tabs (offers, profile, heat, news) over the latest {@link Snapshot}. Everything is
 * read from the snapshot; the buttons only send {@link PmcClient#act}.
 */
final class PmcApp implements PdaApp {
    static final PmcApp INSTANCE = new PmcApp();
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(com.wf.wflib.core.api.WFLib.MODID, "contracts");
    private static final long CONFIRM_MS = 3000;
    private static final String[] TABS = {"OFFERS", "PROFILE", "HEAT", "NEWS"};

    private static final Subject<PmcApp> APP = Subject.register("pmc", PmcApp.class, () -> INSTANCE);
    private static final Subject<Snapshot.Offer> OFFER = Subject.register("pmc_offer", Snapshot.Offer.class);
    private static final Subject<Snapshot.Heat> HEAT = Subject.register("pmc_heat", Snapshot.Heat.class);
    private static final Subject<String> LINE = Subject.register("pmc_line", String.class);

    private static int tab;
    @Nullable
    private static String selectedOffer, selectedHeat;
    private static long armed;

    private PmcApp() {
    }

    private static Snapshot s() {
        return PmcClient.state();
    }

    @Nullable
    private static Snapshot.Offer offer() {
        if (selectedOffer == null) return null;
        for (Snapshot.Offer o : s().offers()) if (o.id().equals(selectedOffer)) return o;
        return null;
    }

    @Nullable
    private static Snapshot.Heat heat() {
        if (selectedHeat == null) return null;
        for (Snapshot.Heat h : s().heat()) if (h.faction().equals(selectedHeat)) return h;
        return null;
    }

    static void register() {
        Signals.num(APP, "tab", a -> tab);
        Signals.bool(APP, "active", a -> s().active() != null);
        Signals.bool(APP, "has_offer", a -> offer() != null);
        Signals.bool(APP, "can_accept", a -> offer() != null && s().active() == null);
        Signals.bool(APP, "has_heat", a -> heat() != null);
        Signals.bool(APP, "has_drives", a -> s().drives() > 0);
        Signals.bool(APP, "no_offers", a -> s().offers().isEmpty());
        Signals.bool(APP, "no_heat", a -> s().heat().isEmpty());
        Signals.bool(APP, "notice", a -> !notice.isEmpty() && System.currentTimeMillis() - noticeAt < NOTICE_MS);
        Signals.text(APP, "notice_text", 160, a -> notice);
        Signals.text(APP, "abandon", 12, a -> System.currentTimeMillis() - armed < CONFIRM_MS ? "CONFIRM" : "ABANDON");
        Signals.text(APP, "rank", 40, a -> s().rank().toUpperCase(Locale.ROOT) + "   " + s().rep() + " REP");
        Signals.text(APP, "next", 60, a -> s().nextAt() == 0 ? "TOP RANK" : "NEXT " + s().nextRank().toUpperCase(Locale.ROOT) + " AT " + s().nextAt());
        Signals.text(APP, "record", 80, a -> s().completed() + " DONE   " + s().failed() + " FAILED   " + s().kills() + " KILLS");
        Signals.text(APP, "tier", 60, a -> "THREAT " + s().tier().toUpperCase(Locale.ROOT));
        Signals.text(APP, "earned", 60, a -> "EARNED " + s().earnings());
        Signals.text(APP, "drives", 40, a -> s().drives() + " INTEL DRIVE" + (s().drives() == 1 ? "" : "S"));
        Signals.text(APP, "war", 120, a -> s().war().isEmpty() ? "MARKETS QUIET" : "MARKETS: " + s().war());
        Signals.text(APP, "black_site", 120, a -> s().blackSite().isEmpty() ? "NO BLACK SITE LIVE" : s().blackSite());
        Signals.text(APP, "title", 60, a -> offer() == null ? "" : offer().title());
        Signals.text(APP, "desc", PdaShell.TEXT_MAX, a -> offer() == null ? "" : offer().description());
        Signals.text(APP, "pay", 60, a -> offer() == null ? "" : "PAYS " + offer().money() + "   +" + offer().rep() + " REP");
        Signals.text(APP, "terms", 80, a -> offer() == null ? "" : offer().minutes() + " MIN   " + offer().minDist() + "-" + offer().maxDist() + " BLOCKS");
        Signals.text(APP, "buy_in", 60, a -> offer() == null || offer().buyIn().isEmpty() ? "" : "BUY-IN " + offer().buyIn() + " (NO REFUND)");
        Signals.text(APP, "goals", 200, a -> offer() == null ? "" : String.join("; ", offer().objectives()).toUpperCase(Locale.ROOT));
        Signals.text(APP, "act_title", 60, a -> s().active() == null ? "" : s().active().title());
        Signals.text(APP, "act_where", 60, a -> s().active() == null ? "" : "AT " + s().active().x() + " " + s().active().z());
        Signals.text(APP, "act_goals", 200, a -> s().active() == null ? "" : String.join("; ", s().active().objectives()).toUpperCase(Locale.ROOT));
        Signals.text(APP, "act_time", 40, a -> clock());
        Signals.text(APP, "bribe", 60, a -> heat() == null ? "" : "BRIBE -" + heat().points() + "  " + heat().bribe());
        Signals.items(APP, "offers", a -> s().offers(), OFFER);
        Signals.items(APP, "heat", a -> s().heat(), HEAT);
        Signals.items(APP, "news", a -> s().news(), LINE);
        Signals.text(OFFER, "label", PdaShell.TEXT_MAX, o -> (o.intel().isEmpty() ? "" : "INTEL ") + o.title());
        Signals.text(OFFER, "money", 20, Snapshot.Offer::money);
        Signals.bool(OFFER, "selected", o -> o.id().equals(selectedOffer));
        Signals.bool(OFFER, "intel", o -> !o.intel().isEmpty());
        Signals.text(HEAT, "label", 60, h -> h.faction().toUpperCase(Locale.ROOT) + "  " + h.value() + "  " + h.state());
        Signals.text(HEAT, "bribe", 20, Snapshot.Heat::bribe);
        Signals.bool(HEAT, "selected", h -> h.faction().equals(selectedHeat));
        Signals.bool(HEAT, "closed", h -> h.state().equals("CLOSED"));
        Signals.text(LINE, "text", PdaShell.TEXT_MAX, l -> l);
    }

    /** Seconds left, counted down locally between snapshots. */
    private static String clock() {
        Snapshot.Active a = s().active();
        if (a == null) return "";
        int left = Math.max(0, a.secondsLeft() - (int) ((System.currentTimeMillis() - received) / 1000));
        return left / 60 + ":" + String.format("%02d", left % 60) + " LEFT";
    }

    private static long received;
    /** The server's answer to the last button, shown for a few seconds (later refreshes carry none). */
    private static final long NOTICE_MS = 8000;
    private static String notice = "";
    private static long noticeAt;
    private static boolean open;

    /** True while the app is on screen: the client asks for fresh state every couple of seconds. */
    static boolean open() {
        return open;
    }

    static void changed(Snapshot snap) {
        received = System.currentTimeMillis();
        if (!snap.notice().isEmpty()) {
            notice = snap.notice();
            noticeAt = received;
        }
        armed = 0;
        if (offer() == null) selectedOffer = snap.offers().isEmpty() ? null : snap.offers().get(0).id();
        if (heat() == null) selectedHeat = snap.heat().isEmpty() ? null : snap.heat().get(0).faction();
    }

    /** Debug: the same as clicking a tab. */
    static void showTab(int to) {
        tab(to);
    }

    private static void tab(int to) {
        tab = to;
        PmcClient.request();
    }

    private static void selectOffer(Object row) {
        selectedOffer = ((Snapshot.Offer) row).id();
    }

    private static void selectHeat(Object row) {
        selectedHeat = ((Snapshot.Heat) row).faction();
    }

    private static void accept(Object ignored) {
        if (offer() != null) PmcClient.act("accept", offer().id());
    }

    private static void abandon(Object ignored) {
        long now = System.currentTimeMillis();
        if (now - armed < CONFIRM_MS) {
            PmcClient.act("abandon", "");
            armed = 0;
        } else {
            armed = now;
        }
    }

    private static void bribe(Object ignored) {
        if (heat() != null) PmcClient.act("bribe", heat().faction());
    }

    private static void analyze(Object ignored) {
        PmcClient.act("analyze", "");
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.literal("Contracts");
    }

    @Override
    public void opened() {
        open = true;
        PmcClient.request();
    }

    @Override
    public void closed() {
        open = false;
    }

    private static WidgetDef button(String label, String enabled, float minWidth, java.util.function.Consumer<Object> action) {
        return new Button(action::accept, enabled, PdaShell.PANEL, PdaShell.HOVER, PdaShell.PRESS, PdaShell.EDGE, 1, 0, 8, 4,
                minWidth, 0, Point.CENTER, text(label, PdaShell.TEXT));
    }

    private static WidgetDef tabButton(int index) {
        return new Button(o -> tab(index), "true", "pmc.tab == " + index + " ? " + PdaShell.PRESS + " : " + PdaShell.PANEL,
                PdaShell.HOVER, PdaShell.PRESS, PdaShell.EDGE, 1, 0, 4, 4, 46, 0, Point.CENTER, text(TABS[index], PdaShell.TEXT));
    }

    private static WidgetDef row(String subject, float w, java.util.function.Consumer<Object> select, String label, String extra) {
        return new Button(select::accept, "true", subject + ".selected ? " + PdaShell.PRESS + " : 0", PdaShell.HOVER, PdaShell.PRESS, "0",
                0, 0, 0, 0, w, 18, Point.TOP_LEFT, new Sized(Float.toString(w), "18", Point.TOP_LEFT, false, stack(
                        at("5", "5", PdaShell.fit(label, subject + ".selected ? " + PdaShell.ACCENT + " : " + PdaShell.TEXT, w - 56)),
                        at(Float.toString(w - 50), "5", PdaShell.fit(extra, PdaShell.DIM, 46)))));
    }

    @Override
    public WidgetDef build() {
        float h = PdaShell.APP_HEIGHT - 16;
        float top = 30, ph = h - 22;
        float listW = 170, detailX = 186, detailW = PdaShell.APP_WIDTH - detailX - 8;
        float rowW = listW - 8;
        WidgetDef offers = stack(
                at("8", Float.toString(top), PdaShell.panel(listW, ph)),
                at("12", Float.toString(top + 4), list("pmc.offers", 18, 1, Float.toString(rowW), Float.toString(ph - 8), PdaShell.EDGE,
                        row("pmc_offer", rowW, PmcApp::selectOffer, "{pmc_offer.label}", "{pmc_offer.money}"))),
                when("pmc.no_offers", at("14", Float.toString(top + 8), PdaShell.wrap("NOTHING ON THE BOARD FOR YOU", PdaShell.DIM, listW - 12, 2))),
                at(Float.toString(detailX), Float.toString(top), PdaShell.panel(detailW, ph)),
                when("pmc.active", stack(
                        at(Float.toString(detailX + 6), Float.toString(top + 6), PdaShell.fit("ACTIVE", PdaShell.ALERT, detailW - 12)),
                        at(Float.toString(detailX + 6), Float.toString(top + 20), PdaShell.fit("{pmc.act_title}", PdaShell.ACCENT, detailW - 12)),
                        at(Float.toString(detailX + 6), Float.toString(top + 34), PdaShell.fit("{pmc.act_where}   {pmc.act_time}", PdaShell.TEXT, detailW - 12)),
                        at(Float.toString(detailX + 6), Float.toString(top + 52), PdaShell.wrap("{pmc.act_goals}", PdaShell.DIM, detailW - 12, 4)),
                        at(Float.toString(detailX + 6), Float.toString(top + ph - 24), button("{pmc.abandon}", "true", 70, PmcApp::abandon))),
                        stack(
                                when("pmc.has_offer", stack(
                                        at(Float.toString(detailX + 6), Float.toString(top + 6), PdaShell.fit("{pmc.title}", PdaShell.ACCENT, detailW - 12)),
                                        at(Float.toString(detailX + 6), Float.toString(top + 20), PdaShell.wrap("{pmc.desc}", PdaShell.DIM, detailW - 12, 3)),
                                        at(Float.toString(detailX + 6), Float.toString(top + 56), PdaShell.fit("{pmc.pay}", PdaShell.TEXT, detailW - 12)),
                                        at(Float.toString(detailX + 6), Float.toString(top + 68), PdaShell.fit("{pmc.terms}", PdaShell.TEXT, detailW - 12)),
                                        at(Float.toString(detailX + 6), Float.toString(top + 80), PdaShell.fit("{pmc.buy_in}", PdaShell.ALERT, detailW - 12)),
                                        at(Float.toString(detailX + 6), Float.toString(top + 94), PdaShell.wrap("{pmc.goals}", PdaShell.DIM, detailW - 12, 3)),
                                        at(Float.toString(detailX + 6), Float.toString(top + ph - 24), button("ACCEPT", "pmc.can_accept", 70, PmcApp::accept)))),
                                when("pmc.has_offer && !pmc.can_accept", at(Float.toString(detailX + 84), Float.toString(top + ph - 20),
                                        PdaShell.fit("FINISH YOUR CONTRACT FIRST", PdaShell.DIM, detailW - 90))))));
        WidgetDef profile = stack(
                at("8", Float.toString(top), PdaShell.panel(PdaShell.APP_WIDTH - 16, ph)),
                at("16", Float.toString(top + 8), PdaShell.fit("{pmc.rank}", PdaShell.ACCENT, 300)),
                at("16", Float.toString(top + 22), PdaShell.fit("{pmc.next}", PdaShell.DIM, 300)),
                at("16", Float.toString(top + 42), PdaShell.fit("{pmc.record}", PdaShell.TEXT, 300)),
                at("16", Float.toString(top + 56), PdaShell.fit("{pmc.earned}", PdaShell.TEXT, 300)),
                at("16", Float.toString(top + 70), PdaShell.fit("{pmc.tier}", PdaShell.ALERT, 300)),
                at("16", Float.toString(top + 90), PdaShell.fit("{pmc.drives}", PdaShell.TEXT, 200)),
                at("16", Float.toString(top + 104), button("ANALYZE", "pmc.has_drives", 70, PmcApp::analyze)));
        float heatW = PdaShell.APP_WIDTH - 16 - 8;
        WidgetDef heat = stack(
                at("8", Float.toString(top), PdaShell.panel(PdaShell.APP_WIDTH - 16, ph)),
                at("12", Float.toString(top + 4), list("pmc.heat", 18, 1, Float.toString(heatW), Float.toString(ph - 36), PdaShell.EDGE,
                        row("pmc_heat", heatW, PmcApp::selectHeat, "{pmc_heat.label}", "{pmc_heat.bribe}"))),
                when("pmc.no_heat", at("16", Float.toString(top + 8), PdaShell.wrap("NOBODY IS LOOKING FOR YOU", PdaShell.DIM, 300, 2))),
                at("14", Float.toString(top + ph - 26), button("{pmc.bribe}", "pmc.has_heat", 110, PmcApp::bribe)));
        WidgetDef news = stack(
                at("8", Float.toString(top), PdaShell.panel(PdaShell.APP_WIDTH - 16, ph)),
                at("16", Float.toString(top + 8), PdaShell.fit("{pmc.war}", PdaShell.ACCENT, 340)),
                at("16", Float.toString(top + 20), PdaShell.fit("{pmc.black_site}", PdaShell.TEXT, 340)),
                at("12", Float.toString(top + 38), list("pmc.news", 12, 1, Float.toString(heatW), Float.toString(ph - 44), PdaShell.EDGE,
                        PdaShell.fit("{pmc_line.text}", PdaShell.DIM, heatW - 8))));
        return stack(
                at("8", "8", tabButton(0)), at("58", "8", tabButton(1)), at("108", "8", tabButton(2)), at("158", "8", tabButton(3)),
                when("pmc.notice", at("212", "3", PdaShell.wrap("{pmc.notice_text}", PdaShell.ALERT, PdaShell.APP_WIDTH - 220, 3))),
                when("pmc.tab == 0", offers), when("pmc.tab == 1", profile), when("pmc.tab == 2", heat), when("pmc.tab == 3", news));
    }
}
