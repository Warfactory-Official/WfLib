package com.wf.wflib.sway.api;

import com.norwood.ahf.hook.AhfHooks;
import com.wf.wflib.core.api.Hook;
import com.wf.wflib.core.api.OrderedRegistry;
import com.wf.wflib.core.api.Seeded;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;

import java.util.function.Predicate;

/** Pain wobble. Intensity and amplitude come from one health model ({@link #SOURCE}); server and client. */
public final class PainSway {
    public interface Source {
        /** Pain sway [0, 1]. */
        float intensity(Player p);

        /** Floor [0, 1] while aiming a ranged weapon (e.g. fractured arm). */
        default float aimFloor(Player p) {
            return 0;
        }

        /** Peak degrees at intensity 1. */
        float amplitudeDeg();
    }

    public static final Hook<Source> SOURCE = Hook.of("sway.pain_source", new Source() {
        @Override
        public float intensity(Player p) {
            return 0;
        }

        @Override
        public float amplitudeDeg() {
            return 0;
        }
    });
    /** Any true => the camera pain sway skips the player (the yielding mod applies its own). */
    public static final OrderedRegistry<Predicate<Player>> YIELDS = OrderedRegistry.create("sway", "pain sway yields");

    private static final double TAU = Math.PI * 2;

    private PainSway() {
    }

    public static float intensity(Player p, boolean aimingRanged) {
        Source s = SOURCE.get();
        float i = s.intensity(p);
        return aimingRanged ? Math.max(i, s.aimFloor(p)) : i;
    }

    public static float amplitudeDeg() {
        return SOURCE.get().amplitudeDeg();
    }

    public static boolean yielded(Player p) {
        for (Predicate<Player> y : YIELDS.values()) {
            if (y.test(p)) {
                return true;
            }
        }
        return false;
    }

    /** Drawing a bow / crossbow or holding a gun (AHF). */
    public static boolean aimingRanged(Player p) {
        if (p.isUsingItem()) {
            Item item = p.getUseItem().getItem();
            if (item instanceof BowItem || item instanceof CrossbowItem) {
                return true;
            }
        }
        return AhfHooks.isHeldGun(p.getMainHandItem()) || AhfHooks.isHeldGun(p.getOffhandItem());
    }

    /** Adds the wobble of peak {@code deg} at {@code time} (client clock ticks, fractional) for {@code seed}. Pure. */
    public static void wobble(float deg, double time, long seed, float[] out) {
        if (deg > 0) {
            double t = time / 20.0;
            out[0] += (float) (deg * Math.sin(TAU * 0.41 * t + phase(seed, 4)));
            out[1] += (float) (deg * Math.sin(TAU * 0.53 * t + phase(seed, 5)));
        }
    }

    /** Index 4, 5 of the {@code 0x5A7} phase family; callers' own breath patterns hold 0-3. */
    private static double phase(long seed, int i) {
        return TAU * Seeded.unit(seed, 0x5A7, i);
    }
}
