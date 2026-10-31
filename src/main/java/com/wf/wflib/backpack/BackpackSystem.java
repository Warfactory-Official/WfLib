package com.wf.wflib.backpack;

import com.mojang.serialization.Codec;
import com.wf.wflib.backpack.api.BackpackMenu;
import com.wf.wflib.backpack.api.Backpacks;
import com.wf.wflib.backpack.client.BackpackClient;
import com.wf.wflib.core.api.WFSystem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import com.wf.wflib.core.api.WFLib;

/** Worn backpack: slots in the player inventory menu, probe-opened by others. Packs come from dependents ({@code BackpackItem}). */
public final class BackpackSystem implements WFSystem {
    private static volatile boolean active;

    /** Mixin guard: true once this system registered. */
    public static boolean active() {
        return active;
    }

    @Override
    public String id() {
        return "backpack";
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        active = true;
        DeferredRegister<AttachmentType<?>> attachments =
                DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, WFLib.MODID);
        DeferredRegister.DataComponents components =
                DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, WFLib.MODID);
        DeferredRegister<MenuType<?>> menus = DeferredRegister.create(Registries.MENU, WFLib.MODID);
        attachments.register("worn_backpack", () -> AttachmentType.builder(() -> ItemStack.EMPTY)
                .serialize(ItemStack.OPTIONAL_CODEC).copyOnDeath().build());
        components.<ItemContainerContents>registerComponentType("backpack_contents", b -> b.persistent(ItemContainerContents.CODEC)
                .networkSynchronized(ItemContainerContents.STREAM_CODEC));
        components.<Boolean>registerComponentType("backpack_locked", b -> b.persistent(Codec.BOOL)
                .networkSynchronized(ByteBufCodecs.BOOL));
        menus.register("backpack", () -> IMenuTypeExtension.create(BackpackMenu::client));
        attachments.register(modBus);
        components.register(modBus);
        menus.register(modBus);

        modBus.addListener(RegisterPayloadHandlersEvent.class, event -> {
            var r = event.registrar("backpack-1");
            r.playToServer(BackpackPackets.Action.TYPE, BackpackPackets.Action.STREAM_CODEC, BackpackPackets.Action::handle);
            r.playToClient(BackpackPackets.Worn.TYPE, BackpackPackets.Worn.STREAM_CODEC, BackpackPackets.Worn::handle);
        });
        modBus.addListener((RegisterCapabilitiesEvent e) -> e.registerEntity(Backpacks.HANDLER, EntityType.PLAYER,
                (player, ctx) -> Backpacks.worn(player).isEmpty() ? null : new BackpackHandler(Backpacks.container(player))));
        NeoForge.EVENT_BUS.addListener(BackpackServer::onDrops);
        NeoForge.EVENT_BUS.addListener(BackpackServer::onStartTracking);
        NeoForge.EVENT_BUS.addListener(BackpackServer::onCommands);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> Backpacks.sync(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> Backpacks.sync(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> Backpacks.sync(e.getEntity()));
        if (com.wf.wflib.core.api.WFSystems.enabled("probe")) {
            BackpackProbe.registerServer();
        }
    }

    @Override
    public void registerClient(IEventBus modBus) {
        modBus.register(BackpackClient.class);
    }
}
