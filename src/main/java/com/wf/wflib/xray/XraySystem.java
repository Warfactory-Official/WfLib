package com.wf.wflib.xray;

import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.xray.api.XrScreen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only screen and camera effects; the particle player lives in {@code particle}. */
public final class XraySystem implements WFSystem {
    private static volatile boolean active;

    /** Mixin guard: true once this system registered. */
    public static boolean active() {
        return active;
    }

    @Override
    public String id() {
        return "xray";
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        active = true;
        container.registerConfig(ModConfig.Type.CLIENT, XrClientConfig.SPEC, "wflib-xray-client.toml");
    }

    @Override
    public void registerClient(IEventBus modBus) {
        NeoForge.EVENT_BUS.register(XrScreen.class);
    }
}
