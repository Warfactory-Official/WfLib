package com.wf.wflib.warhead;

import com.wf.wflib.WFLib;
import com.wf.wflib.aef.ExplosionAEF;
import com.wf.wflib.aef.nuke.MiniNuke;
import com.wf.wflib.aef.standard.BlockAllocatorShapedCharge;
import com.wf.wflib.aef.standard.BlockAllocatorStandard;
import com.wf.wflib.aef.standard.BlockProcessorStandard;
import com.wf.wflib.aef.standard.EntityProcessorCross;
import com.wf.wflib.aef.standard.PlayerProcessorStandard;
import com.wf.wflib.entity.BombletEntity;
import com.wf.wflib.fx.ExplosionCreator;
import com.wf.wflib.util.FragmentationUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Detonations by id. Catalogue (static block) live only once requested or referenced by a live round / missile
 * ({@link com.wf.wflib.content.WFLibContent}); own {@link #register} live at once. Reads live only, any thread.
 */
public final class WarheadRegistry {

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
    private static final Map<ResourceLocation, Detonation> CATALOGUE = new LinkedHashMap<>();
    private static final Map<ResourceLocation, InterceptDetonation> CATALOGUE_INTERCEPTS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Float> CATALOGUE_BLAST = new LinkedHashMap<>();
    /** Spawned sub-munition warheads: live with the parent. */
    private static final Map<ResourceLocation, ResourceLocation> CATALOGUE_CHILD = new LinkedHashMap<>();
    private static volatile Map<ResourceLocation, Detonation> warheads = Map.of();
    private static volatile Map<ResourceLocation, InterceptDetonation> intercepts = Map.of();
    private static volatile Map<ResourceLocation, Float> blastSizes = Map.of();
    /**
     * Peak damage to a person standing on the charge, for warheads that know it outright rather than having it
     * inferred from a blast size.
     */
    private static final Map<ResourceLocation, Integer> PEAK_DAMAGE = new LinkedHashMap<>();

    static {
        define(DEFAULT_ID, STANDARD, STANDARD_INTERCEPT);
        define(rl("mininuke"), MININUKE);
        define(rl("fragmentation"), FRAGMENTATION);
        define(rl("shaped_charge"), SHAPED_CHARGE);
        define(RecursiveFrag.ID, RecursiveFrag::detonate);
        define(GasWarhead.ID, GasWarhead::detonate);
        define(TearGasWarhead.ID, TearGasWarhead::detonate);
        define(FireWarhead.ID, FireWarhead::detonate);
        define(ThermobaricWarhead.ID, ThermobaricWarhead::detonate);
        define(AgentOrangeWarhead.ID, AgentOrangeWarhead::detonate);
        define(TorpedoWarhead.ID, TorpedoWarhead::detonate);
        define(FireCluster.ID, FireCluster::detonate);
        define(rl("interceptor"), INTERCEPTOR, INTERCEPTOR::detonate);
        define(rl("inert"), INERT);
        define(rl("emp"), EMPWarhead::detonate);
        define(BombletWarhead.ID, BombletWarhead.STANDARD);
        define(BombletWarhead.FIRE_ID, BombletWarhead.FIRE);
        CATALOGUE_CHILD.put(rl("fragmentation"), BombletWarhead.ID);
        CATALOGUE_CHILD.put(FireCluster.ID, BombletWarhead.FIRE_ID);
        com.wf.wflib.grenade.GrenadeWarheads.bootstrap();

        CATALOGUE_BLAST.put(DEFAULT_ID, STANDARD_BLAST_SIZE);
        CATALOGUE_BLAST.put(FireWarhead.ID, 8F);
        CATALOGUE_BLAST.put(ThermobaricWarhead.ID, ThermobaricWarhead.SIZE);
        CATALOGUE_BLAST.put(AgentOrangeWarhead.ID, AgentOrangeWarhead.RADIUS);
        CATALOGUE_BLAST.put(rl("shaped_charge"), SHAPED_CHARGE_SIZE);
    }

    private WarheadRegistry() {
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

    private static void define(ResourceLocation id, Detonation detonation) {
        CATALOGUE.put(id, detonation);
    }

    private static void define(ResourceLocation id, Detonation detonation, InterceptDetonation intercept) {
        CATALOGUE.put(id, detonation);
        CATALOGUE_INTERCEPTS.put(id, intercept);
    }

    /** Own (non-catalogue) warhead: live at once. */
    public static void register(ResourceLocation id, Detonation detonation) {
        register(id, detonation, null);
    }

    public static synchronized void register(ResourceLocation id, Detonation detonation,
                                             @Nullable InterceptDetonation intercept) {
        if (CATALOGUE.containsKey(id)) {
            throw new IllegalStateException("warhead " + id + ": WFLib catalogue entry, request it");
        }
        live(id, detonation, intercept, null);
    }

    /**
     * Catalogue id not live => live (+ sub-munition child); live / other id => no-op. Freeze + post-freeze preset
     * registration; dependents request via {@link com.wf.wflib.content.WFLibContent}.
     */
    public static synchronized void require(ResourceLocation id) {
        Detonation d = CATALOGUE.get(id);
        if (d == null || warheads.containsKey(id)) {
            return;
        }
        live(id, d, CATALOGUE_INTERCEPTS.get(id), CATALOGUE_BLAST.get(id));
        ResourceLocation child = CATALOGUE_CHILD.get(id);
        if (child != null) {
            require(child);
        }
    }

    private static void live(ResourceLocation id, Detonation detonation, @Nullable InterceptDetonation intercept,
                             @Nullable Float blast) {
        Map<ResourceLocation, Detonation> w = new LinkedHashMap<>(warheads);
        w.put(id, detonation);
        warheads = Collections.unmodifiableMap(w);
        Map<ResourceLocation, InterceptDetonation> i = new LinkedHashMap<>(intercepts);
        if (intercept != null) {
            i.put(id, intercept);
        } else {
            i.remove(id);
        }
        intercepts = Collections.unmodifiableMap(i);
        if (blast != null) {
            Map<ResourceLocation, Float> b = new LinkedHashMap<>(blastSizes);
            b.put(id, blast);
            blastSizes = Collections.unmodifiableMap(b);
        }
    }

    public static Set<ResourceLocation> catalogue() {
        return Collections.unmodifiableSet(CATALOGUE.keySet());
    }

    public static Detonation get(ResourceLocation id) {
        return warheads.getOrDefault(id, STANDARD);
    }

    public static Detonation get(ResourceLocation id, Detonation fallback) {
        return warheads.getOrDefault(id, fallback);
    }

    public static InterceptDetonation getIntercept(ResourceLocation id) {
        InterceptDetonation intercept = intercepts.get(id);
        if (intercept != null) {
            return intercept;
        }
        Detonation detonation = get(id);
        return detonation::detonate;
    }

    public static boolean exists(ResourceLocation id) {
        return warheads.containsKey(id);
    }

    public static float blastSize(ResourceLocation id) {
        return blastSizes.getOrDefault(id, 0f);
    }

    /** Records what {@code id} does to a person at the centre of it. */
    public static void declarePeakDamage(ResourceLocation id, int damage) {
        PEAK_DAMAGE.put(id, damage);
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
        return warheads.keySet();
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
