package com.wf.wflib.pda.client;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.VisibleForTesting;
import org.lwjgl.opengl.GL11;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * The Terminal's {@code fastfetch}: this machine, whatever server the game is on. Runs entirely on the client, nothing
 * is sent anywhere: the real {@code fastfetch} when it is installed, else a summary from the JVM and the game.
 */
final class Fastfetch {
    private static final int MAX_LINES = 60;
    private static final int MAX_WIDTH = 160;
    private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;?]*[ -/]*[@-~]|\u001B\\].*?(\u0007|\u001B\\\\)");

    private Fastfetch() {
    }

    /** Lines to {@code out} on the render thread, a moment later (the real binary is asked off-thread). */
    static void run(Consumer<List<String>> out) {
        // GL is only valid here.
        List<String> game = game();
        Thread worker = new Thread(() -> {
            List<String> lines = external();
            if (lines.isEmpty()) {
                lines = builtIn(game);
            }
            List<String> result = lines;
            Minecraft.getInstance().execute(() -> out.accept(result));
        }, "wflib-fastfetch");
        worker.setDaemon(true);
        worker.start();
    }

    /** Render-thread facts of the game itself. */
    private static List<String> game() {
        Minecraft mc = Minecraft.getInstance();
        List<String> lines = new ArrayList<>();
        lines.add("Game: Minecraft " + SharedConstants.getCurrentVersion().getName() + ", " + ModList.get().size()
                + " mods");
        lines.add("GPU: " + GlStateManager._getString(GL11.GL_RENDERER));
        lines.add("GL: " + GlStateManager._getString(GL11.GL_VERSION));
        lines.add("Display: " + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight());
        return lines;
    }

    /** The installed {@code fastfetch}, no logo, no colours; empty when it is missing, fails or is slow. */
    private static List<String> external() {
        try {
            Process process = new ProcessBuilder("fastfetch", "--pipe", "--logo", "none").redirectErrorStream(true)
                    .start();
            List<String> raw = new ArrayList<>();
            Thread reader = new Thread(() -> {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream(),
                        StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = in.readLine()) != null && raw.size() < 400) {
                        synchronized (raw) {
                            raw.add(line);
                        }
                    }
                } catch (IOException ignored) {
                    // killed below
                }
            }, "wflib-fastfetch-read");
            reader.setDaemon(true);
            reader.start();
            if (!process.waitFor(4, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return List.of();
            }
            reader.join(500);
            if (process.exitValue() != 0) {
                return List.of();
            }
            synchronized (raw) {
                return clean(raw);
            }
        } catch (IOException | InterruptedException e) {
            return List.of();
        }
    }

    /** Escape sequences out, blank lines dropped, long lines cut, at most {@value #MAX_LINES} lines. */
    @VisibleForTesting
    static List<String> clean(List<String> raw) {
        List<String> out = new ArrayList<>();
        for (String line : raw) {
            String text = ANSI.matcher(line).replaceAll("").replace('\t', ' ').stripTrailing();
            if (text.isBlank()) {
                continue;
            }
            out.add(text.length() > MAX_WIDTH ? text.substring(0, MAX_WIDTH) : text);
            if (out.size() == MAX_LINES) {
                break;
            }
        }
        return out;
    }

    private static List<String> builtIn(List<String> game) {
        List<String> lines = new ArrayList<>();
        lines.add("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " "
                + System.getProperty("os.arch"));
        lines.add("CPU: " + cpu() + " (" + Runtime.getRuntime().availableProcessors() + " threads)");
        lines.add("Memory: " + memory());
        lines.add("Java: " + System.getProperty("java.vm.name") + " " + System.getProperty("java.version"));
        lines.add("Heap: " + size(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) + " / "
                + size(Runtime.getRuntime().maxMemory()));
        lines.add("Game uptime: " + uptime(ManagementFactory.getRuntimeMXBean().getUptime()));
        lines.addAll(game);
        return lines;
    }

    private static String cpu() {
        try {
            for (String line : Files.readAllLines(Path.of("/proc/cpuinfo"))) {
                if (line.startsWith("model name")) {
                    return line.substring(line.indexOf(':') + 1).trim();
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // not Linux
        }
        String id = System.getenv("PROCESSOR_IDENTIFIER");
        return id != null ? id : System.getProperty("os.arch");
    }

    private static String memory() {
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            long total = os.getTotalMemorySize();
            return size(total - os.getFreeMemorySize()) + " / " + size(total);
        }
        return "unknown";
    }

    @VisibleForTesting
    static String size(long bytes) {
        if (bytes < 1024L * 1024) {
            return bytes / 1024 + " KiB";
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format(Locale.ROOT, "%.0f MiB", bytes / (1024.0 * 1024));
        }
        return String.format(Locale.ROOT, "%.1f GiB", bytes / (1024.0 * 1024 * 1024));
    }

    @VisibleForTesting
    static String uptime(long millis) {
        long minutes = millis / 60_000;
        long hours = minutes / 60;
        if (hours > 0) {
            return hours + "h " + minutes % 60 + "m";
        }
        return minutes + "m " + millis / 1000 % 60 + "s";
    }
}
