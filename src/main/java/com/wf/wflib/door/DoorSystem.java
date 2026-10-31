package com.wf.wflib.door;

import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.door.api.DoorBlockEntity;
import com.wf.wflib.door.api.DoorSounds;
import com.wf.wflib.door.api.DoorType;
import com.wf.wflib.door.client.DoorClient;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;
import com.wf.wflib.core.api.WFLib;

/** The NTM door roster: one block and item per {@link DoorType}, shared block entity, lock and key. */
public final class DoorSystem implements WFSystem {
    @Override
    public String id() {
        return "door";
    }

    @Override
    public Set<String> requires() {
        return Set.of("probe");
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        com.wf.wflib.core.api.CreativeTabs.contribute("door", 360, out -> {
            DoorContent.creativeStacks().forEach(out::accept);
            out.accept(DoorContent.DOOR_LOCK.get());
            out.accept(DoorContent.DOOR_KEY.get());
        });
        DeferredRegister<Block> blocks = DeferredRegister.create(Registries.BLOCK, WFLib.MODID);
        DeferredRegister<Item> items = DeferredRegister.create(Registries.ITEM, WFLib.MODID);
        for (DoorType type : DoorType.values()) {
            blocks.register(type.id(), () -> new DoorBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .sound(SoundType.NETHERITE_BLOCK)
                            .strength(DoorContent.hardness(type), 1_000.0f)
                            .requiresCorrectToolForDrops()
                            .noOcclusion()
                            .dynamicShape()
                            .pushReaction(PushReaction.BLOCK)
                            .noLootTable(),
                    type));
            items.register(type.id(), () -> new DoorItem(DoorContent.DOORS.get(type).get(), new Item.Properties()));
        }
        items.register("door_key", () -> new DoorKeyItem(new Item.Properties().stacksTo(1)));
        items.register("door_lock", () -> new DoorLockItem(new Item.Properties().stacksTo(1), 0.1));
        blocks.register(modBus);
        items.register(modBus);
        DeferredRegister<BlockEntityType<?>> blockEntities =
                DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, WFLib.MODID);
        blockEntities.register("door", () -> {
            Block[] doors = DoorContent.DOORS.values().stream().map(h -> (Block) h.get()).toArray(Block[]::new);
            return BlockEntityType.Builder.of(DoorBlockEntity::new, doors).build(null);
        });
        blockEntities.register(modBus);
        DoorComponents.register(modBus);
        DoorSounds.register(modBus);
        DoorProbeActions.register();
        modBus.addListener(RegisterCapabilitiesEvent.class, DoorProbeData::onRegisterCapabilities);
        NeoForge.EVENT_BUS.register(DoorCommands.class);
    }

    @Override
    public void registerClient(IEventBus modBus) {
        DoorClient.register(modBus);
    }
}
