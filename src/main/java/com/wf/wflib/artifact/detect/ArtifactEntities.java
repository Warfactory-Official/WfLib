package com.wf.wflib.artifact.detect;

import com.wf.wflib.WFLib;
import com.wf.wflib.item.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Artefacts in the world and what finds them: the hidden artefact, detectors, bolts. */
public final class ArtifactEntities {
    private ArtifactEntities() {
    }

    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE,
            WFLib.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<ZoneArtifact>> ZONE_ARTIFACT = TYPES.register(
            "zone_artifact", () -> EntityType.Builder.<ZoneArtifact>of(ZoneArtifact::new, MobCategory.MISC)
                    .sized(0.45F, 0.45F).clientTrackingRange(4).updateInterval(20).build("zone_artifact"));
    public static final DeferredHolder<EntityType<?>, EntityType<BoltEntity>> BOLT = TYPES.register("bolt",
            () -> EntityType.Builder.<BoltEntity>of(BoltEntity::new, MobCategory.MISC)
                    .sized(0.15F, 0.15F).clientTrackingRange(4).updateInterval(2).build("bolt"));

    public static final DeferredHolder<Item, Item> DETECTOR_ECHO = ModItems.ITEMS.register("detector_echo",
            () -> new DetectorItem(DetectorTier.ECHO, new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, Item> DETECTOR_BEAR = ModItems.ITEMS.register("detector_bear",
            () -> new DetectorItem(DetectorTier.BEAR, new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, Item> DETECTOR_VELES = ModItems.ITEMS.register("detector_veles",
            () -> new DetectorItem(DetectorTier.VELES, new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, Item> BOLT_ITEM = ModItems.ITEMS.register("bolt",
            () -> new BoltItem(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
