package com.wf.wflib.soldier;

import com.mojang.logging.LogUtils;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Vanilla resource-pack consent, caching and download. The optional HTTP endpoint serves exactly one audio bundle. */
final class VoiceDelivery {
    static final ModConfigSpec SPEC;
    private static final ModConfigSpec.ConfigValue<String> BASE_URL;
    private static final ModConfigSpec.IntValue PORT;
    static {
        var b = new ModConfigSpec.Builder();
        BASE_URL = b.comment("Public HTTP(S) base URL for voice downloads, e.g. http://your-server:25586. Empty disables offers.",
                        "Clients use Minecraft's normal resource-pack prompt. Forward the port when hosting across the internet.",
                        "With httpPort=0, host generated/voices-resources.zip yourself as /wflib-voices/<sha1>.zip.")
                .gameRestart().define("resourceBaseUrl", "");
        PORT = b.comment("Optional built-in audio-only HTTP listener; 0 disables it. Restart to change this port.")
                .gameRestart().defineInRange("httpPort", 0, 0, 65535);
        SPEC = b.build();
    }

    private record Bundle(byte[] bytes, String hash, UUID id) {}
    private static volatile Bundle bundle;
    private static HttpServer http;
    private static ThreadPoolExecutor workers;

    private VoiceDelivery() {}

    static void start(MinecraftServer server) {
        stop();
        try {
            SoldierVoices.reload();
            refresh(server);
            if (PORT.get() == 0 || BASE_URL.get().isBlank()) return;
            baseUrl(BASE_URL.get());
            http = HttpServer.create(new InetSocketAddress(PORT.get()), 8);
            workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16), r -> {
                Thread t = new Thread(r, "warforge-voice-download"); t.setDaemon(true); return t;
            });
            http.setExecutor(workers);
            http.createContext("/wflib-voices/", exchange -> {
                try (exchange) {
                    Bundle current = bundle;
                    String method = exchange.getRequestMethod();
                    if (!method.equals("GET") && !method.equals("HEAD")) {
                        exchange.sendResponseHeaders(405, -1); return;
                    }
                    if (current == null || !exchange.getRequestURI().getRawPath().equals("/wflib-voices/" + current.hash + ".zip")
                            || exchange.getRequestURI().getRawQuery() != null) {
                        exchange.sendResponseHeaders(404, -1); return;
                    }
                    exchange.getResponseHeaders().set("Content-Type", "application/zip");
                    exchange.getResponseHeaders().set("Content-Length", Integer.toString(current.bytes.length));
                    exchange.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable");
                    exchange.sendResponseHeaders(200, method.equals("HEAD") ? -1 : current.bytes.length);
                    if (method.equals("GET")) exchange.getResponseBody().write(current.bytes);
                }
            });
            http.start();
            LogUtils.getLogger().info("NPC voice HTTP on port {}; public base {}", PORT.get(), BASE_URL.get());
        } catch (Exception e) {
            stop();
            LogUtils.getLogger().error("NPC voice download startup failed; local packs still work", e);
        }
    }

    static String baseUrl(String value) {
        URI uri = URI.create(value);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("resourceBaseUrl must be an HTTP(S) base URL without credentials, query or fragment");
        return value.replaceAll("/+$", "");
    }

    static void refresh(MinecraftServer server) throws IOException {
        Bundle previous = bundle;
        if (SoldierVoices.ids().isEmpty()) bundle = null;
        else {
            byte[] bytes = Files.readAllBytes(SoldierVoices.bundle());
            try {
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
                bundle = new Bundle(bytes, hash, UUID.nameUUIDFromBytes(hash.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
            } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        }
        if (previous != null && (bundle == null || !previous.id.equals(bundle.id))) {
            for (ServerPlayer player : server.getPlayerList().getPlayers())
                player.connection.send(new ClientboundResourcePackPopPacket(Optional.of(previous.id)));
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) offer(player);
    }

    static void offer(ServerPlayer player) {
        Bundle current = bundle;
        if (current == null || BASE_URL.get().isBlank()) return;
        var owner = player.server.getSingleplayerProfile();
        if (owner != null && owner.getId().equals(player.getUUID())) return;
        try {
            String url = baseUrl(BASE_URL.get()) + "/wflib-voices/" + current.hash + ".zip";
            player.connection.send(new ClientboundResourcePackPushPacket(current.id, url, current.hash, false,
                    Optional.of(Component.literal("NPC combat voice packs"))));
        } catch (IllegalArgumentException e) { LogUtils.getLogger().error("Invalid NPC voice resourceBaseUrl: {}", e.getMessage()); }
    }

    static void stop() {
        if (http != null) { http.stop(0); http = null; }
        if (workers != null) { workers.shutdownNow(); workers = null; }
        bundle = null;
        SoldierVoices.clear();
    }
}
