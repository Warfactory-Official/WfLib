package com.wf.wflib.fx.api;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Flashback replay state. A replayed client is its own recorded player: events the live client ignores because it
 * predicted them itself (own shots, own sounds) are the only copy a replay has. Flashback is optional, so this is
 * reflective; absent or failing => not a replay. Client-only.
 */
public final class Replay {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String FLASHBACK = "com.moulberry.flashback.";
    private static final boolean PRESENT = exists(FLASHBACK + "Flashback");
    private static final MethodHandle IN_REPLAY = flag("isInReplay");
    private static final MethodHandle EXPORTING = flag("isExporting");
    private static final MethodHandle REPLAY_SERVER = replayServer();
    private static final MethodHandle CLIENT_RENDERING = serverFlag("doClientRendering", false);
    private static final MethodHandle PROCESSING_SNAPSHOT = serverFlag("isProcessingSnapshot", false);
    private static final MethodHandle FAST_FORWARDING = serverFlag("fastForwarding", true);
    private static final MethodHandle EXPORT_JOB = exportJob();
    private static final MethodHandle SETTINGS = exportMethod("ExportJob", "getSettings", "ExportSettings");
    private static final MethodHandle START = exportMethod("ExportSettings", "startTick", null);
    private static final MethodHandle END = exportMethod("ExportSettings", "endTick", null);

    private Replay() {
    }

    /** Flashback classes resolvable. */
    public static boolean present() {
        return PRESENT;
    }

    /** A Flashback replay is being played or exported. */
    public static boolean active() {
        // Flashback's flag is only refreshed at a few points; a live world must not read a stale "true". The server
        // check is computed live.
        return call(IN_REPLAY) && hasReplayServer();
    }

    private static boolean hasReplayServer() {
        try {
            return REPLAY_SERVER == null || (Object) REPLAY_SERVER.invokeExact() != null;
        } catch (Throwable t) {
            return true;
        }
    }

    /** A Flashback video export is rendering (frames are not driven by {@code Minecraft.runTick}). */
    public static boolean exporting() {
        return call(EXPORTING);
    }

    /**
     * The replay is seeking (fast-forwarding, or applying a snapshot): Flashback's own gate for transient effects.
     * Effects replayed then spawn on the particle clock of the moment the seek lands, so every one of the skipped
     * stretch would appear at once.
     */
    public static boolean skipping() {
        if (REPLAY_SERVER == null || CLIENT_RENDERING == null) {
            return false;
        }
        try {
            Object server = (Object) REPLAY_SERVER.invokeExact();
            if (server == null) {
                return false;
            }
            // An export never clears Flashback's private processedSnapshot, so doClientRendering() stays false for
            // the whole render; its two public seek flags are the real signal there.
            if (exporting() && PROCESSING_SNAPSHOT != null && FAST_FORWARDING != null) {
                return (boolean) PROCESSING_SNAPSHOT.invokeExact(server) || (boolean) FAST_FORWARDING.invokeExact(server);
            }
            return !(boolean) CLIENT_RENDERING.invokeExact(server);
        } catch (Throwable t) {
            return false;
        }
    }

    /** A Flashback screenshot is being rendered: an export of one frame, started and ended on a whole tick. */
    public static boolean screenshot() {
        if (EXPORT_JOB == null || SETTINGS == null || START == null || END == null) {
            return false;
        }
        try {
            Object job = (Object) EXPORT_JOB.invokeExact();
            if (job == null) {
                return false;
            }
            Object settings = (Object) SETTINGS.invokeExact(job);
            return (int) END.invokeExact(settings) == (int) START.invokeExact(settings);
        } catch (Throwable t) {
            return false;
        }
    }

    private interface Resolver {
        MethodHandle resolve() throws ReflectiveOperationException;
    }

    /** ClassNotFound => silent null; other failure with Flashback present => warn once, null. */
    private static MethodHandle resolve(Resolver r) {
        try {
            return r.resolve();
        } catch (ClassNotFoundException e) {
            return null;
        } catch (ReflectiveOperationException | LinkageError e) {
            LOG.warn("Flashback is installed but its replay hooks did not resolve; replay handling is off", e);
            return null;
        }
    }

    private static boolean exists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static MethodHandle flag(String name) {
        return resolve(() -> MethodHandles.publicLookup().findStatic(Class.forName(FLASHBACK + "Flashback"), name,
                MethodType.methodType(boolean.class)));
    }

    private static MethodHandle replayServer() {
        return resolve(() -> MethodHandles.publicLookup().findStatic(Class.forName(FLASHBACK + "Flashback"),
                "getReplayServer", MethodType.methodType(Class.forName(FLASHBACK + "playback.ReplayServer")))
                .asType(MethodType.methodType(Object.class)));
    }

    /** {@code (ReplayServer) -> boolean}, as {@code (Object) -> boolean}; {@code field}: public field, else method. */
    private static MethodHandle serverFlag(String name, boolean field) {
        return resolve(() -> {
            Class<?> server = Class.forName(FLASHBACK + "playback.ReplayServer");
            MethodHandles.Lookup l = MethodHandles.publicLookup();
            MethodHandle h = field ? l.findGetter(server, name, boolean.class)
                    : l.findVirtual(server, name, MethodType.methodType(boolean.class));
            return h.asType(MethodType.methodType(boolean.class, Object.class));
        });
    }

    private static MethodHandle exportJob() {
        return resolve(() -> MethodHandles.publicLookup().findStaticGetter(Class.forName(FLASHBACK + "Flashback"),
                "EXPORT_JOB", Class.forName(FLASHBACK + "exporting.ExportJob"))
                .asType(MethodType.methodType(Object.class)));
    }

    /** {@code owner.method()} as {@code (Object) -> Object}, or {@code (Object) -> int} when {@code returns} is null. */
    private static MethodHandle exportMethod(String owner, String method, String returns) {
        return resolve(() -> {
            Class<?> o = Class.forName(FLASHBACK + "exporting." + owner);
            MethodType type = returns == null ? MethodType.methodType(int.class)
                    : MethodType.methodType(Class.forName(FLASHBACK + "exporting." + returns));
            MethodHandle h = MethodHandles.publicLookup().findVirtual(o, method, type);
            return h.asType(MethodType.methodType(returns == null ? int.class : Object.class, Object.class));
        });
    }

    private static boolean call(MethodHandle h) {
        if (h == null) {
            return false;
        }
        try {
            return (boolean) h.invokeExact();
        } catch (Throwable t) {
            return false;
        }
    }
}
