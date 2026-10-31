package com.wf.wflib.pda.music.client;

import com.wf.wflib.pda.api.client.PdaApp;
import com.wf.wflib.pda.api.client.PdaApps;
import com.wf.wflib.pda.api.client.PdaShell;
import com.wf.wflib.pda.music.MusicChunkPacket;
import com.wf.wflib.pda.music.MusicListPacket;
import com.wf.wflib.ui.api.Signals;
import com.wf.wflib.ui.api.Subject;
import com.wf.wflib.ui.api.WidgetDef;
import com.wf.wflib.ui.api.input.TextLine;
import com.wf.wflib.ui.api.input.UiAction;
import com.wf.wflib.ui.api.widget.Button;
import com.wf.wflib.ui.api.widget.Image;
import com.wf.wflib.ui.api.widget.Point;
import com.wf.wflib.ui.api.widget.TextField;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import com.wf.wflib.core.api.WFLib;

import static com.wf.wflib.ui.api.Ui.at;
import static com.wf.wflib.ui.api.Ui.clip;
import static com.wf.wflib.ui.api.Ui.list;
import static com.wf.wflib.ui.api.Ui.rect;
import static com.wf.wflib.ui.api.Ui.slider;
import static com.wf.wflib.ui.api.Ui.stack;
import static com.wf.wflib.ui.api.Ui.text;
import static com.wf.wflib.ui.api.Ui.when;

/**
 * Music player: SERVER / LOCAL libraries and playlists on the left, now playing on the right. Playback is local to
 * this client ({@link MusicPlayer}); files are validated by {@link com.wf.wflib.pda.music.OggAudio}.
 */
public final class MusicApp implements PdaApp {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "music");
    private static final MusicApp INSTANCE = new MusicApp();
    static final Subject<MusicApp> APP = Subject.register("pda_music", MusicApp.class, () -> INSTANCE);
    static final Subject<Row> ROW = Subject.register("pda_music_row", Row.class);

    private static final int TAB_SERVER = 0;
    private static final int TAB_LOCAL = 1;
    private static final int TAB_LISTS = 2;
    private static final String TEXT = PdaShell.TEXT;
    private static final String DIM = PdaShell.DIM;
    private static final String ACCENT = PdaShell.ACCENT;
    private static final String BUTTON = PdaShell.PANEL;
    private static final String HOVER = PdaShell.HOVER;
    private static final String PRESS = PdaShell.PRESS;
    private static final String EDGE = PdaShell.EDGE;
    private static final String BAR = PdaShell.BAR_BACK;

    /** One list line; strings fixed at build so drawing allocates nothing. */
    static final class Row {
        @Nullable
        final MusicTrack track;
        @Nullable
        final MusicPlaylists.Playlist list;
        final int index;
        final String title;
        final String sub;
        final String time;

        Row(@Nullable MusicTrack track, @Nullable MusicPlaylists.Playlist list, int index, String title, String sub,
            String time) {
            this.track = track;
            this.list = list;
            this.index = index;
            this.title = title;
            this.sub = sub;
            this.time = time;
        }
    }

    private static int tab = TAB_LOCAL;
    @Nullable
    private static MusicPlaylists.Playlist opened;
    @Nullable
    private static MusicPlaylists.Playlist target;
    private static boolean armedDelete;
    private static final TextLine NAME = new TextLine(MusicPlaylists.MAX_NAME, MusicApp::create);

    private static final List<Row> ROWS = new ArrayList<>();
    private static List<MusicTrack> shown = List.of();
    private static long builtKey = Long.MIN_VALUE;

    private static MusicTrack shownTrack;
    private static String infoText = "";
    private static String albumText = "";
    private static String artistText = "";
    private static String titleText = "";
    private static long timeSecond = -1;
    private static String timeText = "0:00";
    private static String totalText = "0:00";
    private static float volumeShown = -1;
    private static String volumeText = "";

    private MusicApp() {
    }

    public static void register() {
        Signals.num(APP, "tab", a -> tab);
        Signals.bool(APP, "in_list", a -> opened != null);
        Signals.bool(APP, "lists_closed", a -> tab == TAB_LISTS && opened == null);
        Signals.bool(APP, "lists_open", a -> tab == TAB_LISTS && opened != null);
        Signals.bool(APP, "browsing", a -> tab != TAB_LISTS);
        Signals.bool(APP, "has_rows", a -> !rows().isEmpty());
        Signals.items(APP, "rows", a -> rows(), ROW);
        Signals.text(APP, "hint1", 40, a -> hint(true));
        Signals.text(APP, "hint2", 40, a -> hint(false));
        Signals.text(APP, "target", 48, a -> target == null ? "OPEN A LIST TO USE +"
                : "+ ADDS TO: " + target.name);
        Signals.text(APP, "list_name", 32, a -> opened == null ? "" : opened.name);
        Signals.text(APP, "delete", 8, a -> armedDelete ? "SURE?" : "DELETE");

        Signals.bool(APP, "has", a -> MusicPlayer.current() != null);
        Signals.bool(APP, "playing", a -> MusicPlayer.state() == MusicPlayer.State.PLAYING);
        Signals.bool(APP, "cover", a -> CoverArt.present() && MusicPlayer.probe() != null);
        Signals.bool(APP, "shuffle", a -> MusicPlaylists.shuffle());
        Signals.text(APP, "repeat", 8, a -> "REP " + MusicPlaylists.repeat());
        Signals.text(APP, "title", 48, a -> nowPlaying() == null ? "NOT PLAYING" : titleText);
        Signals.text(APP, "artist", 48, a -> artistText);
        Signals.text(APP, "album", 48, a -> albumText);
        Signals.text(APP, "info", 40, a -> infoText);
        Signals.text(APP, "status", 48, a -> status());
        Signals.bool(APP, "failed", a -> !MusicPlayer.error().isEmpty());
        Signals.text(APP, "time", 12, a -> {
            clock();
            return timeText;
        });
        Signals.text(APP, "total", 12, a -> totalText);
        Signals.num(APP, "progress", a -> MusicPlayer.progress());
        Signals.num(APP, "volume", a -> MusicPlayer.volume());
        Signals.text(APP, "volume_text", 8, a -> {
            float v = MusicPlayer.volume();
            if (v != volumeShown) {
                volumeShown = v;
                volumeText = Math.round(v * 100) + "%";
            }
            return volumeText;
        });
        Signals.text(APP, "queue", 24, a -> MusicPlayer.queueSize() == 0 || MusicPlayer.current() == null ? ""
                : "TRACK " + MusicPlayer.queuePosition() + " OF " + MusicPlayer.queueSize());

        Signals.text(ROW, "title", 48, r -> r.title);
        Signals.text(ROW, "sub", 48, r -> r.sub);
        Signals.text(ROW, "time", 8, r -> r.time);
        Signals.bool(ROW, "playing", r -> r.track != null && MusicPlayer.current() != null
                && MusicPlayer.current().same(r.track));
        Signals.bool(ROW, "missing", r -> r.track != null && r.track.missing());
        Signals.bool(ROW, "can_act", r -> r.track != null && !r.track.missing()
                && (opened != null && tab == TAB_LISTS || tab != TAB_LISTS && target != null));
        Signals.text(ROW, "act", 2, r -> opened != null && tab == TAB_LISTS ? "x" : "+");
        PdaApps.register(INSTANCE, 940);
    }

    // ---- network entry points ---------------------------------------------------------------------------

    public static void accept(MusicListPacket packet) {
        MusicLibrary.acceptServer(packet);
    }

    public static void accept(MusicChunkPacket packet) {
        MusicNet.accept(packet);
    }

    // ---- state ------------------------------------------------------------------------------------------

    @Nullable
    private static MusicTrack nowPlaying() {
        MusicTrack t = MusicPlayer.current();
        if (t != shownTrack || MusicPlayer.probe() != null && infoText.isEmpty()) {
            shownTrack = t;
            var probe = MusicPlayer.probe();
            titleText = t == null ? "" : t.label();
            artistText = t == null ? "" : probe != null ? probe.tags().artist() : t.artist();
            albumText = t == null ? "" : probe != null ? probe.tags().album() : t.album();
            infoText = probe == null ? "" : probe.codec() + " " + probe.rate() / 1000.0 + "K "
                    + (probe.channels() == 1 ? "MONO" : "ST");
            totalText = "0:00";
            timeSecond = -1;
        }
        return t;
    }

    private static void clock() {
        nowPlaying();
        long ms = MusicPlayer.positionMillis();
        if (ms / 1000 != timeSecond) {
            timeSecond = ms / 1000;
            timeText = format(ms);
            totalText = format(MusicPlayer.lengthMillis());
        }
    }

    private static String status() {
        if (!MusicPlayer.error().isEmpty()) {
            return MusicPlayer.error();
        }
        if (MusicPlayer.state() == MusicPlayer.State.LOADING) {
            int p = MusicPlayer.loadingPercent();
            return p < 0 ? "LOADING..." : "DOWNLOADING " + p + "%";
        }
        return MusicPlayer.state() == MusicPlayer.State.PAUSED ? "PAUSED" : "";
    }

    private static String format(long ms) {
        long s = ms / 1000;
        return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
    }

    private static String hint(boolean first) {
        return switch (tab) {
            case TAB_SERVER -> MusicLibrary.serverWaiting() ? first ? "ASKING THE SERVER..." : ""
                    : first ? "NO MUSIC ON THE SERVER" : "<WORLD>/pda_music";
            case TAB_LOCAL -> MusicLibrary.scanning() ? first ? "SCANNING..." : ""
                    : first ? "DROP .OGG / .OPUS FILES IN" : "<GAME>/pda_music";
            default -> opened != null ? first ? "EMPTY LIST" : "USE + ON TRACKS"
                    : first ? "NO PLAYLISTS" : "TYPE A NAME, PRESS NEW";
        };
    }

    private static List<Row> rows() {
        long key = ((long) MusicLibrary.version() << 40) ^ ((long) MusicPlaylists.version() << 16) ^ tab << 8
                ^ System.identityHashCode(opened);
        if (key == builtKey) {
            return ROWS;
        }
        builtKey = key;
        ROWS.clear();
        List<MusicTrack> tracks = switch (tab) {
            case TAB_SERVER -> MusicLibrary.server();
            case TAB_LOCAL -> MusicLibrary.local();
            default -> opened == null ? List.of() : MusicPlaylists.resolve(opened);
        };
        if (tab == TAB_LISTS && opened == null) {
            shown = List.of();
            for (MusicPlaylists.Playlist p : MusicPlaylists.lists()) {
                ROWS.add(new Row(null, p, ROWS.size(), p.name, p.tracks.size() + " TRACKS", ""));
            }
            return ROWS;
        }
        shown = tracks;
        for (int i = 0; i < tracks.size(); i++) {
            MusicTrack t = tracks.get(i);
            if (t.missing()) {
                ROWS.add(new Row(t, null, i, t.name(), "MISSING FILE", ""));
            } else {
                String sub = t.artist().isEmpty() ? "UNKNOWN ARTIST" : t.artist();
                ROWS.add(new Row(t, null, i, t.label(), sub, format(t.millis())));
            }
        }
        return ROWS;
    }

    private static void selectTab(int next) {
        tab = next;
        armedDelete = false;
        if (next == TAB_SERVER) {
            MusicLibrary.refreshServer();
        } else if (next == TAB_LOCAL) {
            MusicLibrary.refreshLocal();
        }
    }

    private static void refresh() {
        if (tab == TAB_SERVER) {
            MusicLibrary.refreshServer();
        } else if (tab == TAB_LOCAL) {
            MusicLibrary.refreshLocal();
        }
    }

    private static void create(String name) {
        MusicPlaylists.Playlist p = MusicPlaylists.create(name);
        NAME.set("");
        if (p != null) {
            target = p;
            opened = p;
        }
    }

    private static void rowClick(Object item) {
        Row r = (Row) item;
        if (r.list != null) {
            opened = r.list;
            target = r.list;
        } else {
            MusicPlayer.playFrom(shown, r.index);
        }
    }

    private static void rowAct(Object item) {
        Row r = (Row) item;
        if (r.track == null) {
            return;
        }
        if (tab == TAB_LISTS && opened != null) {
            MusicPlaylists.remove(opened, r.index);
        } else if (target != null) {
            MusicPlaylists.add(target, r.track);
        }
    }

    private static void deleteOpened() {
        if (opened == null) {
            return;
        }
        if (!armedDelete) {
            armedDelete = true;
            return;
        }
        MusicPlaylists.delete(opened);
        if (target == opened) {
            target = null;
        }
        opened = null;
        armedDelete = false;
    }

    // ---- PdaApp -----------------------------------------------------------------------------------------

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public Component title() {
        return Component.translatable("wflib.pda.app.music");
    }

    @Override
    public boolean visible(LocalPlayer player) {
        return com.wf.wflib.pda.api.client.PdaClient.held();
    }

    @Override
    public void opened() {
        MusicLibrary.refreshLocal();
        if (tab == TAB_SERVER) {
            MusicLibrary.refreshServer();
        }
    }

    @Override
    public WidgetDef build() {
        float h = PdaShell.APP_HEIGHT - 16;
        float leftW = 186;
        float rightX = 8 + leftW + 8;
        float rightW = PdaShell.APP_WIDTH - rightX - 8;
        float rowW = leftW - 8;
        float x0 = rightX + 6;
        float innerW = rightW - 12;

        WidgetDef row = new Button(MusicApp::rowClick, "true",
                "pda_music_row.playing ? " + PRESS + " : 0", HOVER, PRESS, "0", 0, 0, 0, 0,
                rowW, 22, Point.TOP_LEFT, stack(
                clip(rowW - 52, 22, stack(
                        at("4", "3", text("{pda_music_row.title}",
                                "pda_music_row.missing ? " + PdaShell.ALERT + " : " + TEXT)),
                        at("4", "12", text("{pda_music_row.sub}", DIM)))),
                at(Float.toString(rowW - 48), "7", text("{pda_music_row.time}", DIM)),
                when("pda_music_row.can_act", at(Float.toString(rowW - 16), "4",
                        btn("{pda_music_row.act}", 14, 14, MusicApp::rowAct)))));

        return stack(
                at("8", "8", PdaShell.panel(leftW, h)),
                at("12", "12", tabButton("SERVER", TAB_SERVER)),
                at("63", "12", tabButton("LOCAL", TAB_LOCAL)),
                at("114", "12", tabButton("LISTS", TAB_LISTS)),
                when("pda_music.tab != 2", at("168", "12", btn("R", 22, 13, i -> refresh()))),
                at("12", "30", list("pda_music.rows", 22, 0, Float.toString(rowW), "150", EDGE, row)),
                when("!pda_music.has_rows", stack(
                        at("20", "50", text("{pda_music.hint1}", DIM)),
                        at("20", "62", text("{pda_music.hint2}", DIM)))),
                when("pda_music.browsing", at("12", "190", text("{pda_music.target}", DIM))),
                when("pda_music.lists_closed", stack(
                        at("12", "187", new TextField(NAME, "NAME: ", 124, 14, 0xFF101210, 0xFF787F76, 0xFFA9D46C,
                                0xFFD2D6CC, 0xFF8D9488)),
                        at("140", "187", btn("NEW", 38, 14, i -> create(NAME.text().toString()))))),
                when("pda_music.lists_open", stack(
                        at("12", "187", btn("< BACK", 44, 14, i -> {
                            opened = null;
                            armedDelete = false;
                        })),
                        at("60", "187", btn("PLAY", 36, 14, i -> {
                            if (!shown.isEmpty()) {
                                MusicPlayer.playFrom(shown, 0);
                            }
                        })),
                        at("100", "187", btn("{pda_music.delete}", 48, 14, i -> deleteOpened())),
                        at("152", "190", clip(34, 12, text("{pda_music.list_name}", DIM))))),

                at(Float.toString(rightX), "8", PdaShell.panel(rightW, h)),
                when("pda_music.cover",
                        at(Float.toString(x0), "14", new Image(CoverArt.TEXTURE, 64, 64, 0, 0, 1, 1, "#ffffffff", "0")),
                        stack(at(Float.toString(x0), "14", rect(64, 64, BAR)),
                                at(Float.toString(x0 + 8), "42", text("NO COVER", DIM)))),
                at(Float.toString(x0 + 72), "14", clip(innerW - 72, 12, text("{pda_music.title}", TEXT))),
                at(Float.toString(x0 + 72), "27", clip(innerW - 72, 12, text("{pda_music.artist}", ACCENT))),
                at(Float.toString(x0 + 72), "40", clip(innerW - 72, 12, text("{pda_music.album}", DIM))),
                at(Float.toString(x0 + 72), "56", clip(innerW - 72, 12, text("{pda_music.info}", DIM))),
                at(Float.toString(x0), "82", clip(innerW, 12,
                        text("{pda_music.status}", "pda_music.failed ? " + PdaShell.ALERT + " : " + ACCENT))),
                at(Float.toString(x0), "96", slider(Float.toString(innerW), "8", "pda_music.progress",
                        (item, v) -> MusicPlayer.scrubTo((float) v), BAR, ACCENT, TEXT)),
                at(Float.toString(x0), "108", text("{pda_music.time}", TEXT)),
                com.wf.wflib.ui.api.Ui.place(Float.toString(x0 + innerW), "108", Point.TOP_RIGHT,
                        text("{pda_music.total}", DIM)),

                at(Float.toString(x0), "122", btn("|<", 30, 16, i -> MusicPlayer.previous())),
                at(Float.toString(x0 + 34), "122", playPause()),
                at(Float.toString(x0 + 78), "122", btn(">|", 30, 16, i -> MusicPlayer.next())),
                at(Float.toString(x0 + 112), "122", btn("STOP", 40, 16, i -> MusicPlayer.stop())),
                at(Float.toString(x0), "142", toggle("SHUFFLE", 60, "pda_music.shuffle",
                        i -> MusicPlayer.setShuffle(!MusicPlaylists.shuffle()))),
                at(Float.toString(x0 + 64), "142", btn("{pda_music.repeat}", 56, 16, i -> MusicPlayer.cycleRepeat())),

                at(Float.toString(x0), "168", text("VOL", DIM)),
                at(Float.toString(x0 + 24), "167", slider("96", "8", "pda_music.volume",
                        (item, v) -> MusicPlayer.setVolume((float) v), BAR, ACCENT, TEXT)),
                at(Float.toString(x0 + 126), "168", text("{pda_music.volume_text}", TEXT)),
                at(Float.toString(x0), "190", text("{pda_music.queue}", DIM)));
    }

    private static WidgetDef playPause() {
        UiAction action = i -> {
            if (MusicPlayer.state() == MusicPlayer.State.IDLE && !shown.isEmpty()) {
                MusicPlayer.playFrom(shown, 0);
            } else {
                MusicPlayer.toggle();
            }
        };
        return new Button(action, "true", BUTTON, HOVER, PRESS, EDGE, 1, 0, 0, 0,
                40, 16, Point.CENTER, when("pda_music.playing", text("PAUSE", TEXT),
                text("PLAY", TEXT)));
    }

    private static WidgetDef tabButton(String label, int index) {
        return new Button(i -> selectTab(index), "true", "pda_music.tab == " + index + " ? " + PRESS
                + " : " + BUTTON, HOVER, PRESS, EDGE, 1, 0, 0, 0, 47, 14,
                Point.CENTER, text(label, TEXT));
    }

    private static WidgetDef toggle(String label, float w, String on, UiAction action) {
        return new Button(action, "true", on + " ? " + PRESS + " : " + BUTTON, HOVER,
                PRESS, EDGE, 1, 0, 0, 0, w, 16, Point.CENTER, text(label, TEXT));
    }

    private static WidgetDef btn(String label, float w, float h, UiAction action) {
        return new Button(action, "true", BUTTON, HOVER, PRESS, EDGE, 1, 0, 0, 0,
                w, h, Point.CENTER, text(label, TEXT));
    }
}
