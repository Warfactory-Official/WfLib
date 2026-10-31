package com.wf.wflib.missile.client;

import com.wf.wflib.missile.api.MissileModels;
import com.wf.wflib.missile.LaunchConfig;
import com.wf.wflib.missile.MissileDispenserBlockEntity;
import com.wf.wflib.probe.api.ProbeCapabilities;
import com.wf.wflib.probe.api.ProbeElement;
import com.wf.wflib.probe.api.ProbeProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.util.List;
import com.wf.wflib.missile.MissileSystem;
import com.wf.wflib.core.api.WFLib;

/** Probe panel of the missile dispenser. */
@EventBusSubscriber(modid = WFLib.MODID, value = Dist.CLIENT)
public final class MissileProbe {

    private MissileProbe() {
    }

    @SubscribeEvent
    static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        if (!com.wf.wflib.core.api.WFSystems.enabled("probe")) {
            return;
        }
        event.registerBlockEntity(ProbeCapabilities.BLOCK, MissileSystem.MISSILE_DISPENSER_BE.get(),
                (dispenser, context) -> DISPENSER);
    }

    private static final ProbeProvider.Blocks DISPENSER = (info, ctx, state, pos, blockEntity) -> {
            if (!(blockEntity instanceof MissileDispenserBlockEntity dispenser)) {
                return;
            }
            LaunchConfig config = dispenser.getConfig();
            if (config == null) {
                info.text(Component.translatable("probe.wflib.dispenser.empty"),
                        ChatFormatting.DARK_GRAY);
                return;
            }
            ResourceLocation model = MissileModels.model(config.modelId);
            info.add(new ProbeElement.Row(List.of(
                    ProbeElement.Model.spinning(model, 40),
                    new ProbeElement.Column(List.of(
                            ProbeElement.Text.of(Component.translatable(
                                    "probe.wflib.dispenser.loaded",
                                    Component.literal(config.modelId.getPath())), info.accent()),
                            ProbeElement.Text.of(Component.translatable(
                                    "probe.wflib.dispenser.warhead",
                                    Component.literal(config.warheadId.getPath())), 0xFFAAAAAA),
                            ProbeElement.Text.of(Component.translatable(
                                    "probe.wflib.dispenser.target",
                                    (int) config.targetX, (int) config.targetY, (int) config.targetZ),
                                    0xFFAAAAAA)), 2)), 6, ProbeElement.Align.CENTRE));
    };
}
