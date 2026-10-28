package com.wf.wflib.anomaly.cluster;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.wf.wflib.WFLib;
import com.wf.wflib.anomaly.AnomalyConfig;
import com.wf.wflib.anomaly.cluster.ClusterPlan.Design;
import com.wf.wflib.anomaly.cluster.ClusterPlan.Kind;
import com.wf.wflib.anomaly.cluster.ClusterPlan.Loot;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code wflib:anomaly_cluster}: a field of anomalies on ground shaped for it ({@link ClusterPlan.Design}), with
 * artefacts lying among the worst of them. Placed features {@code wflib:anomaly_cluster_tier_1..4} carry it, one a
 * tier; wfcore's resource allocation (WFWorldGen's {@code anomalies} section) scales them by area. Generates only in
 * worlds that run anomalies ({@link AnomalyConfig}).
 */
public final class AnomalyClusterFeature extends Feature<AnomalyClusterFeature.Config> {

    /** @param design one design only (else picked per cluster from those open at the tier) */
    public record Config(int tier, Optional<Design> design) implements FeatureConfiguration {
        static final Codec<Design> DESIGN = Codec.STRING.comapFlatMap(AnomalyClusterFeature::design,
                d -> d.name().toLowerCase(Locale.ROOT));
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, ClusterPlan.TIERS).fieldOf("tier").forGetter(Config::tier),
                DESIGN.optionalFieldOf("design").forGetter(Config::design)
        ).apply(i, Config::new));
    }

    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, WFLib.MODID);
    public static final DeferredHolder<Feature<?>, AnomalyClusterFeature> CLUSTER = FEATURES.register(
            "anomaly_cluster", () -> new AnomalyClusterFeature(Config.CODEC));

    /** Anomalies at least this far apart on the ground (blocks); a tier 4 packs them closer. */
    private static final double SPACING = 3.0D;

    /** What was made, for the command's report. */
    public record Result(ClusterPlan plan, BlockPos centre, int anomalies, int artefacts) {
    }

    private AnomalyClusterFeature(Codec<Config> codec) {
        super(codec);
    }

    public static DataResult<Design> design(String name) {
        try {
            return DataResult.success(Design.valueOf(name.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "unknown anomaly cluster design " + name);
        }
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> ctx) {
        if (!AnomalyConfig.enabled) {
            return false;
        }
        RandomSource r = ctx.random();
        // The chunk's middle, give or take 2: with ClusterPlan.MAX_RADIUS it stays inside what a feature may write.
        BlockPos origin = ctx.origin();
        BlockPos centre = new BlockPos((origin.getX() & ~15) + 8 + r.nextInt(5) - 2, origin.getY(),
                (origin.getZ() & ~15) + 8 + r.nextInt(5) - 2);
        return generate(ctx.level(), r, centre, ctx.config().tier(), ctx.config().design().orElse(null)) != null;
    }

    /**
     * The nearest site to {@code near} that takes a cluster, trying rings out to {@code reach} blocks (the command:
     * "one near me"); null if none does.
     */
    @Nullable
    public static Result generateNear(WorldGenLevel level, RandomSource r, BlockPos near, int tier,
                                      @Nullable Design design, int reach) {
        for (int ring = 0; ring <= reach; ring += 12) {
            int tries = ring == 0 ? 1 : 8;
            for (int i = 0; i < tries; i++) {
                double a = Mth.TWO_PI * i / tries;
                BlockPos at = near.offset((int) Math.round(Math.cos(a) * ring), 0, (int) Math.round(Math.sin(a) * ring));
                Result made = generate(level, r, at, tier, design);
                if (made != null) {
                    return made;
                }
            }
        }
        return null;
    }

    /** A cluster centred on {@code centre}'s column; null if the ground there would not take one (water, a cliff). */
    @Nullable
    public static Result generate(WorldGenLevel level, RandomSource r, BlockPos centre, int tier,
                                  @Nullable Design design) {
        ClusterPlan plan = ClusterPlan.roll(r, tier, design);
        ClusterSite site = ClusterSite.open(level, r, centre, plan.radius(), plan.tier());
        if (site == null) {
            return null;
        }
        ClusterShapes.shape(site, plan.design());
        List<BlockPos> floor = new ArrayList<>(site.floor);
        List<BlockPos> ceiling = new ArrayList<>(site.ceiling);
        Util.shuffle(floor, r);
        Util.shuffle(ceiling, r);
        double spacing = plan.tier() == ClusterPlan.TIERS ? SPACING - 0.5D : SPACING;
        List<BlockPos> placed = new ArrayList<>();
        List<Kind> placedKinds = new ArrayList<>();
        for (Kind k : plan.anomalies()) {
            BlockPos at = switch (k.spot) {
                case FIELD -> floor.stream().filter(p -> free(level, p))
                        .min(Comparator.comparingDouble(p -> p.distSqr(centre.atY(p.getY())))).orElse(null);
                case CEILING -> take(level, ceiling, placed, 2.0D);
                case FLOOR -> take(level, floor, placed, spacing);
            };
            if (at == null) {
                continue;
            }
            level.setBlock(at, block(k).defaultBlockState(), ClusterSite.FLAGS);
            placed.add(at);
            placedKinds.add(k);
        }
        int artefacts = placed.isEmpty() ? 0 : scatter(level, r, plan.artefacts(), placed, placedKinds);
        return new Result(plan, centre, placed.size(), artefacts);
    }

    private static boolean free(WorldGenLevel level, BlockPos p) {
        return level.getBlockState(p).canBeReplaced() && level.getFluidState(p).isEmpty();
    }

    /** The first free spot at least {@code spacing} from every anomaly so far, taken off the list. */
    @Nullable
    private static BlockPos take(WorldGenLevel level, List<BlockPos> spots, List<BlockPos> placed, double spacing) {
        for (int i = 0; i < spots.size(); i++) {
            BlockPos p = spots.get(i);
            if (free(level, p) && placed.stream().allMatch(q -> q.distSqr(p) >= spacing * spacing)) {
                spots.remove(i);
                return p;
            }
        }
        return null;
    }

    private static Block block(Kind k) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, k.id));
    }

    /**
     * Artefacts lie on the ground a block or two from the worst anomalies (STALKER's: you go in to fetch them), one
     * anomaly each, hardest first. They never despawn.
     */
    private static int scatter(WorldGenLevel level, RandomSource r, List<Loot> loot, List<BlockPos> placed,
                               List<Kind> kinds) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < placed.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingInt((Integer i) -> -kinds.get(i).rank));
        int n = 0;
        for (Loot l : loot) {
            BlockPos anchor = placed.get(order.get(n % order.size()));
            double angle = r.nextDouble() * Mth.TWO_PI;
            double out = 1.0D + r.nextDouble();
            int x = anchor.getX() + (int) Math.round(Math.cos(angle) * out);
            int z = anchor.getZ() + (int) Math.round(Math.sin(angle) * out);
            int y = ClusterSite.ground(level, x, z);
            if (y == Integer.MIN_VALUE || Math.abs(y + 1 - anchor.getY()) > 2) {
                x = anchor.getX();
                z = anchor.getZ();
                y = anchor.getY() - 1;
            }
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(
                    ResourceLocation.fromNamespaceAndPath(WFLib.MODID, l.id)));
            ItemEntity item = new ItemEntity(level.getLevel(), x + 0.5D, y + 1.05D, z + 0.5D, stack, 0.0D, 0.0D, 0.0D);
            item.setUnlimitedLifetime();
            level.addFreshEntity(item);
            n++;
        }
        return n;
    }
}
