package com.wf.wflib.demolition;

import net.neoforged.neoforge.event.RegisterCommandsEvent;
import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.demolition.api.DemolitionContent;
import com.wf.wflib.demolition.api.DetonatorItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.UUID;
import com.wf.wflib.core.api.WFLib;

/** Mining charges and the detonator that fires them and other detonatables. */
public final class DemolitionSystem implements WFSystem {
    @Override
    public String id() {
        return "demolition";
    }

    @Override
    public void registerClient(IEventBus modBus) {
        NeoForge.EVENT_BUS.register(com.wf.wflib.demolition.client.ExplosiveOverlayRenderer.class);
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        com.wf.wflib.core.api.CreativeTabs.contribute("demolition", 370, out -> {
            out.accept(DemolitionContent.MINING_CHARGE_ITEM.get());
            out.accept(DemolitionContent.DEEP_MINING_CHARGE_ITEM.get());
            out.accept(DemolitionContent.DETONATOR.get());
        });
        DeferredRegister<Block> blocks = DeferredRegister.create(Registries.BLOCK, WFLib.MODID);
        blocks.register("mining_charge", () -> new MiningChargeBlock(
                BlockBehaviour.Properties.of().mapColor(MapColor.FIRE).strength(0.8f).sound(SoundType.METAL)
                        .isValidSpawn((state, level, pos, ent) -> false), 3, 2, 1));
        blocks.register("deep_mining_charge", () -> new MiningChargeBlock(
                BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(0.8f).sound(SoundType.METAL)
                        .isValidSpawn((state, level, pos, ent) -> false), 3, 2, 2));
        blocks.register(modBus);

        DeferredRegister<Item> items = DeferredRegister.create(Registries.ITEM, WFLib.MODID);
        items.register("mining_charge",
                () -> new BlockItem(DemolitionContent.MINING_CHARGE.get(), new Item.Properties()));
        items.register("deep_mining_charge",
                () -> new BlockItem(DemolitionContent.DEEP_MINING_CHARGE.get(), new Item.Properties()));
        items.register("detonator", () -> new DetonatorItem(new Item.Properties().stacksTo(1)));
        items.register(modBus);

        DeferredRegister<BlockEntityType<?>> blockEntities =
                DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, WFLib.MODID);
        blockEntities.register("mining_charge", () -> BlockEntityType.Builder.of(MiningChargeBlockEntity::new,
                DemolitionContent.MINING_CHARGE.get(), DemolitionContent.DEEP_MINING_CHARGE.get()).build(null));
        blockEntities.register(modBus);

        DeferredRegister<DataComponentType<?>> components =
                DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, WFLib.MODID);
        components.register("detonator_charges", () -> DataComponentType.<List<BlockPos>>builder()
                .persistent(BlockPos.CODEC.listOf())
                .networkSynchronized(BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()))
                .build());
        components.register("detonator_mines", () -> DataComponentType.<List<UUID>>builder()
                .persistent(UUIDUtil.CODEC.listOf())
                .networkSynchronized(UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list()))
                .build());
        components.register(modBus);

        DemolitionSounds.register(modBus);
        NeoForge.EVENT_BUS.register(DetonatorInteractions.class);
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, DemolitionCommands::onRegisterCommands);
    }
}
