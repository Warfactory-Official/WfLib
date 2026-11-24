package com.wf.wflib.core.api;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import com.wf.wflib.colony.ColonyData;
import com.wf.wflib.colony.PendingChunkEditData;
import com.wf.wflib.drone.DroneLaunchQueue;
import com.wf.wflib.drone.api.StationData;
import com.wf.wflib.drone.exchange.ExchangeData;
import com.wf.wflib.industry.api.IndustryData;
import com.wf.wflib.missile.api.MissileListenerData;
import com.wf.wflib.round.DeferredImpacts;
import com.wf.wflib.sim.api.SimWorld;
import com.wf.wflib.stream.api.HostWakeupData;
import com.wf.wflib.work.api.WorkData;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Per-level {@link SavedData} reference cache. {@code DimensionDataStorage} is not thread-safe: region workers read
 * the cached reference, the storage is touched on the server thread: {@code LevelEvent.Load} initialises every
 * {@link #HOLDERS} class and primes its cache. Uncached lookup off the server thread => throws.
 */
@EventBusSubscriber(modid = WFLib.MODID)
public final class LevelCache<T extends SavedData> {

    // Every class declaring a LevelCache; a missing entry => first worker use throws.
    private static final List<Class<?>> HOLDERS = List.of(DeferredImpacts.class, IndustryData.class,
            StationData.class, ExchangeData.class, DroneLaunchQueue.class, HostWakeupData.class, ColonyData.class,
            PendingChunkEditData.class, WorkData.class, SimWorld.class, MissileListenerData.class);

    private static final List<LevelCache<?>> ALL = new CopyOnWriteArrayList<>();

    private final String name;
    private final SavedData.Factory<T> factory;
    private final boolean overworldOnly;
    private final ConcurrentHashMap<ServerLevel, T> byLevel = new ConcurrentHashMap<>();

    private LevelCache(String name, SavedData.Factory<T> factory, boolean overworldOnly) {
        this.name = name;
        this.factory = factory;
        this.overworldOnly = overworldOnly;
        ALL.add(this);
    }

    /** One instance per level. */
    public static <T extends SavedData> LevelCache<T> perLevel(String name, SavedData.Factory<T> factory) {
        return new LevelCache<>(name, factory, false);
    }

    /** One server-wide instance, stored on the overworld; use {@link #get(MinecraftServer)}. */
    public static <T extends SavedData> LevelCache<T> overworld(String name, SavedData.Factory<T> factory) {
        return new LevelCache<>(name, factory, true);
    }

    public T get(ServerLevel level) {
        T cached = this.byLevel.get(level);
        return cached != null ? cached : this.resolve(level);
    }

    public T get(MinecraftServer server) {
        return this.get(server.overworld());
    }

    private T resolve(ServerLevel level) {
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("[wflib] LevelCache " + this.name + " not primed for " + level.dimension().location()
                    + "; storage lookup off the server thread");
        }
        return this.byLevel.computeIfAbsent(level, l -> l.getDataStorage().computeIfAbsent(this.factory, this.name));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        for (Class<?> holder : HOLDERS) {
            try {
                Class.forName(holder.getName(), true, holder.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
        boolean overworld = level.dimension() == Level.OVERWORLD;
        for (LevelCache<?> cache : ALL) {
            if (!cache.overworldOnly || overworld) {
                cache.get(level);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            for (LevelCache<?> cache : ALL) {
                cache.byLevel.remove(level);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (LevelCache<?> cache : ALL) {
            cache.byLevel.clear();
        }
    }
}
