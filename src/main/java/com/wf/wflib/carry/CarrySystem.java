package com.wf.wflib.carry;

import com.wf.wflib.carry.api.Carry;
import com.wf.wflib.carry.client.CarryClient;
import com.wf.wflib.core.api.WFSystem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import com.wf.wflib.core.api.WFLib;

/** Open carry of one {@code carriable} item in both hands, no inventory slot. */
public final class CarrySystem implements WFSystem {
    @Override
    public String id() {
        return "carry";
    }

    @Override
    public void register(IEventBus modBus, ModContainer container) {
        DeferredRegister<AttachmentType<?>> attachments =
                DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, WFLib.MODID);
        attachments.register("carried", () -> AttachmentType.builder(() -> ItemStack.EMPTY)
                .serialize(ItemStack.OPTIONAL_CODEC).copyOnDeath().build());
        attachments.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, event -> {
            var r = event.registrar("carry-1");
            r.playToServer(CarryPackets.Pickup.TYPE, CarryPackets.Pickup.STREAM_CODEC, CarryPackets.Pickup::handle);
            r.playToServer(CarryPackets.Drop.TYPE, CarryPackets.Drop.STREAM_CODEC, CarryPackets.Drop::handle);
            r.playToClient(CarryPackets.Carried.TYPE, CarryPackets.Carried.STREAM_CODEC, CarryPackets.Carried::handle);
        });
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Pre.class, CarryServer::onTick);
        NeoForge.EVENT_BUS.addListener(AttackEntityEvent.class, CarryServer::onAttack);
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.LeftClickBlock.class, CarryServer::onLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(BlockEvent.BreakEvent.class, CarryServer::onBreak);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, PlayerInteractEvent.RightClickBlock.class,
                CarryServer::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickItem.class, CarryServer::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.EntityInteract.class, CarryServer::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(ItemEntityPickupEvent.Pre.class, CarryServer::onPickup);
        NeoForge.EVENT_BUS.addListener(EntityMountEvent.class, CarryServer::onMount);
        NeoForge.EVENT_BUS.addListener(LivingDropsEvent.class, CarryServer::onDrops);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.StartTracking.class, CarryServer::onStartTracking);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> Carry.sync(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> Carry.sync(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> Carry.sync(e.getEntity()));
    }

    @Override
    public void registerClient(IEventBus modBus) {
        modBus.addListener(RegisterKeyMappingsEvent.class, CarryClient::keys);
        modBus.addListener(EntityRenderersEvent.AddLayers.class, CarryClient::layers);
        NeoForge.EVENT_BUS.register(CarryClient.class);
    }
}
