package com.wf.wflib.core.api;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.function.Consumer;

/**
 * Cross-system step lists; each system registers its own steps in {@code register}, so an off system contributes
 * nothing. Descending priority, constants in {@link TickOrder}.
 */
public final class ServerTicks {

    @FunctionalInterface
    public interface LevelTick {
        void tick(ServerLevel level);
    }

    public static final OrderedRegistry<LevelTick> LEVEL_PRE = OrderedRegistry.create("server ticks (level pre)");
    public static final OrderedRegistry<LevelTick> LEVEL_POST = OrderedRegistry.create("server ticks (level post)");
    public static final OrderedRegistry<Consumer<MinecraftServer>> STARTING =
            OrderedRegistry.create("server starting steps");
    public static final OrderedRegistry<Consumer<MinecraftServer>> STOPPING =
            OrderedRegistry.create("server stopping steps");

    private ServerTicks() {
    }

    /** Game-bus listener class; subscribed once by the composition root. */
    public static final class Listeners {
        private Listeners() {
        }

        @SubscribeEvent
        public static void onLevelTickPre(LevelTickEvent.Pre event) {
            if (event.getLevel() instanceof ServerLevel level) {
                for (LevelTick step : LEVEL_PRE.values()) {
                    step.tick(level);
                }
            }
        }

        @SubscribeEvent
        public static void onLevelTickPost(LevelTickEvent.Post event) {
            if (event.getLevel() instanceof ServerLevel level) {
                for (LevelTick step : LEVEL_POST.values()) {
                    step.tick(level);
                }
            }
        }

        @SubscribeEvent
        public static void onServerStarting(ServerStartingEvent event) {
            for (Consumer<MinecraftServer> step : STARTING.values()) {
                step.accept(event.getServer());
            }
        }

        @SubscribeEvent
        public static void onServerStopping(ServerStoppingEvent event) {
            for (Consumer<MinecraftServer> step : STOPPING.values()) {
                step.accept(event.getServer());
            }
        }
    }
}
