package com.wf.wflib.cam.client;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** {@code wflib-cam-client.toml}: immutable snapshot, rebuilt on every load. */
public final class CamClientConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.IntValue CAMERA_MAX_WIDTH;
    private static final ModConfigSpec.IntValue CAMERA_MAX_FPS;
    private static final ModConfigSpec.IntValue CAMERA_MIN_FPS;
    private static final ModConfigSpec.DoubleValue CAMERA_FULL_RATE_DISTANCE;
    private static final ModConfigSpec.IntValue CAMERA_BUDGET;
    private static final ModConfigSpec.IntValue CAMERA_FEED_VIEW_DISTANCE;
    private static final ModConfigSpec.BooleanValue CAMERA_EFFECTS;

    public record Snapshot(int maxWidth, int maxFps, int minFps, double fullRateDistance, int framesPerTick,
                           int feedViewDistance, boolean videoEffects) {}

    private static volatile Snapshot snapshot = new Snapshot(1280, 60, 10, 10.0, 1, 8, true);

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        CAMERA_MAX_WIDTH = b
                .comment("Widest a feed is ever rendered, in pixels; height follows at 16:9. A feed is also",
                        "capped by how large it is actually being drawn, so a monitor across the room costs a",
                        "fraction of this whatever it is set to: raising it only helps a full-screen feed.",
                        "1280 (720p) is the default; 1920 (1080p) is the ceiling.")
                .defineInRange("maxWidth", 1280, 256, 1920);
        CAMERA_MAX_FPS = b
                .comment("Frames per second for the feed the operator is actually looking at. Still the most",
                        "expensive setting here (a feed frame is a second pass over the terrain), but it is",
                        "now a pass and not much more: each feed carries its own section visibility graph, so",
                        "it no longer forces the main view's to be rebuilt as well. 120 is the ceiling.")
                .defineInRange("maxFps", 60, 1, 120);
        CAMERA_MIN_FPS = b
                .comment("Frames per second a feed falls to when it is small or far away. Low is fine: a",
                        "monitor read across a room is legible at ten, and the OSD is composited after the",
                        "video so the readouts never look choppy.")
                .defineInRange("minFps", 10, 1, 120);
        CAMERA_FULL_RATE_DISTANCE = b
                .comment("Blocks within which a monitor gets the full frame rate, falling off linearly to",
                        "minFps at the distance a monitor stops drawing at all.")
                .defineInRange("fullRateDistance", 10.0, 0.0, 64.0);
        CAMERA_BUDGET = b
                .comment("How many feeds may be re-rendered in a single frame. One is strongly recommended and",
                        "is enough for any number of feeds: they take turns, and a wall of eight monitors",
                        "showing eight different drones costs exactly what one monitor costs, each just",
                        "updating an eighth as often.")
                .defineInRange("framesPerTick", 1, 1, 4);
        CAMERA_FEED_VIEW_DISTANCE = b
                .comment("Chunks either side of the drone a feed renders, and the size of the section grid it",
                        "keeps to do it. This is a bubble around the drone, not a distance from you: the server",
                        "streams the terrain for it, so a drone a kilometre out sees exactly as far as one",
                        "overhead. Should match the server's streaming.radius: larger than the",
                        "server sends only builds empty sections, smaller than it sends wastes what it sent.",
                        "Each feed being drawn holds a grid this size, so this is the setting to lower first if",
                        "a wall of monitors costs memory.")
                .defineInRange("feedViewDistance", 8, 2, 16);
        CAMERA_EFFECTS = b
                .comment("Run the video post chain (macroblocking, sensor noise, desaturation, thermal",
                        "palette) on feeds and on vehicle sights that use it. Turning this off leaves a clean picture at the same frame rate and saves",
                        "two full-screen passes per feed frame, and loses the link quality read-out that the",
                        "artifacts are, so a failing datalink stops being visible.")
                .define("videoEffects", true);
        SPEC = b.build();
    }

    private CamClientConfig() {
    }

    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            snapshot = new Snapshot(CAMERA_MAX_WIDTH.get(), CAMERA_MAX_FPS.get(), CAMERA_MIN_FPS.get(), CAMERA_FULL_RATE_DISTANCE.get(), CAMERA_BUDGET.get(), CAMERA_FEED_VIEW_DISTANCE.get(), CAMERA_EFFECTS.get());
        }
    }

    public static Snapshot get() {
        return snapshot;
    }
}
