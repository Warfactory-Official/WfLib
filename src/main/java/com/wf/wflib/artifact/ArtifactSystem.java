package com.wf.wflib.artifact;

import com.wf.wflib.artifact.api.ArtifactContent;
import com.wf.wflib.artifact.api.Artifact;
import com.wf.wflib.artifact.api.ArtifactItem;
import com.wf.wflib.artifact.api.BoltEntity;
import com.wf.wflib.artifact.api.Bolts;
import com.wf.wflib.artifact.api.DetectorItem;
import com.wf.wflib.artifact.api.DetectorTier;
import com.wf.wflib.artifact.api.ZoneArtifact;
import com.wf.wflib.artifact.api.Artifacts;
import com.wf.wflib.artifact.client.ArtifactClientSetup;
import com.wf.wflib.artifact.detect.BoltItem;
import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.core.api.WFSystems;
import com.wf.wflib.hazard.api.DoseShields;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;
import com.wf.wflib.core.api.WFLib;
import com.wf.wflib.artifact.api.ArtifactSounds;

/** STALKER artefacts, the detectors that find them, and thrown bolts. */
public final class ArtifactSystem implements WFSystem {
    @Override
    public String id() {
        return "artifact";
    }

    @Override
    public Set<String> requires() {
        return Set.of("xray");
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        com.wf.wflib.core.api.CreativeTabs.contribute("artifact", 450, out -> {
            ArtifactContent.ARTIFACTS.values().forEach(a -> out.accept(a.get()));
            out.accept(ArtifactContent.DETECTOR_ECHO.get());
            out.accept(ArtifactContent.DETECTOR_BEAR.get());
            out.accept(ArtifactContent.DETECTOR_VELES.get());
            out.accept(ArtifactContent.BOLT_ITEM.get());
        });
        DeferredRegister<Item> items = DeferredRegister.create(Registries.ITEM, WFLib.MODID);
        for (Artifact a : Artifact.ALL) {
            items.register(a.id(), () -> new ArtifactItem(a, new Item.Properties().stacksTo(1)));
        }
        items.register("detector_echo", () -> new DetectorItem(DetectorTier.ECHO, new Item.Properties().stacksTo(1)));
        items.register("detector_bear", () -> new DetectorItem(DetectorTier.BEAR, new Item.Properties().stacksTo(1)));
        items.register("detector_veles", () -> new DetectorItem(DetectorTier.VELES, new Item.Properties().stacksTo(1)));
        items.register("bolt", () -> new BoltItem(new Item.Properties()));
        items.register(modBus);
        DeferredRegister<EntityType<?>> entities = DeferredRegister.create(Registries.ENTITY_TYPE, WFLib.MODID);
        entities.register("zone_artifact", () -> EntityType.Builder.<ZoneArtifact>of(ZoneArtifact::new, MobCategory.MISC)
                .sized(0.3F, 0.3F).clientTrackingRange(4).updateInterval(2).build("zone_artifact"));
        entities.register("bolt", () -> EntityType.Builder.<BoltEntity>of(BoltEntity::new, MobCategory.MISC)
                .sized(0.15F, 0.15F).clientTrackingRange(4).updateInterval(1).build("bolt"));
        entities.register(modBus);
        ArtifactSounds.register(modBus);
        Artifacts.register();
        Bolts.register();
        if (WFSystems.enabled("hazard")) {
            DoseShields.SHIELDS.register(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "artifact"), 0,
                    Artifacts::shieldDose);
        }
    }

    @Override
    public void registerClient(IEventBus modBus) {
        ArtifactClientSetup.register(modBus);
    }
}
