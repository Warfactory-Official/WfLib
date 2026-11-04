package com.wf.wflib.labs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.logging.LogUtils;
import com.wf.wflib.labs.api.LabContent;
import com.wf.wflib.labs.api.LabContents;
import com.wf.wflib.labs.api.LabProducer;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Warm lab server: NDJSON over loopback TCP, one request line in, frames out. Socket IO runs on its own threads;
 * runs execute on the thread that calls {@link #pump}, queued first come first served.
 *
 * <pre>
 * {"op":"list"}                                   -> {"type":"list","producers":[{id,table,domain,columns}],"config_files":[..]}
 * {"op":"run","ids":[..],"args":"k=v ..","config":{"file.toml":{"a.b":v}}}
 *     -> {"type":"queued","position":n}?  {"type":"table",id,name,domain,columns}
 *        {"type":"rows","table":id,"rows":[[..],..]}  {"type":"progress",table,rows,index,total}
 *        {"type":"done","dir":..,"manifest":{..}}  | {"type":"error","message":..}
 * {"op":"shutdown"}                               -> {"type":"shutdown"}, server exits after queued runs
 * </pre>
 */
final class LabServe implements AutoCloseable {
    static final long DEFAULT_QUEUE_BYTES = 64L << 20;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_REQUEST = 1 << 20;
    private static final int MAX_CLIENTS = 16;
    private static final int BATCH_ROWS = 512;
    private static final int BATCH_CHARS = 256 << 10;
    private static final long BATCH_NANOS = 100_000_000L;
    private static final Job STOP = new Job(null, null, null, null);

    private record Job(Client client, String ids, Map<String, String> args, JsonObject config) {
    }

    private final ServerSocket socket;
    private volatile long queueLimit;
    private final BlockingQueue<Job> jobs = new LinkedBlockingQueue<>();
    private final Set<Client> clients = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    private LabServe(ServerSocket socket, long queueLimit) {
        this.socket = socket;
        this.queueLimit = queueLimit;
    }

    /**
     * @param port 0 = any free port
     * @param queueBytes per-client outbound buffer; beyond it the client is dropped with an error frame
     */
    static LabServe open(int port, long queueBytes) throws IOException {
        ServerSocket s = new ServerSocket();
        s.bind(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127, 0, 0, 1}), port));
        LabServe serve = new LabServe(s, queueBytes);
        Thread t = new Thread(serve::acceptLoop, "wflabs-serve-accept");
        t.setDaemon(true);
        t.start();
        return serve;
    }

    void queueLimit(long chars) {
        this.queueLimit = chars;
    }

    int port() {
        return socket.getLocalPort();
    }

    /** {@code -Dwflabs.serve=<port>}: blocks the calling (server) thread until a shutdown request. */
    static void serve(MinecraftServer server, int port) throws IOException {
        long queue = Long.getLong("wflabs.serve.queueBytes", DEFAULT_QUEUE_BYTES);
        try (LabServe s = open(port, queue)) {
            LOGGER.info("[labs] serve listening 127.0.0.1:{}", s.port());
            while (s.pump(server, 60_000L)) {
                continue;
            }
        }
        LOGGER.info("[labs] serve stopped");
    }

    /**
     * Runs at most one queued job on the calling thread.
     *
     * @return false once a shutdown request was reached
     */
    boolean pump(MinecraftServer server, long timeoutMs) {
        Job job;
        try {
            job = jobs.poll(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        if (job == null) {
            return true;
        }
        if (job == STOP) {
            return false;
        }
        Client c = job.client();
        try {
            if (!c.dropped) {
                execute(server, job);
            }
        } finally {
            c.finished();
        }
        return true;
    }

    private void execute(MinecraftServer server, Job job) {
        Client c = job.client();
        StreamObserver obs = new StreamObserver(c);
        ConfigOverrides overrides = null;
        try {
            overrides = ConfigOverrides.apply(job.config());
            LabRun.Result r = LabRun.run(server, job.ids(), job.args(), obs, overrides.effective());
            JsonObject done = new JsonObject();
            done.addProperty("type", "done");
            done.addProperty("dir", r.dir().toAbsolutePath().toString());
            done.add("manifest", r.manifest());
            c.send(done + "\n");
        } catch (RuntimeException | Error e) {
            if (c.dropped) {
                LOGGER.info("[labs] serve: run abandoned, client gone");
            } else {
                LOGGER.warn("[labs] serve: run failed", e);
                c.send(error(describe(e)));
            }
        } finally {
            if (overrides != null) {
                try {
                    overrides.close();
                } catch (RuntimeException e) {
                    LOGGER.error("[labs] serve: config restore failed", e);
                    c.send(error("config restore failed: " + describe(e)));
                }
            }
        }
    }

    private static String describe(Throwable e) {
        Throwable root = e;
        while ((root instanceof IllegalStateException || root instanceof java.util.concurrent.ExecutionException)
                && root.getCause() != null) {
            root = root.getCause();
        }
        return root instanceof IllegalArgumentException ? String.valueOf(root.getMessage())
                : root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    private static String error(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "error");
        o.addProperty("message", message);
        return o + "\n";
    }

    private void acceptLoop() {
        while (!closed) {
            try {
                Socket s = socket.accept();
                if (clients.size() >= MAX_CLIENTS) {
                    s.close();
                    continue;
                }
                s.setTcpNoDelay(true);
                s.setKeepAlive(true);
                Client c = new Client(s);
                clients.add(c);
                c.start();
            } catch (IOException e) {
                if (!closed) {
                    LOGGER.warn("[labs] serve accept: {}", e.toString());
                }
            }
        }
    }

    @Override
    public void close() {
        closed = true;
        try {
            socket.close();
        } catch (IOException e) {
            LOGGER.debug("[labs] serve close: {}", e.toString());
        }
        for (Client c : clients) {
            c.finishAndClose(1500);
        }
    }

    private JsonObject list() {
        JsonArray producers = new JsonArray();
        for (LabProducer p : LabRun.select("all")) {
            JsonObject o = LabRun.tableJson(p.id(), p.table(), 0, 0);
            o.remove("rows");
            o.remove("millis");
            o.addProperty("domain", p.domain());
            o.addProperty("table", p.table().name());
            producers.add(o);
        }
        Set<String> files = new TreeSet<>();
        for (LabContent c : LabContents.REGISTRY.values()) {
            files.addAll(c.configFiles());
        }
        JsonArray f = new JsonArray();
        files.forEach(f::add);
        JsonObject out = new JsonObject();
        out.addProperty("type", "list");
        out.add("producers", producers);
        out.add("config_files", f);
        return out;
    }

    private final class Client {
        private final Socket sock;
        private final BlockingQueue<String> out = new LinkedBlockingQueue<>();
        private final AtomicLong queued = new AtomicLong();
        private final AtomicInteger pending = new AtomicInteger();
        private volatile boolean dropped;
        private volatile boolean readClosed;
        private volatile boolean closeWhenFlushed;
        private Thread writer;

        Client(Socket sock) {
            this.sock = sock;
        }

        void start() {
            writer = new Thread(this::writeLoop, "wflabs-serve-write");
            writer.setDaemon(true);
            writer.start();
            Thread reader = new Thread(this::readLoop, "wflabs-serve-read");
            reader.setDaemon(true);
            reader.start();
        }

        /** Never blocks; a client over the buffer limit is dropped. */
        void send(String frame) {
            if (dropped) {
                return;
            }
            if (queued.addAndGet(frame.length()) > queueLimit) {
                drop("client too slow: outbound buffer over " + queueLimit + " chars, dropped");
                return;
            }
            out.add(frame);
        }

        private synchronized void drop(String message) {
            if (dropped) {
                return;
            }
            dropped = true;
            out.clear();
            out.add(error(message));
            closeWhenFlushed = true;
            LOGGER.warn("[labs] serve: {}", message);
            Thread t = new Thread(() -> {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                closeSocket();
            }, "wflabs-serve-reaper");
            t.setDaemon(true);
            t.start();
        }

        void finished() {
            pending.decrementAndGet();
            maybeClose();
        }

        private void maybeClose() {
            if (readClosed && pending.get() == 0) {
                closeWhenFlushed = true;
            }
        }

        void finishAndClose(long waitMs) {
            closeWhenFlushed = true;
            try {
                if (writer != null) {
                    writer.join(waitMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            closeSocket();
        }

        private void closeSocket() {
            try {
                sock.close();
            } catch (IOException e) {
                LOGGER.debug("[labs] serve socket close: {}", e.toString());
            }
            clients.remove(this);
        }

        private void writeLoop() {
            try (OutputStream os = sock.getOutputStream()) {
                for (;;) {
                    String f = out.poll(200, TimeUnit.MILLISECONDS);
                    if (f == null) {
                        if (closeWhenFlushed) {
                            break;
                        }
                        continue;
                    }
                    queued.addAndGet(-f.length());
                    os.write(f.getBytes(StandardCharsets.UTF_8));
                    if (out.isEmpty()) {
                        os.flush();
                    }
                }
            } catch (IOException e) {
                if (!dropped) {
                    dropped = true;
                    LOGGER.info("[labs] serve: client write failed: {}", e.toString());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                dropped = true;
                closeSocket();
            }
        }

        private void readLoop() {
            try (InputStream in = sock.getInputStream()) {
                ByteArrayOutputStream line = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    for (int i = 0; i < n; i++) {
                        if (buf[i] == '\n') {
                            handle(line.toString(StandardCharsets.UTF_8));
                            line.reset();
                        } else {
                            line.write(buf[i]);
                            if (line.size() > MAX_REQUEST) {
                                drop("request line over " + MAX_REQUEST + " bytes");
                                return;
                            }
                        }
                    }
                }
                if (line.size() > 0) {
                    handle(line.toString(StandardCharsets.UTF_8));
                }
            } catch (SocketException e) {
                LOGGER.debug("[labs] serve read: {}", e.toString());
            } catch (IOException e) {
                LOGGER.info("[labs] serve read: {}", e.toString());
            } finally {
                readClosed = true;
                maybeClose();
            }
        }

        private void handle(String text) {
            if (text.isBlank()) {
                return;
            }
            try {
                JsonObject req = JsonParser.parseString(text).getAsJsonObject();
                String op = req.has("op") ? req.get("op").getAsString() : "";
                switch (op) {
                    case "list" -> send(list() + "\n");
                    case "shutdown" -> {
                        send("{\"type\":\"shutdown\"}\n");
                        jobs.add(STOP);
                    }
                    case "run" -> {
                        Job job = job(req);
                        pending.incrementAndGet();
                        jobs.add(job);
                        send("{\"type\":\"queued\",\"position\":" + jobs.size() + "}\n");
                    }
                    default -> send(error("unknown op '" + op + "'; have list, run, shutdown"));
                }
            } catch (RuntimeException e) {
                send(error("bad request: " + e.getMessage()));
            }
        }

        private Job job(JsonObject req) {
            if (!req.has("ids")) {
                throw new IllegalArgumentException("run wants ids");
            }
            JsonElement ids = req.get("ids");
            String joined;
            if (ids.isJsonArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonElement e : ids.getAsJsonArray()) {
                    sb.append(sb.length() > 0 ? "," : "").append(e.getAsString());
                }
                joined = sb.toString();
            } else {
                joined = ids.getAsString();
            }
            if (joined.isBlank()) {
                throw new IllegalArgumentException("run wants ids");
            }
            Map<String, String> args = new java.util.LinkedHashMap<>();
            JsonElement a = req.get("args");
            if (a != null && a.isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : a.getAsJsonObject().entrySet()) {
                    args.put(e.getKey(), e.getValue().getAsString());
                }
            } else if (a != null && !a.isJsonNull()) {
                args.putAll(LabRun.parse(a.getAsString()));
            }
            JsonElement cfg = req.get("config");
            if (cfg != null && !cfg.isJsonNull() && !cfg.isJsonObject()) {
                throw new IllegalArgumentException("config wants {file: {key: value}}");
            }
            return new Job(this, joined, args, cfg == null || cfg.isJsonNull() ? null : cfg.getAsJsonObject());
        }
    }

    private static final class StreamObserver implements LabRun.Observer {
        private final Client client;
        private final StringBuilder batch = new StringBuilder(BATCH_CHARS + 4096);
        private String table;
        private int index;
        private int total;
        private int count;
        private long rows;
        private long last = System.nanoTime();

        StreamObserver(Client client) {
            this.client = client;
        }

        @Override
        public void table(String id, JsonObject header, int index, int total) {
            flush();
            this.table = id;
            this.index = index;
            this.total = total;
            this.rows = 0;
            JsonObject frame = new JsonObject();
            frame.addProperty("type", "table");
            header.entrySet().forEach(e -> frame.add(e.getKey(), e.getValue()));
            client.send(frame + "\n");
        }

        @Override
        public void row(String id, CharSequence line) {
            if (count > 0) {
                batch.append(',');
            }
            batch.append(line, 0, line.length() - 1);
            count++;
            rows++;
            if (count >= BATCH_ROWS || batch.length() >= BATCH_CHARS || System.nanoTime() - last >= BATCH_NANOS) {
                flush();
            }
        }

        @Override
        public void tableDone(String id, long rows) {
            flush();
        }

        @Override
        public boolean cancelled() {
            return client.dropped;
        }

        private void flush() {
            last = System.nanoTime();
            if (count == 0) {
                return;
            }
            client.send("{\"type\":\"rows\",\"table\":" + new JsonPrimitive(table) + ",\"rows\":[" + batch + "]}\n");
            client.send("{\"type\":\"progress\",\"table\":" + new JsonPrimitive(table) + ",\"rows\":" + rows
                    + ",\"index\":" + index + ",\"total\":" + total + "}\n");
            batch.setLength(0);
            count = 0;
        }
    }
}
