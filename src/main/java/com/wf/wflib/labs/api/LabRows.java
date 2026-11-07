package com.wf.wflib.labs.api;

import com.wf.wflib.round.api.KineticPreset;
import com.wf.wflib.round.api.KineticPresets;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/** Shared producer plumbing. */
public final class LabRows {
    private LabRows() {
    }

    /** A silent server player not in the world: the body armour and damage code answers for (players carry the game's own resistances). */
    public static net.neoforged.neoforge.common.util.FakePlayer victim(net.minecraft.server.level.ServerLevel level) {
        var p = new net.neoforged.neoforge.common.util.FakePlayer(level, new com.mojang.authlib.GameProfile(
                java.util.UUID.nameUUIDFromBytes("wflabs".getBytes(java.nio.charset.StandardCharsets.UTF_8)), "wflabs"));
        p.setSilent(true);
        return p;
    }

    public static KineticPreset preset(LabLauncher l) {
        return KineticPresets.get(ResourceLocation.parse(l.roundId()));
    }

    public static String origin(LabLauncher l) {
        return LabContents.DEV_ORIGIN.equals(LabContents.origin(l.roundId()))
                || LabContents.DEV_ORIGIN.equals(LabContents.origin(l.id())) ? LabContents.DEV_ORIGIN
                : LabContents.SHIPPED;
    }

    public static String extra(LabLauncher l) {
        Map<String, Object> m = l.extra();
        return m.isEmpty() ? null : json(m);
    }

    /** Flat JSON object, keys sorted; values Number or String (other => toString). */
    public static String json(Map<String, Object> m) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : new java.util.TreeMap<>(m).entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(e.getKey().replace("\"", "\\\"")).append("\":");
            if (e.getValue() instanceof Number n) {
                sb.append(n);
            } else {
                sb.append('"').append(String.valueOf(e.getValue()).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            }
        }
        return sb.append('}').toString();
    }

    /** {@code work} per item on {@code pool}, each with its own {@link #victim}, created here on the calling thread (player construction needs the server thread). */
    public static <T> void eachWithVictim(ExecutorService pool, net.minecraft.server.level.ServerLevel level, List<T> items,
                                          java.util.function.BiConsumer<T, net.neoforged.neoforge.common.util.FakePlayer> work) {
        List<net.neoforged.neoforge.common.util.FakePlayer> victims = new java.util.ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            victims.add(victim(level));
        }
        List<Integer> ix = new java.util.ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ix.add(i);
        }
        each(pool, ix, i -> work.accept(items.get(i), victims.get(i)));
    }

    /** Runs {@code work} per item on {@code pool}, waiting for all; first failure rethrown. */
    public static <T> void each(ExecutorService pool, List<T> items, java.util.function.Consumer<T> work) {
        List<Future<Void>> fs = new java.util.ArrayList<>(items.size());
        for (T item : items) {
            Callable<Void> c = () -> {
                work.accept(item);
                return null;
            };
            fs.add(pool.submit(c));
        }
        for (Future<Void> f : fs) {
            try {
                f.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            } catch (ExecutionException e) {
                throw new IllegalStateException(e.getCause());
            }
        }
    }
}
