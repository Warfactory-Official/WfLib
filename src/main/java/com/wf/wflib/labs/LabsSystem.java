package com.wf.wflib.labs;

import com.mojang.logging.LogUtils;
import com.wf.wflib.core.api.WFSystem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

import java.nio.file.Path;

/** WFLabs: headless data-mapping runs of the real damage stack; producers registered by the owning systems. */
public final class LabsSystem implements WFSystem {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public String id() {
        return "labs";
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, LabsConfig.SPEC, "wflib-labs.toml");
        modBus.addListener(LabsConfig::onLoad);
        com.wf.wflib.labs.api.LabProducers.register(new ScenarioProducer());
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, LabsCommands::register);
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, LabsSystem::headless);
    }

    private static volatile boolean betweenTicks;

    /** Headless lab running on the server thread outside any tick: {@link com.wf.wflib.labs.api.LabTicks} may tick. */
    public static boolean betweenTicks() {
        return betweenTicks;
    }

    /** {@code -Dwflabs} or {@code -Dwflabs.serve} set. */
    public static boolean headlessRequested() {
        return System.getProperty("wflabs") != null || System.getProperty("wflabs.serve") != null;
    }

    /**
     * {@code -Dwflabs=<ids|all> [-Dwflabs.args="k=v ..."]}: run once the overworld is loaded, then stop.
     * {@code -Dwflabs.serve=<port>}: stay up serving {@link LabServe} on 127.0.0.1 until a shutdown request.
     */
    private static void headless(ServerStartedEvent event) {
        betweenTicks = headlessRequested();
        String serve = System.getProperty("wflabs.serve");
        if (serve != null && !serve.isBlank()) {
            boolean ok = false;
            try {
                LabServe.serve(event.getServer(), Integer.parseInt(serve.trim()));
                ok = true;
            } catch (java.io.IOException | RuntimeException | Error e) {
                LOGGER.error("[labs] serve FAILED", e);
            }
            stop(event.getServer(), ok);
            return;
        }
        String what = System.getProperty("wflabs");
        if (what == null || what.isBlank()) {
            return;
        }
        boolean ok = false;
        try {
            Path out = LabRun.execute(event.getServer(), what, LabRun.parse(System.getProperty("wflabs.args", "")));
            LOGGER.info("[labs] output {}", out);
            ok = true;
        } catch (RuntimeException | Error e) {
            LOGGER.error("[labs] FAILED", e);
        }
        stop(event.getServer(), ok);
    }

    /** A test server has no tracker before its first tick and throws on a normal exit: leave it directly. */
    private static void stop(net.minecraft.server.MinecraftServer server, boolean ok) {
        if (!server.getClass().getName().endsWith("GameTestServer")) {
            server.halt(false);
            if (!ok) {
                Runtime.getRuntime().halt(1);
            }
            return;
        }
        org.apache.logging.log4j.LogManager.shutdown();
        Runtime.getRuntime().halt(ok ? 0 : 1);
    }
}
