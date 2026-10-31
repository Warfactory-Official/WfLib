package com.wf.wflib.artifact.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.Map;
import java.util.WeakHashMap;
import com.wf.wflib.artifact.detect.BoltItem;
import com.wf.wflib.core.api.WFLib;

/**
 * Bolts thrown to find anomalies: {@code wflib:bolt} and anything in {@link #THROWABLE} (every {@code c:bolts} item,
 * GregTech's included). Each throw takes one off the stack; the bolt carries it and gives it back when picked up.
 */
public final class Bolts {
    static final TagKey<Item> THROWABLE = ItemTags.create(
            ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "throwable_bolts"));
    /** Ticks between throws, per player, whichever bolt. */
    public static final int COOLDOWN = 12;
    /** Game time of each player's last throw, per side: {@code Entity.equals} is by id, which the sides share. */
    private static final Map<Player, Long> LAST_THROW = new WeakHashMap<>();
    private static final Map<Player, Long> LAST_THROW_CLIENT = new WeakHashMap<>();

    private Bolts() {
    }

    public static boolean isBolt(ItemStack stack) {
        return stack.is(THROWABLE);
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(Bolts::onRightClickItem);
    }

    /** Other mods' bolts have no use of their own: thrown here. {@link BoltItem} throws itself. */
    private static void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
        ItemStack stack = e.getItemStack();
        if (stack.getItem() instanceof BoltItem || !isBolt(stack)) {
            return;
        }
        e.setCanceled(true);
        e.setCancellationResult(toss(e.getLevel(), e.getEntity(), e.getHand()).getResult());
    }

    public static InteractionResultHolder<ItemStack> toss(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Map<Player, Long> thrown = level.isClientSide ? LAST_THROW_CLIENT : LAST_THROW;
        long now = level.getGameTime();
        Long last = thrown.get(player);
        if (last != null && now >= last && now - last < COOLDOWN) {
            return InteractionResultHolder.fail(stack);
        }
        thrown.put(player, now);
        player.getCooldowns().addCooldown(stack.getItem(), COOLDOWN);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW,
                SoundSource.PLAYERS, 0.4F, 1.4F);
        if (!level.isClientSide) {
            level.addFreshEntity(new BoltEntity(level, player, stack.copyWithCount(1)));
        }
        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        stack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
