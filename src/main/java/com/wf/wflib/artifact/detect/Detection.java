package com.wf.wflib.artifact.detect;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import com.wf.wflib.artifact.api.DetectorItem;
import com.wf.wflib.artifact.api.DetectorTier;
import com.wf.wflib.artifact.api.ZoneArtifact;

/**
 * Who sees what: a player holding a detector (either hand) hears the artefacts its rank reaches within its radius,
 * and sees, and can pick up, those within its reveal radius. Without one, artefacts are not there for them.
 */
public final class Detection {
    private Detection() {
    }

    /** The detector held, the better if both hands hold one; null if none. */
    @Nullable
    public static DetectorTier held(Player player) {
        DetectorTier best = null;
        for (ItemStack stack : new ItemStack[] {player.getMainHandItem(), player.getOffhandItem()}) {
            if (stack.getItem() instanceof DetectorItem d && (best == null || d.tier().rank > best.rank)) {
                best = d.tier();
            }
        }
        return best;
    }

    public static boolean finds(DetectorTier tier, ZoneArtifact artifact) {
        return artifact.detectRank() <= tier.rank;
    }

    /** Shown to {@code player} (and theirs to take): a detector out that finds it, within its reveal radius. */
    public static boolean revealed(Player player, ZoneArtifact artifact) {
        DetectorTier tier = held(player);
        return tier != null && finds(tier, artifact)
                && artifact.position().distanceTo(player.position().add(0.0D, 1.0D, 0.0D)) <= tier.reveal;
    }

    /** Artefacts {@code tier} hears round {@code player}, nearest first. */
    public static List<ZoneArtifact> heard(Player player, DetectorTier tier) {
        AABB box = player.getBoundingBox().inflate(tier.radius);
        List<ZoneArtifact> out = player.level().getEntitiesOfClass(ZoneArtifact.class, box,
                a -> finds(tier, a) && a.distanceTo(player) <= tier.radius);
        out.sort(Comparator.comparingDouble(a -> a.distanceToSqr(player)));
        return out;
    }
}
