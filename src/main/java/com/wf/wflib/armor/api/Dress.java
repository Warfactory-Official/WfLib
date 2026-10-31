package com.wf.wflib.armor.api;

import com.wf.wflib.armor.ArmorConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Equipping and removing authored armour. A piece the wearer may not handle ({@link #selfAllowed}) changes hands
 * only through the probe: another player ({@link #PUT_ON}, {@link #TAKE_OFF}) or an armour stand
 * ({@link #TO_STAND}). Creative bypasses; dispensers and commands are not gated.
 */
public final class Dress {
    public static final ResourceLocation PUT_ON = ArmorPreset.rl("dress_put_on");
    public static final ResourceLocation TAKE_OFF = ArmorPreset.rl("dress_take_off");
    public static final ResourceLocation TO_STAND = ArmorPreset.rl("dress_to_stand");

    /** Indexed by probe action {@code arg}. */
    public static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private Dress() {
    }

    /** {@code stack} in or for {@code wearer}'s own hands. */
    public static boolean selfAllowed(Player wearer, ItemStack stack) {
        return wearer.isCreative() || !(stack.getItem() instanceof ArmorPieceItem piece)
                || piece.preset().selfDress() && ArmorConfig.get().selfDress();
    }

    public static boolean assisted(Player wearer, ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ArmorPieceItem && !selfAllowed(wearer, stack);
    }

    /** What {@code helper} holds, if it can go on {@code target} now. */
    @Nullable
    public static EquipmentSlot putOnSlot(Player helper, Player target) {
        ItemStack held = helper.getMainHandItem();
        if (target == helper || !(held.getItem() instanceof ArmorPieceItem piece) || selfAllowed(target, held)) {
            return null;
        }
        EquipmentSlot slot = piece.getEquipmentSlot();
        return target.getItemBySlot(slot).isEmpty() && !ArmorStacks.blocked(target, slot, held) ? slot : null;
    }

    public static boolean canTakeOff(Player helper, Player target, EquipmentSlot slot) {
        return target != helper && assisted(target, target.getItemBySlot(slot));
    }

    public static boolean canToStand(Player wearer, ArmorStand stand, EquipmentSlot slot) {
        return assisted(wearer, wearer.getItemBySlot(slot)) && stand.getItemBySlot(slot).isEmpty();
    }

    public static void putOn(ServerPlayer helper, Player target) {
        EquipmentSlot slot = putOnSlot(helper, target);
        if (slot != null) {
            target.setItemSlot(slot, helper.getMainHandItem().split(1));
        }
    }

    public static void takeOff(ServerPlayer helper, Player target, EquipmentSlot slot) {
        if (canTakeOff(helper, target, slot)) {
            give(helper, target.getItemBySlot(slot).copy());
            target.setItemSlot(slot, ItemStack.EMPTY);
        }
    }

    public static void toStand(ServerPlayer wearer, ArmorStand stand, EquipmentSlot slot) {
        if (canToStand(wearer, stand, slot)) {
            stand.setItemSlot(slot, wearer.getItemBySlot(slot).copy());
            wearer.setItemSlot(slot, ItemStack.EMPTY);
        }
    }

    private static void give(ServerPlayer to, ItemStack stack) {
        if (!to.getInventory().add(stack)) {
            to.drop(stack, false);
        }
    }

    public static void refuse(ServerPlayer to, String why) {
        to.displayClientMessage(Component.translatable("wflib.dress.no_" + why), true);
    }
}
