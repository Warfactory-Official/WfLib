package com.wf.wflib.armor.api.exo;

import com.wf.wflib.core.api.OrderedRegistry;
import com.wf.wflib.armor.api.ArmorPreset;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Automatic healing for a wearer whose exo has life support. {@link #DANGER}: any true => in danger.
 * {@link #HANDLERS}: priority descending, first to return true consumed something.
 */
public final class LifeSupport {
    public interface Handler {
        /** Uses one healing item on {@code player}; false = nothing usable. */
        boolean use(ServerPlayer player);
    }

    public static final OrderedRegistry<Predicate<ServerPlayer>> DANGER =
            OrderedRegistry.create("armor", "exo life support danger");
    public static final OrderedRegistry<Handler> HANDLERS = OrderedRegistry.create("armor", "exo life support handlers");

    private LifeSupport() {
    }

    public static void registerDefaults() {
        DANGER.register(ArmorPreset.rl("exo_low_health"), 0,
                p -> p.getHealth() <= p.getMaxHealth() * ExoConfig.get().lifeSupportDanger());
        HANDLERS.register(ArmorPreset.rl("exo_potion"), 0, LifeSupport::drinkPotion);
    }

    public static boolean inDanger(ServerPlayer player) {
        for (Predicate<ServerPlayer> p : DANGER.values()) {
            if (p.test(player)) {
                return true;
            }
        }
        return false;
    }

    public static boolean use(ServerPlayer player) {
        for (Handler h : HANDLERS.values()) {
            if (h.use(player)) {
                return true;
            }
        }
        return false;
    }

    /** Inventories healing items may come from: main inventory, then the worn backpack when that system is on. */
    public static List<Container> sources(ServerPlayer player) {
        List<Container> out = new ArrayList<>(2);
        out.add(player.getInventory());
        if (com.wf.wflib.core.api.WFSystems.enabled("backpack")) {
            out.add(com.wf.wflib.backpack.api.Backpacks.container(player));
        }
        return out;
    }

    private static boolean healing(PotionContents contents) {
        for (MobEffectInstance e : contents.getAllEffects()) {
            if (e.is(MobEffects.HEAL) || e.is(MobEffects.REGENERATION) || e.is(MobEffects.ABSORPTION)) {
                return true;
            }
        }
        return false;
    }

    private static boolean drinkPotion(ServerPlayer player) {
        for (Container c : sources(player)) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack stack = c.getItem(i);
                PotionContents contents = stack.is(Items.POTION) ? stack.get(DataComponents.POTION_CONTENTS) : null;
                if (contents == null || !healing(contents)) {
                    continue;
                }
                contents.forEachEffect(e -> {
                    if (e.getEffect().value().isInstantenous()) {
                        e.getEffect().value().applyInstantenousEffect(player, player, player, e.getAmplifier(), 1.0);
                    } else {
                        player.addEffect(new MobEffectInstance(e));
                    }
                });
                stack.shrink(1);
                c.setChanged();
                ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
                if (!player.getInventory().add(bottle)) {
                    player.drop(bottle, false);
                }
                return true;
            }
        }
        return false;
    }
}
