package com.wf.wflib.compat;

import com.wf.wflib.compat.gt.gametest.GregtechEMPGameTest;
import com.wf.wflib.compat.wfweight.ArtifactWeight;
import com.wf.wflib.compat.wfweight.BackpackWeight;
import com.wf.wflib.compat.wfweight.CarryWeight;
import com.wf.wflib.compat.wfweight.ItemWeights;
import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.core.api.WFSystems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import com.wf.wflib.compat.api.GregtechCompat;

/**
 * Faction and territory facade (inert without WarForge) and the bridges to optional mods. A bridge is wired only
 * when its mod is loaded and the system it reads is on.
 */
public final class CompatSystem implements WFSystem {
    @Override
    public String id() {
        return "compat";
    }

    @Override
    public boolean mandatory() {
        return true;
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, CompatConfig.SPEC, "wflib-compat.toml");
        modBus.addListener(CompatConfig::onLoad);
        if (GregtechCompat.isActive()) {
            modBus.addListener(GregtechEMPGameTest::register);
        }
        // After every constructor: a dependent's require turns systems on late.
        modBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(() -> {
            if (!ModList.get().isLoaded("wfweight")) {
                return;
            }
            ItemWeights.register();
            if (WFSystems.enabled("artifact")) {
                ArtifactWeight.register();
            }
            if (WFSystems.enabled("backpack")) {
                BackpackWeight.register();
            }
            if (WFSystems.enabled("carry")) {
                CarryWeight.register();
            }
        }));
    }
}
