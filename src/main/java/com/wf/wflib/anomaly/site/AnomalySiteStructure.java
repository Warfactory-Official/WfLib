package com.wf.wflib.anomaly.site;

import com.mojang.serialization.MapCodec;
import com.wf.wflib.anomaly.AnomalyConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * {@code wflib:anomaly_site}: one candidate per structure-set cell becomes a site with a chance that is each tier's
 * base share times its density there ({@link AnomalySites#density}, which wfcore feeds from WFWorldGen's areas), the
 * tier picked in proportion. Only in worlds that run anomalies ({@link AnomalyConfig}); only on dry, not too steep
 * ground.
 */
public final class AnomalySiteStructure extends Structure {
    public static final MapCodec<AnomalySiteStructure> CODEC = simpleCodec(AnomalySiteStructure::new);

    /** Stock chance a candidate cell holds a site of each tier (density 1). */
    static final double[] BASE = {0.20D, 0.08D, 0.03D, 0.012D};

    public AnomalySiteStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        if (!AnomalyConfig.enabled) {
            return Optional.empty();
        }
        ChunkPos cp = ctx.chunkPos();
        int x = cp.getMiddleBlockX();
        int z = cp.getMiddleBlockZ();
        double[] w = new double[BASE.length];
        double total = 0.0D;
        for (int t = 0; t < BASE.length; t++) {
            w[t] = BASE[t] * Math.max(0.0D, AnomalySites.density.multiplier(t + 1, x, z));
            total += w[t];
        }
        var random = ctx.random();
        double u = random.nextDouble();
        if (u >= Math.min(1.0D, total)) {
            return Optional.empty();
        }
        int tier = 1;
        for (double acc = 0.0D; tier <= BASE.length; tier++) {
            acc += w[tier - 1] / total * Math.min(1.0D, total);
            if (u < acc) {
                break;
            }
        }
        tier = Math.min(tier, BASE.length);
        GeneratorTerrain terrain = new GeneratorTerrain(ctx.chunkGenerator(), ctx.heightAccessor(), ctx.randomState());
        int y = terrain.ground(x, z);
        Holder<Biome> biome = ctx.biomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y),
                QuartPos.fromBlock(z), ctx.randomState().sampler());
        SiteStyle style = SiteStyle.fromBiome(biome.unwrapKey().map(k -> k.location().getPath()).orElse(""));
        SitePlan plan = SitePlan.roll(random, tier, style, null, null);
        if (!AnomalySites.suitable(terrain, x, z, plan.radius())) {
            return Optional.empty();
        }
        BoundingBox box = AnomalySites.box(x, y, z, plan.radius());
        return Optional.of(new GenerationStub(new BlockPos(x, y, z),
                b -> b.addPiece(new AnomalySitePiece(plan, style, x, z, box))));
    }

    @Override
    public StructureType<?> type() {
        return AnomalySites.TYPE.get();
    }
}
