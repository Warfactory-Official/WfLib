package com.wf.wflib.mist;

import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.mist.api.WFFluids;
import com.wf.wflib.mist.api.MistEffects;
import com.wf.wflib.mist.api.MistEntity;
import com.wf.wflib.mist.client.MistClient;
import com.wf.wflib.mist.debug.MistDebug;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.wf.wflib.core.api.WFLib;

public final class MistSystem implements WFSystem {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, WFLib.MODID);

    /** Stationary, fluid-imbued area entity. */
    public static final DeferredHolder<EntityType<?>, EntityType<MistEntity>> MIST =
            ENTITY_TYPES.register("mist", () -> EntityType.Builder.of(MistEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .clientTrackingRange(10)
                    .updateInterval(Integer.MAX_VALUE)
                    .noSummon()
                    .build("mist")
            );

    @Override
    public String id() {
        return "mist";
    }

    @Override
    public boolean mandatory() {
        return true;
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        com.wf.wflib.core.api.CreativeTabs.contribute("mist", 650, out -> out.accept(com.wf.wflib.mist.api.WFFluids.KEROSENE_BUCKET.get()));
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, com.wf.wflib.mist.client.MistClientConfig.SPEC, "wflib-mist-client.toml");
        ENTITY_TYPES.register(modBus);
        WFFluids.register(modBus);
        MistEffects.register(WFFluids.PHOSGENE.getId(), new PhosgeneMistEffect());
        MistEffects.register(WFFluids.MUSTARD_GAS.getId(), new MustardGasMistEffect());
        MistEffects.register(WFFluids.TEAR_GAS.getId(), new TearGasMistEffect());
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, MistDebug::onRegisterCommands);
    }

    @Override
    public void registerClient(IEventBus modBus) {
        MistClient.register(modBus);
    }
}
