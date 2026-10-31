package com.wf.wflib.build;

import com.wf.wflib.core.api.ServerTicks;
import com.wf.wflib.core.api.TickOrder;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import com.wf.wflib.build.api.BlueprintFormats;
import com.wf.wflib.core.api.WFSystem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

import java.util.Set;

/** Blueprint reading, construction and salvage plans, and the drone build pilot. */
public final class BuildSystem implements WFSystem {
    @Override
    public String id() {
        return "build";
    }

    @Override
    public Set<String> requires() {
        return Set.of("drone", "work");
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        ServerTicks.LEVEL_POST.register(ResourceLocation.fromNamespaceAndPath("wflib", "build"), TickOrder.BUILD, BuildManager::tick);
        BlueprintFormats.register(LitematicaFormat.INSTANCE);
        BlueprintFormats.register(StructureNbtFormat.INSTANCE);
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, BuildCommands::onRegisterCommands);
    }
}
