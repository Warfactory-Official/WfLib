package com.wf.wflib.armor.api.exo;

import com.wf.wflib.armor.api.ArmorResult;
import com.wf.wflib.armor.api.WornArmor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Hit -> part wear. */
public final class ExoParts {
    private ExoParts() {
    }

    /**
     * @param limb lowercase zone ({@code head torso left_arm right_arm left_leg right_leg}) or null = unknown, one random part of the piece
     */
    public static void hit(LivingEntity entity, List<WornArmor> worn, @Nullable String limb, ArmorResult result) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        int damage = (int) Math.ceil(result.through() + result.absorbed() * ExoConfig.get().partAbsorbedShare());
        if (damage <= 0) {
            return;
        }
        boolean changed = false;
        for (WornArmor piece : worn) {
            ItemStack garment = piece.garment();
            ExoSpec spec = ExoProfiles.of(garment);
            if (spec == null || spec.parts().isEmpty()) {
                continue;
            }
            List<Integer> hit = new ArrayList<>();
            for (int i = 0; i < spec.parts().size(); i++) {
                if (limb != null ? spec.parts().get(i).limbs().contains(limb) : true) {
                    hit.add(i);
                }
            }
            if (hit.isEmpty()) {
                continue;
            }
            int part = hit.get(limb == null ? player.getRandom().nextInt(hit.size()) : 0);
            changed |= ExoStacks.damage(garment, spec, part, damage) > 0;
        }
        if (changed) {
            ExoServer.recompute(player, false);
        }
    }
}
