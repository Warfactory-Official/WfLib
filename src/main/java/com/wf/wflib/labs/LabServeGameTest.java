package com.wf.wflib.labs;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wf.wflib.core.api.WFLib;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;

/** Warm server: streamed rows == written rows, per-request config overrides restore, slow clients are dropped. */
@PrefixGameTestTemplate(false)
public class LabServeGameTest {
    private static final String TEMPLATE = "empty";
    private static final String RUN = "{\"op\":\"run\",\"ids\":[\"round_flight\"],\"args\":\"ranges=0,100,300 rounds=5.56x45/ball\"";

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = "wflib_labs")
    public static void configOverridesApplyAndRestore(GameTestHelper helper) throws Exception {
        Path toml = FMLPaths.CONFIGDIR.get().resolve("wflib-labs.toml");
        String before = sha(toml);
        int samples = LabsConfig.snapshot().samples();
        JsonObject req = JsonParser.parseString("{\"wflib-labs.toml\":{\"samples\":7}}").getAsJsonObject();
        try (ConfigOverrides o = ConfigOverrides.apply(req)) {
            helper.assertTrue(LabsConfig.snapshot().samples() == 7, "snapshot not refreshed: " + LabsConfig.snapshot());
            helper.assertTrue(o.effective().equals(req), "effective " + o.effective());
        }
        helper.assertTrue(LabsConfig.snapshot().samples() == samples, "samples not restored");
        helper.assertTrue(before.equals(sha(toml)), "toml changed on disk");
        for (String bad : new String[]{"{\"wflib-labs.toml\":{\"nope\":1}}", "{\"nope.toml\":{\"samples\":1}}",
                "{\"wflib-labs.toml\":{\"samples\":0}}", "{\"wflib-labs.toml\":{\"samples\":1.5}}",
                "{\"wflib-labs.toml\":{\"samples\":\"x\"}}",
                "{\"wflib-labs.toml\":{\"threads\":1,\"samples\":0}}"}) {
            boolean threw = false;
            try (ConfigOverrides ignored = ConfigOverrides.apply(JsonParser.parseString(bad).getAsJsonObject())) {
                // unreachable
            } catch (IllegalArgumentException e) {
                threw = true;
            }
            helper.assertTrue(threw, "accepted " + bad);
            helper.assertTrue(LabsConfig.snapshot().samples() == samples && LabsConfig.snapshot().threads() == 0, "partial apply: " + bad);
        }
        helper.assertTrue(before.equals(sha(toml)), "toml changed on disk after rejects");
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = "wflib_labs", timeoutTicks = 2400)
    public static void streamedRowsEqualWrittenRows(GameTestHelper helper) throws Exception {
        MinecraftServer server = helper.getLevel().getServer();
        Path toml = FMLPaths.CONFIGDIR.get().resolve("wflib-labs.toml");
        String before = sha(toml);
        try (LabServe serve = LabServe.open(0, LabServe.DEFAULT_QUEUE_BYTES)) {
            List<JsonObject> frames = converse(helper, server, serve,
                    RUN + ",\"config\":{\"wflib-labs.toml\":{\"samples\":5}}}", "done", "error");
            JsonObject last = frames.get(frames.size() - 1);
            helper.assertTrue("done".equals(last.get("type").getAsString()), "run failed: " + last);
            Path dir = Path.of(last.get("dir").getAsString());
            try {
                JsonObject manifest = last.getAsJsonObject("manifest");
                helper.assertTrue(manifest.getAsJsonObject("overrides").getAsJsonObject("wflib-labs.toml").get("samples").getAsInt() == 5,
                        "overrides not recorded");
                helper.assertTrue(LabsConfig.snapshot().samples() != 5, "override leaked past the run");
                helper.assertTrue(before.equals(sha(toml)), "toml changed on disk");
                List<String> streamed = new ArrayList<>();
                String table = null;
                for (JsonObject f : frames) {
                    switch (f.get("type").getAsString()) {
                        case "table" -> table = f.get("id").getAsString();
                        case "rows" -> {
                            helper.assertTrue(f.get("table").getAsString().equals(table), "rows before table");
                            f.getAsJsonArray("rows").forEach(r -> streamed.add(r.toString()));
                        }
                        default -> { }
                    }
                }
                List<String> written = new ArrayList<>();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(
                        Files.newInputStream(dir.resolve("round_flight.ndjson.gz"))), StandardCharsets.UTF_8))) {
                    for (String line; (line = r.readLine()) != null; ) {
                        written.add(line);
                    }
                }
                helper.assertTrue(!written.isEmpty(), "no rows written");
                helper.assertTrue(written.size() == streamed.size(), "rows " + written.size() + " written, " + streamed.size() + " streamed");
                for (int i = 0; i < written.size(); i++) {
                    helper.assertTrue(written.get(i).equals(streamed.get(i)), "row " + i + " differs: " + written.get(i) + " vs " + streamed.get(i));
                }
            } finally {
                deleteTree(dir);
            }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = "wflib_labs", timeoutTicks = 2400)
    public static void slowClientIsDroppedAndServerStaysUsable(GameTestHelper helper) throws Exception {
        MinecraftServer server = helper.getLevel().getServer();
        try (LabServe serve = LabServe.open(0, 2000)) {
            try (Socket sock = new Socket(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), serve.port())) {
                OutputStream os = sock.getOutputStream();
                os.write((RUN + "}\n").getBytes(StandardCharsets.UTF_8));
                os.flush();
                serve.pump(server, 30_000);
                boolean sawError = false;
                sock.setSoTimeout(10_000);
                BufferedReader r = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
                try {
                    for (String line; (line = r.readLine()) != null; ) {
                        if (line.contains("\"type\":\"error\"") && line.contains("too slow")) {
                            sawError = true;
                        }
                    }
                } catch (IOException e) {
                    // reset after the drop is fine
                }
                helper.assertTrue(sawError, "no too-slow error frame");
            }
            serve.queueLimit(LabServe.DEFAULT_QUEUE_BYTES);
            List<JsonObject> frames = converse(helper, server, serve, RUN + "}", "done", "error");
            helper.assertTrue("done".equals(frames.get(frames.size() - 1).get("type").getAsString()), "server unusable after drop");
            deleteTree(Path.of(frames.get(frames.size() - 1).get("dir").getAsString()));
        }
        helper.succeed();
    }

    private static List<JsonObject> converse(GameTestHelper helper, MinecraftServer server, LabServe serve, String request,
                                             String... terminal) throws Exception {
        List<JsonObject> frames = new CopyOnWriteArrayList<>();
        AtomicBoolean finished = new AtomicBoolean();
        Throwable[] failure = new Throwable[1];
        Thread client = new Thread(() -> {
            try (Socket sock = new Socket(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), serve.port())) {
                sock.setSoTimeout(120_000);
                sock.getOutputStream().write((request + "\n").getBytes(StandardCharsets.UTF_8));
                sock.getOutputStream().flush();
                BufferedReader r = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
                for (String line; (line = r.readLine()) != null; ) {
                    JsonObject f = JsonParser.parseString(line).getAsJsonObject();
                    frames.add(f);
                    if (java.util.Arrays.asList(terminal).contains(f.get("type").getAsString())) {
                        break;
                    }
                }
            } catch (Throwable t) {
                failure[0] = t;
            } finally {
                finished.set(true);
            }
        }, "labserve-test-client");
        client.setDaemon(true);
        client.start();
        long t0 = System.nanoTime();
        while (!finished.get() && System.nanoTime() - t0 < 100_000_000_000L) {
            serve.pump(server, 20);
        }
        client.join(5000);
        if (failure[0] != null) {
            throw new IllegalStateException(failure[0]);
        }
        helper.assertTrue(finished.get() && !frames.isEmpty(), "client got no terminal frame");
        return frames;
    }

    private static String sha(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    private static void deleteTree(Path dir) throws IOException {
        try (var walk = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) {
                Files.deleteIfExists(p);
            }
        }
    }
}
