package com.wf.wflib.nvg;

import com.wf.wflib.core.api.WFLibContent;
import com.wf.wflib.core.api.WFSystem;
import com.wf.wflib.nvg.api.NvgComponents;
import com.wf.wflib.nvg.api.NvgContent;
import com.wf.wflib.nvg.api.NvgDismountRecipe;
import com.wf.wflib.nvg.api.client.NvgClient;
import com.wf.wflib.nvg.api.client.NvgHand;
import com.wf.wflib.nvg.api.client.NvgIllumination;
import com.wf.wflib.nvg.api.client.NvgItemClient;
import com.wf.wflib.nvg.client.NvgKeys;
import com.wf.wflib.nvg.client.NvgLights;
import com.wf.wflib.nvg.client.NvgOptics;
import com.wf.wflib.nvg.client.NvgView;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.Set;
import com.wf.wflib.core.api.WFLib;

/** Night-vision goggles; holders named through {@link NvgContent}. */
public final class NvgSystem implements WFSystem {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, WFLib.MODID);

    static {
        SERIALIZERS.register("nvg_dismount", () -> new SimpleCraftingRecipeSerializer<>(NvgDismountRecipe::new));
    }

    @Override
    public String id() {
        return "nvg";
    }

    @Override
    public Set<String> requires() {
        return Set.of("armor");
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        com.wf.wflib.core.api.CreativeTabs.contribute("nvg", 230, out -> com.wf.wflib.nvg.api.NvgContent.live().forEach(id ->
                out.accept(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id))));
        NvgComponents.register(modBus);
        SERIALIZERS.register(modBus);
        modBus.addListener(NvgSystem::onRegister);
        modBus.addListener(RegisterPayloadHandlersEvent.class, event -> event.registrar("nvg-1")
                .playToServer(NvgActionPacket.TYPE, NvgActionPacket.STREAM_CODEC, (pkt, ctx) -> pkt.handle(ctx)));
        NeoForge.EVENT_BUS.register(NvgServer.class);
    }

    private static void onRegister(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.ITEM)) {
            return;
        }
        WFLibContent.freeze();
        event.register(Registries.ITEM, helper -> NvgContent.live().forEach(id ->
                helper.register(id, NvgCatalogue.factory(id).get())));
    }

    @Override
    public void registerClient(IEventBus modBus) {
        modBus.register(NvgKeys.class);
        NeoForge.EVENT_BUS.register(NvgClient.class);
        NeoForge.EVENT_BUS.register(NvgHand.class);
        NeoForge.EVENT_BUS.register(NvgView.class);
        modBus.addListener(RegisterClientReloadListenersEvent.class, event ->
                event.registerReloadListener(new NvgOptics.Loader()));
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(() -> {
            NvgIllumination.register();
            NvgLights.register();
        }));
        modBus.addListener(RegisterClientExtensionsEvent.class, event -> {
            for (Item item : BuiltInRegistries.ITEM) {
                if (item instanceof com.wf.wflib.nvg.api.NvgItem && !event.isItemRegistered(item)) {
                    event.registerItem(NvgItemClient.INSTANCE, item);
                }
            }
        });
    }
}
