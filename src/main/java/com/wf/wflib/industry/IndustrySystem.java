package com.wf.wflib.industry;

import com.wf.wflib.core.api.ServerTicks;
import com.wf.wflib.core.api.TickOrder;
import net.minecraft.resources.ResourceLocation;
import com.wf.wflib.industry.api.IndustryClusters;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import com.wf.wflib.core.api.WFSystem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.common.NeoForge;

/** What a block is worth as provocation, and where the bases are. */
public final class IndustrySystem implements WFSystem {
    @Override
    public String id() {
        return "industry";
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        ServerTicks.LEVEL_POST.register(ResourceLocation.fromNamespaceAndPath("wflib", "industry"), TickOrder.INDUSTRY, IndustryClusters::tick);
        ServerTicks.STOPPING.register(ResourceLocation.fromNamespaceAndPath("wflib", "industry"), TickOrder.INDUSTRY_STOP, server -> IndustryClusters.clear());
        container.registerConfig(ModConfig.Type.COMMON, IndustryConfig.SPEC, "wflib-industry.toml");
        modBus.addListener(IndustryConfig::onLoad);
        modBus.addListener(FMLLoadCompleteEvent.class, event -> event.enqueueWork(IndustryValues::rebuild));
        NeoForge.EVENT_BUS.register(IndustryTracker.class);
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, IndustryCommands::onRegisterCommands);
    }
}
