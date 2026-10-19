package com.wf.wflib.pda.terminal;

import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Locale;

/**
 * {@code fastfetch server}: what the host is running, for any player holding a PDA. Only what a player may know: no host
 * name, user, address or path.
 */
final class ServerFetch {
    private ServerFetch() {
    }

    static List<String> lines(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return List.of("err: no server");
        }
        Runtime rt = Runtime.getRuntime();
        double msPerTick = server.getAverageTickTimeNanos() / 1.0e6;
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        return List.of(
                "Server: " + (server.isDedicatedServer() ? "dedicated" : "integrated") + ", Minecraft "
                        + SharedConstants.getCurrentVersion().getName() + ", " + ModList.get().size() + " mods",
                "OS: " + System.getProperty("os.name") + " " + System.getProperty("os.arch"),
                "CPU: " + rt.availableProcessors() + " threads",
                "Java: " + System.getProperty("java.vm.name") + " " + System.getProperty("java.version"),
                String.format(Locale.ROOT, "Heap: %d / %d MiB", (rt.totalMemory() - rt.freeMemory()) >> 20,
                        rt.maxMemory() >> 20),
                String.format(Locale.ROOT, "Tick: %.1f ms (%.1f TPS)", msPerTick, Math.min(20.0, 1000.0 / Math.max(msPerTick, 1.0))),
                "Players: " + server.getPlayerCount() + " / " + server.getMaxPlayers(),
                String.format(Locale.ROOT, "Uptime: %dh %dm", uptime / 3600, uptime / 60 % 60));
    }
}
