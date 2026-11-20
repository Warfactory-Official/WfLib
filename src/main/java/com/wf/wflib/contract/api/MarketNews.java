package com.wf.wflib.contract.api;

import net.minecraft.server.MinecraftServer;

import java.util.function.Function;

/** The one-line state of the markets for the Operations app; an economy mod registers it, otherwise the line is empty. */
public final class MarketNews {
    private static volatile Function<MinecraftServer, String> provider;

    private MarketNews() {
    }

    public static void provider(Function<MinecraftServer, String> source) {
        provider = source;
    }

    public static String headline(MinecraftServer server) {
        var source = provider;
        String line = source == null ? null : source.apply(server);
        return line == null ? "" : line;
    }
}
