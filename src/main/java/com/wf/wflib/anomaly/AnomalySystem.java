package com.wf.wflib.anomaly;

import com.wf.wflib.anomaly.api.AnomalyCaves;
import com.wf.wflib.anomaly.api.AnomalyContent;
import com.wf.wflib.anomaly.api.AnomalySites;
import com.wf.wflib.anomaly.client.AnomalyClientSetup;
import com.wf.wflib.core.api.WFSystem;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import com.wf.wflib.core.api.WFLib;

/** STALKER-style anomalies, their sites and cave fields. Off by default; placed anomalies are lost while off. */
public final class AnomalySystem implements WFSystem {
    private static final Map<String, AnomalyType> TYPES = new LinkedHashMap<>(Map.ofEntries(
            Map.entry("gravity_anomaly", AnomalyType.VORTEX),
            Map.entry("whirligig", AnomalyType.WHIRLIGIG),
            Map.entry("springboard", AnomalyType.SPRINGBOARD),
            Map.entry("electro", AnomalyType.ELECTRO),
            Map.entry("burner", AnomalyType.BURNER),
            Map.entry("acid", AnomalyType.ACID),
            Map.entry("steam", AnomalyType.STEAM),
            Map.entry("flash", AnomalyType.FLASH),
            Map.entry("ghost", AnomalyType.GHOST),
            Map.entry("net", AnomalyType.NET),
            Map.entry("ball_lightning", AnomalyType.BALL_LIGHTNING),
            Map.entry("liquid_gold", AnomalyType.LIQUID_GOLD),
            Map.entry("green_dragon", AnomalyType.GREEN_DRAGON),
            Map.entry("rebounder", AnomalyType.REBOUNDER),
            Map.entry("mephistopheles", AnomalyType.MEPHISTOPHELES),
            Map.entry("burnt_fuzz", AnomalyType.BURNT_FUZZ),
            Map.entry("grabber", AnomalyType.GRABBER),
            Map.entry("comet", AnomalyType.COMET),
            Map.entry("radiation_field", AnomalyType.RADIATION_FIELD),
            Map.entry("thermal_field", AnomalyType.THERMAL_FIELD),
            Map.entry("acid_field", AnomalyType.ACID_FIELD),
            Map.entry("no_gravity", AnomalyType.NO_GRAVITY),
            Map.entry("steam_vent", AnomalyType.STEAM_VENT),
            Map.entry("psy_field", AnomalyType.PSY_FIELD),
            Map.entry("radar", AnomalyType.RADAR),
            Map.entry("darkness", AnomalyType.DARKNESS)));

    @Override
    public String id() {
        return "anomaly";
    }

    @Override
    public boolean defaultEnabled() {
        return false;
    }

    @Override
    public Set<String> requires() {
        return Set.of("artifact", "xray");
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        com.wf.wflib.core.api.CreativeTabs.contribute("anomaly", 1000, out -> com.wf.wflib.anomaly.api.AnomalyContent.ANOMALY_ITEMS.forEach(item -> out.accept(item.get())));
        DeferredRegister<Block> blocks = DeferredRegister.create(Registries.BLOCK, WFLib.MODID);
        DeferredRegister<Item> items = DeferredRegister.create(Registries.ITEM, WFLib.MODID);
        for (DeferredHolder<Block, Block> holder : AnomalyContent.ANOMALIES) {
            String path = holder.getId().getPath();
            AnomalyType type = TYPES.get(path);
            blocks.register(path, () -> new AnomalyBlock(type, BlockBehaviour.Properties.of()
                    .strength(-1.0f, 3600000.8f).noLootTable().noCollission().noOcclusion()
                    .pushReaction(PushReaction.BLOCK).isValidSpawn((state, level, pos, ent) -> false)));
            items.register(path, () -> new BlockItem(holder.get(), new Item.Properties()));
        }
        blocks.register(modBus);
        items.register(modBus);
        DeferredRegister<BlockEntityType<?>> blockEntities =
                DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, WFLib.MODID);
        blockEntities.register("gravity_anomaly", () -> BlockEntityType.Builder.of(AnomalyBlockEntity::new,
                AnomalyContent.ANOMALIES.stream().map(DeferredHolder::get).toArray(Block[]::new)).build(null));
        blockEntities.register(modBus);
        AnomalySoundEvents.register(modBus);
        AnomalySites.register(modBus);
        AnomalyCaves.register(modBus);
        Anomalies.register();
    }

    @Override
    public void registerClient(IEventBus modBus) {
        AnomalyClientSetup.register(modBus);
    }
}
