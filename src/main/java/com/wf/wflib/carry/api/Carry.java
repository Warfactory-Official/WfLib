package com.wf.wflib.carry.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import com.wf.wflib.carry.CarryPackets;
import com.wf.wflib.core.api.WFLib;

/**
 * Open carry of one {@link #CARRIABLE} item in both hands: player attachment {@link #CARRIED}, no inventory slot.
 * While {@link #carrying}: no attack, jump, sprint, held-item use, pickup, mounting or inventory screen; blocks still
 * usable; storage blocks take the item instead of opening. Creative / spectator exempt (stack handed back).
 * Weight counted through wfweight inventory source (compat.wfweight.CarryWeight).
 */
public final class Carry {
    public static final TagKey<Item> CARRIABLE = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "carriable"));
    /** Reach for picking a dropped item up. */
    public static final double REACH = 4;

    /** Registered by {@code CarrySystem}. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ItemStack>> CARRIED = DeferredHolder.create(
            NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "carried"));

    private static final ResourceLocation NO_JUMP = ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "carry_no_jump");

    private Carry() {
    }

    public static ItemStack carried(Player player) {
        return player.getData(CARRIED);
    }

    /** Restrictions apply: stack held and player not exempt. */
    public static boolean carrying(Player player) {
        return !carried(player).isEmpty() && !player.isCreative() && !player.isSpectator();
    }

    // ------------------------------------------------------------------ state changes (server)

    /** {@code entity < 0} or none in reach => main hand. */
    public static void pickup(ServerPlayer player, int entity) {
        if (!carried(player).isEmpty() || player.isCreative() || player.isSpectator() || !player.isAlive()
                || player.isPassenger()) {
            return;
        }
        if (entity >= 0) {
            if (!(player.level().getEntity(entity) instanceof ItemEntity item) || item.isRemoved()
                    || !item.getItem().is(CARRIABLE)
                    || item.getBoundingBox().inflate(0.5).distanceToSqr(player.getEyePosition()) > REACH * REACH) {
                return;
            }
            ItemStack taken = item.getItem().split(1);
            if (item.getItem().isEmpty()) {
                item.discard();
            }
            set(player, taken);
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!held.is(CARRIABLE)) {
            player.displayClientMessage(Component.translatable("wflib.carry.nothing"), true);
            return;
        }
        set(player, held.split(1));
    }

    public static void drop(ServerPlayer player) {
        ItemStack stack = carried(player);
        if (stack.isEmpty() || !carrying(player)) {
            return;
        }
        set(player, ItemStack.EMPTY);
        player.drop(stack, false, true);
    }

    public static void set(ServerPlayer player, ItemStack stack) {
        player.setData(CARRIED, stack);
        refresh(player);
        sync(player);
    }

    /** Exempt player holding a stack: inventory, else floor. */
    public static void release(ServerPlayer player) {
        ItemStack stack = carried(player);
        set(player, ItemStack.EMPTY);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** Jump power 0 while carrying (vanilla skips the jump at <= 1e-5). */
    public static void refresh(Player player) {
        AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump == null) {
            return;
        }
        boolean want = carrying(player);
        if (want == jump.hasModifier(NO_JUMP)) {
            return;
        }
        if (want) {
            jump.addTransientModifier(new AttributeModifier(NO_JUMP, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else {
            jump.removeModifier(NO_JUMP);
        }
    }

    public static void sync(Player player) {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        com.wf.wflib.carry.CarryPackets.Carried packet = new CarryPackets.Carried(sp.getId(), carried(sp).copy());
        PacketDistributor.sendToPlayer(sp, packet);
        PacketDistributor.sendToPlayersTrackingEntity(sp, packet);
    }
}
