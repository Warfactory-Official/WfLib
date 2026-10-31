package com.wf.wflib.pda.api.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.wf.wflib.pda.music.BackgroundImage;
import com.wf.wflib.pda.music.BackgroundListPacket;
import com.wf.wflib.pda.music.MusicListRequestPacket;
import com.wf.wflib.pda.music.client.MusicNet;
import com.wf.wflib.pda.api.client.PdaThemeState.Token;
import com.wf.wflib.ui.api.Signals;
import com.wf.wflib.ui.api.input.TextLine;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import com.wf.wflib.pda.client.PdaBackground;

/**
 * PDA-wide look: accent / text / panel colours ({@code #RGB} / {@code #RRGGBB}, typed live), panel opacity and a
 * server background image. Everything else derives from the three colours. Persisted in
 * {@code config/wflib_pda.json}. The three colours make the gamma CUSTOM theme ({@link #custom()}); typing one selects
 * it. Signals {@code pda.th_*} and the accessors follow the active theme; opacity applies to every theme.
 */
public final class PdaTheme {
    static final float MIN_OPACITY = 0.05F;
    public static final String CUSTOM_ID = "custom";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long SAVE_DELAY_MS = 700;

    /** {@code RRGGBB} strings; {@code background} = server file name, "" = none. */
    private static final class Data {
        String accent = "A9D46C";
        String text = "D2D6CC";
        String panel = "454A45";
        float opacity = 1;
        String background = "";
    }

    /** One background list line; {@code name} "" = none. */
    public record Choice(String name, String label) {
    }

    public static final TextLine ACCENT = new TextLine(9, s -> flush());
    public static final TextLine TEXT = new TextLine(9, s -> flush());
    public static final TextLine PANEL = new TextLine(9, s -> flush());

    private static Data data;
    private static int accent;
    private static int text;
    private static int panel;
    private static String shownAccent = "";
    private static String shownText = "";
    private static String shownPanel = "";
    private static com.wf.wflib.ui.api.theme.PdaTheme custom;
    private static long dirtyAt;

    private static List<Choice> choices = List.of(new Choice("", "NONE"));
    private static boolean listing;
    private static String status = "";
    private static boolean loading;
    private static int requested;
    private static boolean restored;

    private PdaTheme() {
    }

    public static void register() {
        var pda = PdaShell.PDA;
        Signals.num(pda, "th_accent", s -> argb(accent()));
        Signals.num(pda, "th_text", s -> argb(text()));
        Signals.num(pda, "th_dim", s -> argb(dim()));
        Signals.num(pda, "th_panel", s -> argb(faded(Token.PANEL.argb())));
        Signals.num(pda, "th_back", s -> argb(Token.BACK.argb()));
        Signals.num(pda, "th_bar", s -> argb(faded(Token.BAR.argb())));
        Signals.num(pda, "th_edge", s -> argb(Token.EDGE.argb()));
        Signals.num(pda, "th_hover", s -> argb(Token.HOVER.argb()));
        Signals.num(pda, "th_press", s -> argb(Token.PRESS.argb()));
        Signals.num(pda, "th_button", s -> argb(Token.PANEL.argb()));
        Signals.bool(pda, "has_bg", s -> PdaBackground.present());
    }

    // ---- colours ----------------------------------------------------------------------------------------

    /** {@code RGB} or {@code RRGGBB}, optional leading '#'. */
    public static boolean valid(String hex) {
        return hex != null && parse(hex) >= 0;
    }

    /** RRGGBB, -1 if invalid. */
    public static int parse(CharSequence in) {
        int from = 0;
        int to = in.length();
        while (from < to && in.charAt(from) <= ' ') {
            from++;
        }
        while (to > from && in.charAt(to - 1) <= ' ') {
            to--;
        }
        if (from < to && in.charAt(from) == '#') {
            from++;
        }
        int n = to - from;
        if (n != 3 && n != 6) {
            return -1;
        }
        int v = 0;
        for (int i = from; i < to; i++) {
            int d = Character.digit(in.charAt(i), 16);
            if (d < 0) {
                return -1;
            }
            v = n == 3 ? v << 8 | d << 4 | d : v << 4 | d;
        }
        return v;
    }

    private static int mix(int a, int b, float t) {
        int out = 0;
        for (int shift = 0; shift < 24; shift += 8) {
            float x = (a >> shift & 0xFF) + ((b >> shift & 0xFF) - (a >> shift & 0xFF)) * t;
            out |= Math.round(x) << shift;
        }
        return out;
    }

    private static double argb(int argb) {
        return argb & 0xFFFFFFFFL;
    }

    /** Theme colour with its alpha scaled by the panel opacity. */
    private static int faded(int argb) {
        return Math.round((argb >>> 24) * opacity()) << 24 | argb & 0xFFFFFF;
    }

    private static String hex(int rgb) {
        return String.format("%06X", rgb);
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("wflib_pda.json");
    }

    private static Data data() {
        if (data == null) {
            data = new Data();
            try {
                Path f = file();
                if (Files.isRegularFile(f) && Files.size(f) < (64 << 10)) {
                    Data read = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Data.class);
                    if (read != null) {
                        data.accent = valid(read.accent) ? hex(parse(read.accent)) : data.accent;
                        data.text = valid(read.text) ? hex(parse(read.text)) : data.text;
                        data.panel = valid(read.panel) ? hex(parse(read.panel)) : data.panel;
                        data.opacity = Float.isNaN(read.opacity) ? 1 : Math.clamp(read.opacity, MIN_OPACITY, 1);
                        data.background = read.background != null && BackgroundImage.validName(read.background)
                                ? read.background : "";
                    }
                }
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("PDA theme unreadable, using defaults: {}", e.toString());
            }
            accent = parse(data.accent);
            text = parse(data.text);
            panel = parse(data.panel);
            ACCENT.set(data.accent);
            TEXT.set(data.text);
            PANEL.set(data.panel);
            shownAccent = data.accent;
            shownText = data.text;
            shownPanel = data.panel;
            derive();
        }
        return data;
    }

    private static void derive() {
        int o = 0xFF000000;
        int bar = o | mix(panel, 0, 0.35F);
        var c = com.wf.wflib.ui.api.theme.PdaTheme.CLASSIC;
        custom = new com.wf.wflib.ui.api.theme.PdaTheme(CUSTOM_ID, "CUSTOM", o | mix(panel, 0, 0.15F), bar,
                o | panel, o | mix(panel, text, 0.12F), o | mix(panel, text, 0.24F), o | mix(panel, text, 0.4F), 0,
                o | text, o | mix(text, panel, 0.45F), o | accent, c.alert(), bar, o | accent, 0x66000000 | accent,
                c.chamfer(), c.scanlines(), c.vignette(), c.noise(), c.flicker(), c.bezel(), c.bezelScrew(), c.hazard(),
                c.glow());
    }

    /** The gamma theme made from the typed colours; a new instance after every change. */
    public static com.wf.wflib.ui.api.theme.PdaTheme custom() {
        data();
        return custom;
    }

    /** Typed hex => live colours; debounced save. Every colour accessor polls. */
    private static void poll() {
        Data d = data();
        boolean changed = false;
        if (!ACCENT.text().toString().equals(shownAccent)) {
            shownAccent = ACCENT.text().toString();
            int v = parse(shownAccent);
            if (v >= 0) {
                accent = v;
                d.accent = hex(v);
                changed = true;
            }
        }
        if (!TEXT.text().toString().equals(shownText)) {
            shownText = TEXT.text().toString();
            int v = parse(shownText);
            if (v >= 0) {
                text = v;
                d.text = hex(v);
                changed = true;
            }
        }
        if (!PANEL.text().toString().equals(shownPanel)) {
            shownPanel = PANEL.text().toString();
            int v = parse(shownPanel);
            if (v >= 0) {
                panel = v;
                d.panel = hex(v);
                changed = true;
            }
        }
        if (changed) {
            derive();
            dirtyAt = Util.getMillis();
            if (!CUSTOM_ID.equals(PdaThemeState.theme().id())) {
                PdaThemeState.select(custom);
            }
        }
        if (dirtyAt != 0 && Util.getMillis() - dirtyAt > SAVE_DELAY_MS) {
            flush();
        }
    }

    private static void flush() {
        dirtyAt = 0;
        String json = GSON.toJson(data());
        Util.ioPool().execute(() -> {
            try {
                Path f = file();
                Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
                Files.writeString(tmp, json, StandardCharsets.UTF_8);
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                LOGGER.warn("PDA theme not saved: {}", e.toString());
            }
        });
    }

    /** ARGB for canvas drawing, of the active theme. */
    public static int accent() {
        poll();
        return Token.ACCENT.argb();
    }

    public static int text() {
        return Token.TEXT.argb();
    }

    public static int dim() {
        return Token.DIM.argb();
    }

    public static boolean badAccent() {
        return parse(ACCENT.text()) < 0;
    }

    public static boolean badText() {
        return parse(TEXT.text()) < 0;
    }

    public static boolean badPanel() {
        return parse(PANEL.text()) < 0;
    }

    public static float opacity() {
        return data().opacity;
    }

    public static void setOpacity(float v) {
        data().opacity = Math.clamp(v, MIN_OPACITY, 1);
        dirtyAt = Util.getMillis();
    }

    public static void reset() {
        Data d = new Data();
        ACCENT.set(d.accent);
        TEXT.set(d.text);
        PANEL.set(d.panel);
        data().opacity = d.opacity;
        poll();
        PdaThemeState.select(com.wf.wflib.ui.api.theme.PdaTheme.CLASSIC);
        select("");
    }

    // ---- background -------------------------------------------------------------------------------------

    static boolean hasBackground() {
        return PdaBackground.present();
    }

    public static String background() {
        return data().background;
    }

    public static List<Choice> choices() {
        return choices;
    }

    public static boolean listing() {
        return listing;
    }

    public static String status() {
        return status;
    }

    public static void refresh() {
        listing = true;
        PacketDistributor.sendToServer(MusicListRequestPacket.BACKGROUNDS);
    }

    public static void accept(BackgroundListPacket packet) {
        List<Choice> out = new ArrayList<>(packet.names().size() + 1);
        out.add(new Choice("", "NONE"));
        for (String name : packet.names()) {
            if (BackgroundImage.validName(name)) {
                out.add(new Choice(name, name.substring(0, name.lastIndexOf('.'))));
            }
        }
        choices = List.copyOf(out);
        listing = false;
        String saved = background();
        if (!saved.isEmpty() && !PdaBackground.present() && !loading) {
            if (choices.stream().anyMatch(c -> c.name().equals(saved))) {
                load(saved);
            } else {
                status = "BACKGROUND GONE FROM SERVER";
            }
        }
    }

    /** Once per world join: ask for the saved background. */
    public static void restore() {
        poll();
        if (Minecraft.getInstance().level == null) {
            restored = false;
        } else if (!restored && !background().isEmpty()) {
            restored = true;
            refresh();
        }
    }

    public static void select(String name) {
        data().background = name;
        flush();
        if (name.isEmpty()) {
            MusicNet.BACKGROUND.cancel();
            loading = false;
            requested++;
            status = "";
            PdaBackground.show(null);
        } else {
            load(name);
        }
    }

    private static void load(String name) {
        int mine = ++requested;
        loading = true;
        status = "LOADING...";
        MusicNet.BACKGROUND.fetch(name).whenComplete((bytes, error) -> {
            if (error instanceof CancellationException) {
                return;
            }
            if (error != null || bytes == null) {
                Minecraft.getInstance().execute(() -> {
                    if (mine == requested) {
                        loading = false;
                        status = "SERVER REFUSED THE IMAGE";
                    }
                });
                return;
            }
            Util.backgroundExecutor().execute(() -> {
                NativeImage image = PdaBackground.prepare(bytes);
                Minecraft.getInstance().execute(() -> {
                    if (mine != requested) {
                        if (image != null) {
                            image.close();
                        }
                        return;
                    }
                    loading = false;
                    if (image == null) {
                        status = "NOT A VALID IMAGE";
                        return;
                    }
                    status = "";
                    PdaBackground.show(image);
                });
            });
        });
    }
}
