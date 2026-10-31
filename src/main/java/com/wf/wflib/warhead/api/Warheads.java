package com.wf.wflib.warhead.api;

import com.wf.wflib.core.api.WFRegistry;
import com.wf.wflib.warhead.Catalogue;
import com.wf.wflib.explosion.api.ExplosionAEF;
import com.wf.wflib.explosion.api.MiniNuke;
import com.wf.wflib.explosion.api.BlockAllocatorShapedCharge;
import com.wf.wflib.explosion.api.BlockAllocatorStandard;
import com.wf.wflib.explosion.api.BlockProcessorStandard;
import com.wf.wflib.explosion.api.EntityProcessorCross;
import com.wf.wflib.explosion.api.PlayerProcessorStandard;
import com.wf.wflib.explosion.api.ExplosionCreator;
import com.wf.wflib.explosion.api.FragmentationUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import com.wf.wflib.core.api.WFLib;

/**
 * Detonations by id. Catalogue entries live only once requested or referenced by a live round / missile
 * ({@link com.wf.wflib.core.api.WFLibContent}); own {@link #register} live at once, before the load-complete freeze.
 * Reads live only, any thread.
 */
public final class Warheads {

    public static final int STANDARD_BLAST_RADIUS = 50;
    public static final float STANDARD_BLAST_SIZE = 15F;

    public static final Detonation STANDARD = (source, pos) -> {
        Level level = source.level();
        if (level.isClientSide) {
            return;
        }
        float size = sizeFor(source, STANDARD_BLAST_SIZE);
        var xnt = new ExplosionAEF(level, pos.x, pos.y, pos.z, size, source.exploder());

        if (source.breaksBlocks()) {
            xnt.setBlockAllocator(new BlockAllocatorStandard(size < 8 ? 16 : 32));
            xnt.setBlockProcessor(new BlockProcessorStandard().setNoDrop());
        }
        xnt.setEntityProcessor(new EntityProcessorCross());
        xnt.setPlayerProcessor(new PlayerProcessorStandard());
        xnt.igniterFaction(source.igniterFactionId());
        xnt.explode();
        if (size >= STANDARD_BLAST_SIZE * 0.5f) {
            ExplosionCreator.composeEffectLarge(level, pos.x, pos.y, pos.z);
        } else {
            ExplosionCreator.composeEffectSmall(level, pos.x, pos.y, pos.z);
        }
    };
    public static final InterceptDetonation STANDARD_INTERCEPT = (source, pos) -> {
        Level level = source.level();
        if (level.isClientSide) {
            return;
        }
        ExplosionCreator.composeEffectSmall(level, pos.x, pos.y, pos.z);
    };

    public static final float SHAPED_CHARGE_SIZE = 6F;

    /**
     * Shaped charge (HEAT): fires a tight jet along the carrier's impact heading ({@link WarheadCarrier#angle()}),
     * drilling a deep, narrow shaft through hardened cover with only a shallow crater at the surface.
     */
    public static final Detonation SHAPED_CHARGE = (source, pos) -> {
        Level level = source.level();
        if (level.isClientSide) {
            return;
        }
        var xnt = new ExplosionAEF(level, pos.x, pos.y, pos.z, sizeFor(source, SHAPED_CHARGE_SIZE), source.exploder())
                .makeShapedCharge(source.angle(), source.blastHalfAngleDeg(),
                        BlockAllocatorShapedCharge.DEFAULT_JET_POWER)
                .igniterFaction(source.igniterFactionId());
        if (!source.breaksBlocks()) {
            xnt.setBlockAllocator(null);
        }
        xnt.explode();
        ExplosionCreator.composeEffectSmall(level, pos.x, pos.y, pos.z);
    };

    public static final Detonation MININUKE = (source, pos) ->
            MiniNuke.detonate(source.level(), pos,
                    MiniNuke.medium(), source.igniterFactionId());

    public static final Detonation FRAGMENTATION = (source, pos) -> {
        Level level = source.level();
        if (level.isClientSide) {
            return;
        }
        FragmentationUtil.cone(level, pos, new Vec3(0.0, -1.0, 0.0),
                Math.toRadians(60.0), source.getFragmentCount(), 1.2, 0.4,
                BombletWarhead.ID, BombletWarhead.STANDARD, BombletEntity.DEFAULT_FUSE, null);
        ExplosionCreator.composeEffectSmall(level, pos.x, pos.y, pos.z);
    };

    public static final Detonation INTERCEPTOR = (source, pos) -> {
        Level level = source.level();
        if (level.isClientSide) {
            return;
        }
        ExplosionCreator.composeEffectSmall(level, pos.x, pos.y, pos.z);
    };

    public static final Detonation INERT = (source, pos) -> {
    };

    private static final ResourceLocation DEFAULT_ID = rl("standard");
    private static final WFRegistry<Own> OWN = WFRegistry.create("warhead", "warheads");
    private static final WFRegistry<Integer> PEAK_DAMAGE = WFRegistry.create("warhead", "warhead peak damage");

    private record Own(Detonation detonation, @Nullable InterceptDetonation intercept) {
    }

    private Warheads() {
    }

    private static float sizeFor(WarheadCarrier source, float authored) {
        float size = source.blastSize();
        return size > 0 ? size : authored;
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(WFLib.MODID, path);
    }

    public static ResourceLocation parse(String id) {
        if (id == null || id.isEmpty()) {
            return DEFAULT_ID;
        }
        ResourceLocation parsed = id.indexOf(':') >= 0 ? ResourceLocation.tryParse(id) : rl(id);
        return parsed != null ? parsed : DEFAULT_ID;
    }

    /** Own (non-catalogue) warhead: live at once. */
    public static void register(ResourceLocation id, Detonation detonation) {
        register(id, detonation, null);
    }

    public static void register(ResourceLocation id, Detonation detonation,
                                @Nullable InterceptDetonation intercept) {
        if (Catalogue.defined(id)) {
            throw new IllegalStateException("warhead " + id + ": WFLib catalogue entry, request it");
        }
        OWN.register(id, new Own(detonation, intercept));
    }

    public static Detonation get(ResourceLocation id) {
        return get(id, STANDARD);
    }

    public static Detonation get(ResourceLocation id, Detonation fallback) {
        Own own = OWN.get(id);
        if (own != null) {
            return own.detonation;
        }
        Detonation live = Catalogue.detonation(id);
        return live != null ? live : fallback;
    }

    public static InterceptDetonation getIntercept(ResourceLocation id) {
        Own own = OWN.get(id);
        InterceptDetonation intercept = own != null ? own.intercept : Catalogue.intercept(id);
        if (intercept != null) {
            return intercept;
        }
        Detonation detonation = get(id);
        return detonation::detonate;
    }

    /** WFLib catalogue warhead {@code id} (+ children) goes live; no-op for any other id. */
    public static void require(ResourceLocation id) {
        Catalogue.require(id);
    }

    public static boolean exists(ResourceLocation id) {
        return OWN.containsKey(id) || Catalogue.live(id);
    }

    public static float blastSize(ResourceLocation id) {
        return Catalogue.blastSize(id);
    }

    /** Records what {@code id} does to a person at the centre of it. */
    public static void declarePeakDamage(ResourceLocation id, int damage) {
        PEAK_DAMAGE.register(id, damage);
    }

    public static int peakEntityDamage(ResourceLocation id) {
        Integer declared = PEAK_DAMAGE.get(id);
        if (declared != null) {
            return declared;
        }
        float size = blastSize(id);
        return size <= 0f ? 0 : (int) (8.0f * size + 1.0f);
    }

    public static Set<ResourceLocation> ids() {
        Set<ResourceLocation> all = new LinkedHashSet<>(OWN.ids());
        all.addAll(Catalogue.liveIds());
        return Collections.unmodifiableSet(all);
    }

    public static ResourceLocation defaultId() {
        return DEFAULT_ID;
    }

    @FunctionalInterface
    public interface Detonation {
        void detonate(WarheadCarrier source, Vec3 pos);
    }

    @FunctionalInterface
    public interface InterceptDetonation {
        void detonate(WarheadCarrier source, Vec3 pos);
    }
}
