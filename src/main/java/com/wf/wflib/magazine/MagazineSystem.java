package com.wf.wflib.magazine;

import com.wf.wflib.core.api.WFLibContent;
import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.magazine.api.MagContents;
import com.wf.wflib.magazine.api.MagazineItems;
import com.wf.wflib.magazine.api.PodTubes;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;
import com.wf.wflib.core.api.WFLib;

/** Holders named through {@link MagazineItems}. */
public final class MagazineSystem implements WFSystem {
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, WFLib.MODID);

    @Override
    public String id() {
        return "magazine";
    }

    @Override
    public java.util.Set<String> requires() {
        return java.util.Set.of("ammo");
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        com.wf.wflib.core.api.CreativeTabs.contribute("magazine", 280, out -> com.wf.wflib.magazine.api.MagazineItems.creativeStacks().forEach(out::accept));
        COMPONENTS.register("mag_contents", () -> DataComponentType.<MagContents>builder()
                .persistent(MagContents.CODEC).networkSynchronized(MagContents.STREAM_CODEC).cacheEncoding().build());
        COMPONENTS.register("pod_tubes", () -> DataComponentType.<PodTubes>builder()
                .persistent(PodTubes.CODEC).networkSynchronized(PodTubes.STREAM_CODEC).cacheEncoding().build());
        COMPONENTS.register(modBus);
        MagazineCatalogue.define();
        modBus.addListener(MagazineSystem::onRegister);
    }

    private static void onRegister(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.ITEM)) {
            return;
        }
        WFLibContent.freeze();
        event.register(Registries.ITEM, helper -> MagazineItems.live().forEach(id ->
                helper.register(id, MagazineCatalogue.def(id).factory().get())));
    }
}
