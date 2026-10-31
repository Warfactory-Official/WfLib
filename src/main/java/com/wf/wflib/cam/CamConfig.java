package com.wf.wflib.cam;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** {@code wflib-cam.toml}: immutable snapshot, rebuilt on every load. */
public final class CamConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.IntValue CAMERA_STREAM_RADIUS;
    private static final ModConfigSpec.IntValue CAMERA_STREAM_BUDGET;
    private static final ModConfigSpec.IntValue CAMERA_MAX_CHANNELS;

    public record Snapshot(int streamRadius, int streamBudget, int maxBound) {}

    private static volatile Snapshot snapshot = new Snapshot(8, 24, 8);

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.comment("Terrain streaming for drone camera feeds. A client can only draw chunks the server has sent",
                        "it, and the server only sends the ones around the player's own body, so without this a",
                        "feed goes black the moment the drone outruns its operator's view distance, however far",
                        "the datalink reaches. These chunks are loaded but never ticked, and are sent only to the",
                        "people actually watching a feed.")
                .push("streaming");
        CAMERA_STREAM_RADIUS = b
                .comment("Chunks either side of a watched drone to keep loaded and stream to its audience. This is",
                        "how far a feed can see, and it is a bubble around the drone rather than a distance from",
                        "the player, so it costs the same whether the drone is fifty blocks away or a thousand.",
                        "Eight is 128 blocks of terrain in every direction, which reads as a horizon on a camera",
                        "that small. Raising it is quadratic in both bandwidth and the chunks the server must",
                        "keep resident. Zero disables streaming entirely and puts the old render-distance ceiling",
                        "back.")
                .defineInRange("radius", 8, 0, 16);
        CAMERA_STREAM_BUDGET = b
                .comment("Chunk packets built per publish (five publishes a second), shared out between however",
                        "many feeds have an audience. This is a fill-rate limit, not a cap: what does not fit is",
                        "sent on the next publish, nearest ring first, so a feed sharpens from the middle",
                        "outwards. Lower it if opening a feed briefly stutters other players on a busy server.")
                .defineInRange("chunksPerPublish", 24, 1, 256);
        b.pop();

        CAMERA_MAX_CHANNELS = b
                .comment("Camera panels: the monitor block and the handheld receiver. Both hold a bounded list of",
                        "bound cameras (fixed ones and camera-equipped drones alike), and switch between them.",
                        "How many cameras one monitor or one receiver can have bound at once. The limit is what",
                        "makes covering a base a matter of choosing angles rather than papering the walls: every",
                        "bound camera is one you did not bind somewhere else. Lowering this below what is already",
                        "bound does not delete anything: an over-full panel keeps working and simply refuses the",
                        "next binding.")
                .defineInRange("maxBound", 8, 1, 32);
        SPEC = b.build();
    }

    private CamConfig() {
    }

    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            snapshot = new Snapshot(CAMERA_STREAM_RADIUS.get(), CAMERA_STREAM_BUDGET.get(), CAMERA_MAX_CHANNELS.get());
        }
    }

    public static Snapshot get() {
        return snapshot;
    }
}
