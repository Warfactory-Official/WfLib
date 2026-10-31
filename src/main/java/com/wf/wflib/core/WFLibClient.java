package com.wf.wflib.core;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import com.wf.wflib.mist.api.WFFluids;
import com.wf.wflib.core.api.WFLib;


@Mod(value = WFLib.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = WFLib.MODID, value = Dist.CLIENT)
public class WFLibClient {
    private static final Logger LOGGER = LogUtils.getLogger();

    public WFLibClient(IEventBus modEventBus) {
        com.wf.wflib.core.api.WFSystems.startClient(WFLib.MODID, modEventBus);
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
    }

    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event) {

        event.enqueueWork(() -> {
        });
    }

    private static final ResourceLocation WATER_STILL = ResourceLocation.withDefaultNamespace("block/water_still");
    private static final ResourceLocation WATER_FLOW = ResourceLocation.withDefaultNamespace("block/water_flow");

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        for (FluidType type : NeoForgeRegistries.FLUID_TYPES) {
            int tint = switch (type) {
                case WFFluids.GasFluidType gas -> gas.tint();
                case WFFluids.KeroseneFluidType ignored -> WFFluids.KeroseneFluidType.TINT;
                default -> -1;
            };
            if (tint >= 0 && !event.isFluidTypeRegistered(type)) {
                event.registerFluidType(waterTinted(tint), type);
            }
        }
    }

    private static IClientFluidTypeExtensions waterTinted(int rgb) {
        return new IClientFluidTypeExtensions() {
            @Override
            public int getTintColor() {
                return 0xFF000000 | rgb;
            }

            @Override
            public ResourceLocation getStillTexture() {
                return WATER_STILL;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return WATER_FLOW;
            }
        };
    }
}