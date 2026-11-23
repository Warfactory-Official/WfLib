package com.wf.wflib.worldstats.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.ui.api.Signals;
import com.wf.wflib.ui.api.Subject;
import com.wf.wflib.ui.api.UiHud;
import com.wf.wflib.ui.api.UiInstance;
import com.wf.wflib.ui.api.UiLayouts;
import com.wf.wflib.ui.api.WidgetDef;
import com.wf.wflib.ui.api.widget.Box;
import com.wf.wflib.ui.api.widget.CodedText;
import com.wf.wflib.ui.api.widget.Point;
import com.wf.wflib.ui.api.widget.Transform;
import com.wf.wflib.worldstats.StatsConfig;
import com.wf.wflib.worldstats.WeatherPacket;
import com.wf.wflib.worldstats.api.WorldStats;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.wf.wflib.ui.api.Ui.layout;
import static com.wf.wflib.ui.api.Ui.place;
import static com.wf.wflib.ui.api.Ui.repeat;
import static com.wf.wflib.ui.api.Ui.stack;
import static com.wf.wflib.ui.api.Ui.when;

/** The overlay: templates from {@link StatsConfig}, tags computed from the client's level each frame. */
public final class StatsHud {
    private static final KeyMapping TOGGLE = new KeyMapping("key.wflib.worldstats", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, "key.categories.wflib");
    private static final Pattern TAG = Pattern.compile("\\{([a-z_]+)}");
    private static final String[] MOON = {"Full Moon", "Waning Gibbous", "Last Quarter", "Waning Crescent", "New Moon", "Waxing Crescent", "First Quarter", "Waxing Gibbous"};
    private static final Pattern ENTITY_STATS = Pattern.compile("E: (\\d+)/(\\d+)");

    private static final ResourceLocation LAYOUT = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "worldstats");
    private static final List<String> CORNERS = List.of("top_left", "top_right", "bottom_left", "bottom_right");
    private static final int LINE_MAX = 256;
    private static final StatsHud INSTANCE = new StatsHud();
    private static final Subject<StatsHud> STATS = Subject.register("worldstats", StatsHud.class, StatsHud::visible);
    private static final Subject<String> LINE = Subject.register("worldstats_line", String.class);
    private static final List<String> lines = new ArrayList<>();
    private static long linesFrame = -1;
    private static UiInstance instance;

    private static volatile boolean shown = true;
    private static WeatherPacket weather = new WeatherPacket(0, 0, 0, false, false);
    private static long weatherAt;

    private StatsHud() {
    }

    public static void weather(WeatherPacket p) {
        weather = p;
        weatherAt = System.nanoTime();
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(RegisterKeyMappingsEvent.class, e -> e.register(TOGGLE));
        modBus.addListener(RegisterGuiLayersEvent.class, e -> UiHud.layer(e, LAYOUT, UiHud.Position.ABOVE_ALL, null, StatsHud::collect));
        modBus.addListener(FMLClientSetupEvent.class, e -> e.enqueueWork(StatsHud::registerUi));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> {
            while (TOGGLE.consumeClick()) {
                long window = Minecraft.getInstance().getWindow().getWindow();
                boolean shift = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
                boolean ctrl = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
                if (shift || ctrl) {
                    setScale(StatsConfig.SCALE.get() * (shift ? 1.15 : 1 / 1.15));        // shift+key bigger, ctrl+key smaller
                } else {
                    shown = !shown;
                }
            }
        });
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RegisterClientCommandsEvent.class, StatsHud::commands);
    }

    private static void setScale(double v) {
        StatsConfig.SCALE.set(Math.max(0.4, Math.min(4.0, Math.round(v * 100) / 100.0)));
        StatsConfig.SPEC.save();
    }

    /** {@code /worldstats scale|pos|anchor|background|toggle|reset}: the same settings as the config file, live. */
    private static void commands(net.neoforged.neoforge.client.event.RegisterClientCommandsEvent e) {
        var d = e.getDispatcher();
        d.register(net.minecraft.commands.Commands.literal("worldstats")
                .then(net.minecraft.commands.Commands.literal("toggle").executes(x -> {
                    shown = !shown;
                    return 1;
                }))
                .then(net.minecraft.commands.Commands.literal("scale").then(net.minecraft.commands.Commands.argument("value", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.4, 4.0)).executes(x -> {
                    setScale(com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(x, "value"));
                    return 1;
                })))
                .then(net.minecraft.commands.Commands.literal("pos")
                        .then(net.minecraft.commands.Commands.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 4000))
                                .then(net.minecraft.commands.Commands.argument("y", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 4000)).executes(x -> {
                                    StatsConfig.X.set(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(x, "x"));
                                    StatsConfig.Y.set(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(x, "y"));
                                    StatsConfig.SPEC.save();
                                    return 1;
                                }))))
                .then(net.minecraft.commands.Commands.literal("anchor")
                        .then(net.minecraft.commands.Commands.argument("corner", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((x, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(List.of("top_left", "top_right", "bottom_left", "bottom_right"), b))
                                .executes(x -> {
                                    String corner = com.mojang.brigadier.arguments.StringArgumentType.getString(x, "corner");
                                    if (!List.of("top_left", "top_right", "bottom_left", "bottom_right").contains(corner)) {
                                        return 0;
                                    }
                                    StatsConfig.ANCHOR.set(corner);
                                    StatsConfig.SPEC.save();
                                    return 1;
                                })))
                .then(net.minecraft.commands.Commands.literal("background")
                        .then(net.minecraft.commands.Commands.argument("alpha", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 255)).executes(x -> {
                            StatsConfig.BACKGROUND.set(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(x, "alpha"));
                            StatsConfig.SPEC.save();
                            return 1;
                        })))
                .then(net.minecraft.commands.Commands.literal("reset").executes(x -> {
                    StatsConfig.SCALE.set(0.75);
                    StatsConfig.X.set(2);
                    StatsConfig.Y.set(2);
                    StatsConfig.ANCHOR.set("top_left");
                    StatsConfig.BACKGROUND.set(96);
                    StatsConfig.SPEC.save();
                    shown = true;
                    return 1;
                })));
    }

    private static StatsHud visible() {
        Minecraft mc = Minecraft.getInstance();
        boolean hidden = !shown || !StatsConfig.SHOWN.get() || mc.level == null || mc.player == null || mc.getDebugOverlay().showDebugScreen();
        return hidden ? null : INSTANCE;
    }

    /** Computed once per frame: the layout reads the list and its count several times. */
    private static List<String> lines() {
        long frame = UiHud.frame();
        if (frame != linesFrame) {
            linesFrame = frame;
            lines.clear();
            Minecraft mc = Minecraft.getInstance();
            for (String template : StatsConfig.LINES.get()) {
                String line = fill(template, mc);
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
            for (var section : WorldStats.sections().values()) {
                List<String> extra = section.get();
                if (extra != null) lines.addAll(extra);
            }
        }
        return lines;
    }

    private static void registerUi() {
        Signals.num(STATS, "scale", s -> StatsConfig.SCALE.get());
        Signals.num(STATS, "x", s -> StatsConfig.X.get());
        Signals.num(STATS, "y", s -> StatsConfig.Y.get());
        Signals.num(STATS, "alpha", s -> StatsConfig.BACKGROUND.get());
        Signals.num(STATS, "count", s -> lines().size());
        Signals.choice(STATS, "corner", CORNERS, s -> CORNERS.indexOf(StatsConfig.ANCHOR.get()));
        Signals.items(STATS, "lines", s -> lines(), LINE);
        Signals.text(LINE, "text", LINE_MAX, l -> l);
        WidgetDef box = new Box("rgba(0, 0, 0, worldstats.alpha)", "0", 0, 0, 2, 2, 0, 0, Point.TOP_LEFT,
                repeat("worldstats.lines", true, 2, new CodedText("{worldstats_line.text}", "#FFFFFFFF", true, "1")));
        WidgetDef[] corners = new WidgetDef[CORNERS.size()];
        for (int i = 0; i < corners.length; i++) {
            String name = CORNERS.get(i);
            Point pivot = Point.valueOf(name.toUpperCase(Locale.ROOT));
            String x = name.endsWith("right") ? "ui.width - worldstats.x" : "worldstats.x";
            String y = name.startsWith("bottom") ? "ui.height - worldstats.y" : "worldstats.y";
            corners[i] = when("worldstats.corner == worldstats.corner." + name,
                    place(x, y, pivot, new Transform("0", "0", "0", "worldstats.scale", pivot, box)));
        }
        UiLayouts.register(layout(LAYOUT, when("worldstats.present && worldstats.count > 0", stack(corners))).absolute());
    }

    private static void collect(UiHud.Collector out) {
        if (visible() == null) {
            return;
        }
        if (instance == null || !instance.current()) {
            instance = UiLayouts.get(LAYOUT).instantiate();
        }
        out.add(instance);
    }

    private static String fill(String template, Minecraft mc) {
        Matcher m = TAG.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = tag(m.group(1), mc);
            if (value == null) {
                return "";                      // a tag nothing provides (a mod that is not installed): the line is left out
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String tag(String name, Minecraft mc) {
        var level = mc.level;
        var player = mc.player;
        BlockPos pos = player.blockPosition();
        switch (name) {
            case "fps":
                return Integer.toString(mc.getFps());
            case "day":
                return Long.toString(level.getDayTime() / 24000L + 1);
            case "time": {
                long t = level.getDayTime() % 24000L;
                int minutes = (int) ((t + 6000) % 24000 * 1440 / 24000);       // tick 0 is 06:00
                int h = minutes / 60 % 12 == 0 ? 12 : minutes / 60 % 12;
                return String.format(Locale.ROOT, "%02d:%02d %s (%s)", h, minutes % 60, minutes / 60 % 24 < 12 ? "AM" : "PM", t < 12000 ? "day" : t < 13500 ? "dusk" : t < 22500 ? "night" : "dawn");
            }
            case "dimension": {
                String id = level.dimension().location().toString();
                String num = level.dimension() == net.minecraft.world.level.Level.OVERWORLD ? "0" : level.dimension() == net.minecraft.world.level.Level.NETHER ? "-1"
                        : level.dimension() == net.minecraft.world.level.Level.END ? "1" : "?";
                String nice = id.substring(id.indexOf(':') + 1).replace('_', ' ');
                return Character.toUpperCase(nice.charAt(0)) + nice.substring(1) + " (" + num + ")";
            }
            case "chunks":
                return Integer.toString(level.getChunkSource().getLoadedChunksCount());
            case "biome": {
                var holder = level.getBiome(pos);
                return holder.unwrapKey().map(k -> {
                    String p = k.location().getPath().replace('_', ' ');
                    StringBuilder b = new StringBuilder();
                    for (String w : p.split(" ")) {
                        b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
                    }
                    return b.toString().trim();
                }).orElse("?");
            }
            case "temp":
                return temperature(level.getBiome(pos).value(), pos, level, StatsConfig.UNITS.get());
            case "humidity":
                return Integer.toString(Math.round(Mth.clamp(downfall(level.getBiome(pos).value()), 0, 1) * 100));
            case "entities": {
                Matcher m = ENTITY_STATS.matcher(mc.levelRenderer.getEntityStatistics());
                String rendered = m.find() ? m.group(1) : "?";
                return rendered + " Rendered / " + level.getEntityCount() + " Loaded";
            }
            case "cx":
                return Integer.toString(pos.getX() >> 4);
            case "cz":
                return Integer.toString(pos.getZ() >> 4);
            case "ox":
                return Integer.toString(pos.getX() & 15);
            case "oz":
                return Integer.toString(pos.getZ() & 15);
            case "y":
                return Integer.toString(pos.getY());
            case "facing": {
                var d = player.getDirection();
                return Character.toUpperCase(d.getName().charAt(0)) + d.getName().substring(1);
            }
            case "light":
                return level.getBrightness(LightLayer.BLOCK, pos) + " block, " + level.getBrightness(LightLayer.SKY, pos) + " sky";
            case "moon":
                return MOON[level.getMoonPhase()];
            case "storm":
                return storm(level);
            default:
                var custom = WorldStats.tags().get(name);
                return custom == null ? null : custom.get();
        }
    }

    private static float downfall(Biome biome) {
        return biome.getModifiedClimateSettings().downfall();
    }

    /** Vanilla biome temperature (0.8 temperate, below 0 frozen, 2 desert) read as a rough outdoor temperature, lowered with height and at night. */
    private static String temperature(Biome biome, BlockPos pos, net.minecraft.world.level.Level level, String units) {
        float base = biome.getModifiedClimateSettings().temperature();
        double c = base <= 0.8 ? 31.5 * base - 3.25 : 21.95 + (base - 0.8) * 15;
        c -= Math.max(0, pos.getY() - 63) * 0.06;
        if (!level.isDay()) {
            c -= 4;
        }
        if (level.isRaining()) {
            c -= 2;
        }
        StringBuilder out = new StringBuilder();
        for (char u : units.toCharArray()) {
            if (out.length() > 0) {
                out.append(" ");
            }
            switch (Character.toUpperCase(u)) {
                case 'F' -> out.append(Math.round(c * 9 / 5 + 32)).append("°F");
                case 'K' -> out.append(Math.round(c + 273.15)).append("K");
                default -> out.append(Math.round(c)).append("°C");
            }
        }
        return out.toString();
    }

    private static String storm(net.minecraft.world.level.Level level) {
        if (!level.dimensionType().hasSkyLight()) {
            return "";
        }
        long since = (System.nanoTime() - weatherAt) / 50_000_000L;            // ticks since the packet
        if (weather.raining() || weather.thundering()) {
            long left = Math.max(0, Math.max(weather.raining() ? weather.rain() : 0, weather.thundering() ? weather.thunder() : 0) - since);
            return (weather.thundering() ? "Thunderstorm" : "Rain") + " clears in " + clock(left);
        }
        long until = Math.max(0, (weather.clear() > 0 ? weather.clear() : weather.rain()) - since);
        return clock(until) + " until the next storm";
    }

    private static String clock(long ticks) {
        long s = ticks / 20;
        return s >= 3600 ? (s / 3600) + "h " + (s / 60 % 60) + "m" : s >= 60 ? (s / 60) + "m" : s + "s";
    }
}
