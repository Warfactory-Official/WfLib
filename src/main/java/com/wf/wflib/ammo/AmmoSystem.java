package com.wf.wflib.ammo;

import com.wf.wflib.ammo.api.Ammo;
import com.wf.wflib.ammo.api.AmmoCodecs;
import com.wf.wflib.ammo.api.AmmoItems;
import com.wf.wflib.ammo.api.CartridgeItem;
import com.wf.wflib.ammo.client.AmmoClient;
import com.wf.wflib.core.api.WFLibContent;
import com.wf.wflib.core.api.WFSystem;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import com.wf.wflib.core.api.WFLib;

/** Holders named through {@link AmmoItems}. */
public final class AmmoSystem implements WFSystem {
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, WFLib.MODID);

    @Override
    public String id() {
        return "ammo";
    }

    @Override
    public boolean mandatory() {
        return true;
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        com.wf.wflib.core.api.CreativeTabs.contribute("ammo", 290, out -> com.wf.wflib.ammo.api.Ammo.variants().stream()
                .filter(v -> v.id().getNamespace().equals(com.wf.wflib.core.api.WFLib.MODID))
                .forEach(v -> out.accept(com.wf.wflib.ammo.api.AmmoItems.stack(v, 1))));
        COMPONENTS.register("ammo_variant", () -> DataComponentType.<ResourceLocation>builder()
                .persistent(AmmoCodecs.VARIANT)
                .networkSynchronized(ResourceLocation.STREAM_CODEC)
                .cacheEncoding()
                .build());
        COMPONENTS.register(modBus);
        AmmoCatalogue.define();
        modBus.addListener(AmmoSystem::onRegister);
    }

    @Override
    public void registerClient(IEventBus modBus) {
        AmmoClient.register(modBus);
    }

    private static void onRegister(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.ITEM)) {
            return;
        }
        WFLibContent.freeze();
        Map<ResourceLocation, CartridgeItem> out = new LinkedHashMap<>();
        event.register(Registries.ITEM, helper -> Ammo.calibres().forEach(c -> {
            CartridgeItem item = new CartridgeItem(c, new Item.Properties().stacksTo(c.stackSize())
                    .component(AmmoItems.VARIANT.get(), Ammo.defaultVariant(c.id()).id()));
            helper.register(c.itemId(), item);
            out.put(c.id(), item);
        }));
        AmmoItems.setItems(out);
    }
}
