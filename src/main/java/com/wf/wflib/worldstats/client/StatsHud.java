package com.wf.wflib.worldstats.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.worldstats.StatsConfig;
import com.wf.wflib.worldstats.WeatherPacket;
import com.wf.wflib.worldstats.api.WorldStats;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.IEventBus;
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

/** The overlay: templates from {@link StatsConfig}, tags computed from the client's level each frame. */
public final class StatsHud {
    private static final KeyMapping TOGGLE = new KeyMapping("key.wflib.worldstats", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F6, "key.categories.wflib");
    private static final Pattern TAG = Pattern.compile("\\{([a-z_]+)}");
    private static final String[] MOON = {"Full Moon", "Waning Gibbous", "Last Quarter", "Waning Crescent", "New Moon", "Waxing Crescent", "First Quarter", "Waxing Gibbous"};
    private static final Pattern ENTITY_STATS = Pattern.compile("E: (\\d+)/(\\d+)");

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
        modBus.addListener(RegisterGuiLayersEvent.class, e -> e.registerAboveAll(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "worldstats"), StatsHud::render));
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

    private static void render(GuiGraphics g, net.minecraft.client.DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!shown || !StatsConfig.SHOWN.get() || mc.level == null || mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) {
            return;
        }
        List<String> lines = new ArrayList<>();
        for (String template : StatsConfig.LINES.get()) {
            String line = fill(template, mc);
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        float scale = StatsConfig.SCALE.get().floatValue();
        int width = 0, lineHeight = mc.font.lineHeight + 1;
        for (String line : lines) {
            width = Math.max(width, mc.font.width(line));
        }
        int boxW = (int) ((width + 4) * scale), boxH = (int) ((lines.size() * lineHeight + 3) * scale);
        String anchor = StatsConfig.ANCHOR.get();
        float px = anchor.endsWith("right") ? g.guiWidth() - StatsConfig.X.get() - boxW + 2 * scale : StatsConfig.X.get() + 2 * scale;
        float py = anchor.startsWith("bottom") ? g.guiHeight() - StatsConfig.Y.get() - boxH + 2 * scale : StatsConfig.Y.get() + 2 * scale;
        g.pose().pushPose();
        g.pose().translate(px, py, 0);
        g.pose().scale(scale, scale, 1);
        int alpha = StatsConfig.BACKGROUND.get();
        if (alpha > 0 && !lines.isEmpty()) {
            g.fill(-2, -2, width + 2, lines.size() * lineHeight + 1, alpha << 24);
        }
        int y = 0;
        for (String line : lines) {
            g.drawString(mc.font, line, 0, y, 0xFFFFFFFF, true);
            y += lineHeight;
        }
        g.pose().popPose();
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
