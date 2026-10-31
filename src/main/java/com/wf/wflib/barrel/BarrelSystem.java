package com.wf.wflib.barrel;

import com.wf.wflib.barrel.api.Barrels;
import com.wf.wflib.barrel.api.SpareBarrel;
import com.wf.wflib.barrel.api.SpareBarrelItem;
import com.wf.wflib.barrel.client.BarrelClient;
import com.wf.wflib.core.api.WFLibContent;
import com.wf.wflib.core.api.WFSystem;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;
import com.wf.wflib.core.api.WFLib;

/** Holders named through {@link Barrels}; the item is registered iff requested ({@link WFLibContent.Kind#BARREL}). */
public final class BarrelSystem implements WFSystem {
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, WFLib.MODID);

    @Override
    public String id() {
        return "barrel";
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        com.wf.wflib.core.api.CreativeTabs.contribute("barrel", 270, out -> com.wf.wflib.barrel.api.Barrels.creativeStacks().forEach(out::accept));
        COMPONENTS.register("spare_barrel", () -> DataComponentType.<SpareBarrel>builder()
                .persistent(SpareBarrel.CODEC).networkSynchronized(SpareBarrel.STREAM_CODEC).cacheEncoding().build());
        COMPONENTS.register(modBus);
        modBus.addListener(BarrelSystem::onRegister);
    }

    @Override
    public void registerClient(IEventBus modBus) {
        BarrelClient.register(modBus);
    }

    private static void onRegister(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.ITEM)) {
            return;
        }
        WFLibContent.freeze();
        if (Barrels.live()) {
            event.register(Registries.ITEM, helper ->
                    helper.register(Barrels.SPARE_ID, new SpareBarrelItem(new Item.Properties())));
        }
    }
}
