package com.wf.wflib.map.api.client;

import com.wf.wflib.core.api.OrderedRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Map-mod-neutral entry: layers publish shapes, tools register once; every attached {@link MapHost} draws
 * and dispatches them. Late host => current layers replayed; hosts attach on third-party map-mod timing, so
 * {@link #addHost} is runtime state, not a registry. Client only.
 */
public final class WFMaps {

    private record Published(ResourceKey<Level> dimension, List<MapShape> shapes) {
    }

    private static final List<MapHost> HOSTS = new CopyOnWriteArrayList<>();
    public static final OrderedRegistry<MapTool> TOOLS = OrderedRegistry.create("map", "map.tools");
    /** Client thread only. */
    private static final Map<String, Published> LAYERS = new LinkedHashMap<>();

    private WFMaps() {
    }

    public static void addHost(MapHost host) {
        Minecraft.getInstance().execute(() -> {
            HOSTS.add(host);
            LAYERS.forEach((layer, published) -> host.show(layer, published.dimension(), published.shapes()));
        });
    }

    public static List<MapHost> hosts() {
        return HOSTS;
    }

    /** Any thread; applied on the client thread. */
    public static void publish(String layer, ResourceKey<Level> dimension, List<MapShape> shapes) {
        List<MapShape> copy = List.copyOf(shapes);
        Minecraft.getInstance().execute(() -> {
            if (copy.isEmpty()) {
                LAYERS.remove(layer);
            } else {
                LAYERS.put(layer, new Published(dimension, copy));
            }
            for (MapHost host : HOSTS) {
                host.show(layer, dimension, copy);
            }
        });
    }

    public static void clear(String layer) {
        Minecraft.getInstance().execute(() -> {
            Published gone = LAYERS.remove(layer);
            if (gone != null) {
                for (MapHost host : HOSTS) {
                    host.show(layer, gone.dimension(), List.of());
                }
            }
        });
    }

    public static boolean hasFullscreen() {
        for (MapHost host : HOSTS) {
            if (host.hasFullscreen()) {
                return true;
            }
        }
        return false;
    }

    /** First host with a fullscreen map opens it. @return false = none installed. */
    public static boolean openFullscreen() {
        for (MapHost host : HOSTS) {
            if (host.hasFullscreen()) {
                host.openFullscreen();
                return true;
            }
        }
        return false;
    }

    public static List<MapTool> tools() {
        return TOOLS.values();
    }

    /** Layer ids currently drawn, for diagnostics. */
    public static List<String> layers() {
        return new ArrayList<>(LAYERS.keySet());
    }
}
