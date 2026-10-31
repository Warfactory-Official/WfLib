package com.wf.wflib.door;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import com.wf.wflib.door.api.DoorBlockEntity;
import com.wf.wflib.door.api.DoorType;
import com.wf.wflib.core.api.WFLib;

/** Holders registered by {@code DoorSystem}: fifteen blocks and items, the one block entity type, lock and key. */
public final class DoorContent {

    public static final Map<DoorType, DeferredHolder<Block, DoorBlock>> DOORS = new EnumMap<>(DoorType.class);
    public static final Map<DoorType, DeferredHolder<Item, DoorItem>> DOOR_ITEMS = new EnumMap<>(DoorType.class);

    static {
        for (DoorType type : DoorType.values()) {
            DOORS.put(type, DeferredHolder.create(Registries.BLOCK, id(type)));
            DOOR_ITEMS.put(type, DeferredHolder.create(Registries.ITEM, id(type)));
        }
    }

    /** One block entity type for every door, because they differ by table entry rather than by class. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DoorBlockEntity>> DOOR =
            DeferredHolder.create(Registries.BLOCK_ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "door"));

    public static final DeferredHolder<Item, DoorKeyItem> DOOR_KEY =
            DeferredHolder.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "door_key"));

    public static final DeferredHolder<Item, DoorLockItem> DOOR_LOCK =
            DeferredHolder.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "door_lock"));

    private DoorContent() {
    }

    /** Vault and secure doors are meant to resist being cut through; the rest are ordinary heavy metal. */
    static float hardness(DoorType type) {
        return switch (type) {
            case SECURE_ACCESS_DOOR -> 20.0f;
            case WATER_DOOR, CARGO_DOOR -> 5.0f;
            default -> 10.0f;
        };
    }

    public static ResourceLocation id(DoorType type) {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, type.id());
    }

    /** Every door in the creative tab, one entry per skin it can wear. */
    public static List<ItemStack> creativeStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        for (DoorType type : DoorType.values()) {
            DoorItem item = DOOR_ITEMS.get(type).get();
            for (int skin = 0; skin < Math.max(1, type.skins()); skin++) {
                stacks.add(DoorItem.withSkin(item, skin));
            }
        }
        return stacks;
    }
}
