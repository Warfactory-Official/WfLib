package com.wf.wflib.warhead;

import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.warhead.api.BombletEntity;
import com.wf.wflib.warhead.api.BombletWarhead;
import com.wf.wflib.warhead.api.EMPWarhead;
import com.wf.wflib.warhead.api.Warheads;
import com.wf.wflib.warhead.client.WarheadClient;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.warhead.api.AgentOrangeWarhead;
import com.wf.wflib.warhead.api.FireWarhead;
import com.wf.wflib.warhead.api.GasWarhead;
import com.wf.wflib.warhead.api.RecursiveFrag;
import com.wf.wflib.warhead.api.TearGasWarhead;
import com.wf.wflib.warhead.api.TorpedoWarhead;

import static com.wf.wflib.warhead.api.Warheads.rl;

public final class WarheadSystem implements WFSystem {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, WFLib.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<BombletEntity>> BOMBLET =
            ENTITY_TYPES.register("bomblet", () -> EntityType.Builder.<BombletEntity>of(BombletEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .fireImmune()
                    .build("bomblet")
            );

    public static final DeferredHolder<EntityType<?>, EntityType<AgentOrangeEntity>> AGENT_ORANGE =
            ENTITY_TYPES.register("agent_orange", () -> EntityType.Builder.<AgentOrangeEntity>of(AgentOrangeEntity::new,
                            MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .noSummon()
                    .build("agent_orange")
            );

    @Override
    public String id() {
        return "warhead";
    }

    @Override
    public boolean mandatory() {
        return true;
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        ENTITY_TYPES.register(modBus);
        modBus.addListener(WarheadConfig::onLoad);
        container.registerConfig(ModConfig.Type.COMMON, WarheadConfig.SPEC, "wflib-warhead.toml");
        defineCatalogue();
    }

    @Override
    public void registerClient(IEventBus modBus) {
        WarheadClient.register(modBus);
    }

    private static void defineCatalogue() {
        ResourceLocation standard = Warheads.defaultId();
        Catalogue.define(standard, Warheads.STANDARD, Warheads.STANDARD_INTERCEPT);
        Catalogue.define(rl("mininuke"), Warheads.MININUKE);
        Catalogue.define(rl("fragmentation"), Warheads.FRAGMENTATION);
        Catalogue.define(rl("shaped_charge"), Warheads.SHAPED_CHARGE);
        Catalogue.define(RecursiveFrag.ID, RecursiveFrag::detonate);
        Catalogue.define(GasWarhead.ID, GasWarhead::detonate);
        Catalogue.define(TearGasWarhead.ID, TearGasWarhead::detonate);
        Catalogue.define(FireWarhead.ID, FireWarhead::detonate);
        Catalogue.define(ThermobaricWarhead.ID, ThermobaricWarhead::detonate);
        Catalogue.define(AgentOrangeWarhead.ID, AgentOrangeWarhead::detonate);
        Catalogue.define(TorpedoWarhead.ID, TorpedoWarhead::detonate);
        Catalogue.define(FireCluster.ID, FireCluster::detonate);
        Catalogue.define(rl("interceptor"), Warheads.INTERCEPTOR, Warheads.INTERCEPTOR::detonate);
        Catalogue.define(rl("inert"), Warheads.INERT);
        Catalogue.define(rl("emp"), EMPWarhead::detonate);
        Catalogue.define(BombletWarhead.ID, BombletWarhead.STANDARD);
        Catalogue.define(BombletWarhead.FIRE_ID, BombletWarhead.FIRE);
        Catalogue.child(rl("fragmentation"), BombletWarhead.ID);
        Catalogue.child(FireCluster.ID, BombletWarhead.FIRE_ID);

        Catalogue.blast(standard, Warheads.STANDARD_BLAST_SIZE);
        Catalogue.blast(FireWarhead.ID, 8F);
        Catalogue.blast(ThermobaricWarhead.ID, ThermobaricWarhead.SIZE);
        Catalogue.blast(AgentOrangeWarhead.ID, AgentOrangeWarhead.RADIUS);
        Catalogue.blast(rl("shaped_charge"), Warheads.SHAPED_CHARGE_SIZE);
    }
}
