package com.wf.wflib.stream;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/** {@code wflib-stream.toml}: immutable snapshot, rebuilt on every load. */
public final class StreamConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.IntValue DETACHED_STREAM_RADIUS;
    private static final ModConfigSpec.IntValue DETACHED_CHUNKS_PER_TICK;
    private static final ModConfigSpec.BooleanValue DETACHED_SUPPRESS_BODY_ENTITIES;
    private static final ModConfigSpec.IntValue DETACHED_BODY_VIEW_DISTANCE;
    private static final ModConfigSpec.IntValue DETACHED_BODY_TICKET_RADIUS;
    private static final ModConfigSpec.IntValue WAKEUP_TIMEOUT;
    private static final ModConfigSpec.IntValue WAKEUP_TICKET_RADIUS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> STREAM_DEBUG;
    private static final ModConfigSpec.IntValue STREAM_DEBUG_HEARTBEAT;

    public record Snapshot(int streamRadius, int maxChunksPerTick, boolean suppressBodyStream, int bodyViewDistance,
                           int bodyTicketRadius, int wakeupTimeout, int wakeupTicketRadius,
                           List<String> debugCategories, int heartbeatTicks) {}

    private static volatile Snapshot snapshot = new Snapshot(12, 16, true, 2, 2, 200, 2, List.of(), 40);

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Remote operation: a player rides an entity from afar (a UAV) while their body stays parked.",
                        "Their view is moved onto the entity and its terrain streamed to them.")
                .push("detachedBody");
        DETACHED_STREAM_RADIUS = b
                .comment("Chunk radius streamed around the remotely operated entity, clamped to the server view distance.")
                .defineInRange("streamRadius", 12, 2, 32);
        DETACHED_CHUNKS_PER_TICK = b
                .comment("Chunk packets sent to one operator per tick. Unbounded, taking control dumps the whole",
                        "view square at once and stalls input for seconds.")
                .defineInRange("maxChunksPerTick", 16, 1, 1024);
        DETACHED_SUPPRESS_BODY_ENTITIES = b
                .comment("Stop tracking entities around the parked body to its operator. Whatever the body rides or",
                        "carries stays tracked; everything returns when control ends.")
                .define("suppressBodyStream", true);
        DETACHED_BODY_VIEW_DISTANCE = b
                .comment("View distance of the parked body. Below 2 keeps the normal view distance.")
                .defineInRange("bodyViewDistance", 2, -1, 32);
        DETACHED_BODY_TICKET_RADIUS = b
                .comment("Chunk radius loaded around the parked body instead of its player ticket. Chunks outside it",
                        "stop ticking (mobs, redstone, farms) until control ends. Negative keeps the player ticket.")
                .defineInRange("bodyTicketRadius", 2, -1, 8);
        WAKEUP_TIMEOUT = b
                .comment("Ticks to wait for a remotely operable entity in unloaded terrain to load on connect.")
                .defineInRange("wakeupTimeout", 200, 20, 6000);
        WAKEUP_TICKET_RADIUS = b
                .comment("Chunk ticket radius while waking such an entity.")
                .defineInRange("wakeupTicketRadius", 2, 1, 8);
        b.pop();

        b.push("streamDebug");
        STREAM_DEBUG = b
                .comment("Chunk streaming log categories: ALL, SESSION, TICKET, CHUNK, CENTER, WAKEUP, CLIENT, CACHE.",
                        "Empty = off. `/wflib stream debug` overrides until `/wflib stream reset`.")
                .defineListAllowEmpty("categories", List.of(), () -> "", o -> o instanceof String);
        STREAM_DEBUG_HEARTBEAT = b
                .comment("Ticks between debug heartbeat lines.")
                .defineInRange("heartbeatTicks", 40, 1, 12000);
        b.pop();
        SPEC = b.build();
    }

    private StreamConfig() {
    }

    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        snapshot = new Snapshot(DETACHED_STREAM_RADIUS.get(), DETACHED_CHUNKS_PER_TICK.get(),
                DETACHED_SUPPRESS_BODY_ENTITIES.get(), DETACHED_BODY_VIEW_DISTANCE.get(),
                DETACHED_BODY_TICKET_RADIUS.get(), WAKEUP_TIMEOUT.get(), WAKEUP_TICKET_RADIUS.get(),
                List.copyOf(STREAM_DEBUG.get()), STREAM_DEBUG_HEARTBEAT.get());
    }

    public static Snapshot get() {
        return snapshot;
    }
}
