package com.wf.wflib.anomaly.site;

import com.mojang.serialization.MapCodec;
import com.wf.wflib.anomaly.AnomalyConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * {@code wflib:anomaly_cave}: starts exactly at the chunks of {@link AnomalyCaves#allocation}, each a field of the
 * slot's tier holding the slot's artefacts, set in the best large natural cave near the start ({@link CaveProbe}) or
 * cut into the rock where there is none. Only in worlds that run anomalies ({@link AnomalyConfig}).
 */
public final class CaveFieldStructure extends Structure {
    public static final MapCodec<CaveFieldStructure> CODEC = simpleCodec(CaveFieldStructure::new);

    public CaveFieldStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        if (!AnomalyConfig.enabled) {
            return Optional.empty();
        }
        AnomalyCaves.Slot slot = AnomalyCaves.allocation.at(ctx.chunkPos().x, ctx.chunkPos().z);
        if (slot == null) {
            return Optional.empty();
        }
        CavePlan plan = CavePlan.roll(RandomSource.create(slot.seed()), slot.tier(), slot.artefacts(), slot::allows);
        CaveProbe.Site site = CaveProbe.find(ctx.chunkGenerator(), ctx.heightAccessor(), ctx.randomState(), slot, plan);
        CavePlan placed = plan.withHeight(site.height());
        CaveLayout layout = CaveLayout.build(placed, site.x(), site.z(), site.floor());
        BoundingBox box = layout.box();
        return Optional.of(new GenerationStub(new BlockPos(site.x(), site.floor(), site.z()),
                b -> b.addPiece(new CaveFieldPiece(placed, site.x(), site.z(), site.floor(), box))));
    }

    @Override
    public StructureType<?> type() {
        return AnomalyCaves.TYPE.get();
    }
}
