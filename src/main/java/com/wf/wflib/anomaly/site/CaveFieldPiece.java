package com.wf.wflib.anomaly.site;

import com.wf.wflib.anomaly.api.AnomalyMix.Kind;
import com.wf.wflib.anomaly.api.AnomalyMix.Loot;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.ArrayList;
import java.util.List;
import com.wf.wflib.anomaly.api.AnomalyCaves;
import com.wf.wflib.anomaly.api.CavePlan;

/** One cave field as a structure piece: plan and middle, saved; each chunk it crosses paints its own part. */
public final class CaveFieldPiece extends StructurePiece {
    private final CavePlan plan;
    private final int cx;
    private final int cz;
    private final int floor;
    private CaveLayout layout;

    CaveFieldPiece(CavePlan plan, int cx, int cz, int floor, BoundingBox box) {
        super(AnomalyCaves.PIECE.get(), 0, box);
        this.plan = plan;
        this.cx = cx;
        this.cz = cz;
        this.floor = floor;
    }

    public CaveFieldPiece(StructurePieceSerializationContext ctx, CompoundTag tag) {
        super(AnomalyCaves.PIECE.get(), tag);
        List<Kind> kinds = new ArrayList<>();
        for (Tag t : tag.getList("anomalies", Tag.TAG_STRING)) {
            kinds.add(Kind.valueOf(t.getAsString()));
        }
        List<Loot> loot = new ArrayList<>();
        for (Tag t : tag.getList("artefacts", Tag.TAG_STRING)) {
            loot.add(Loot.valueOf(t.getAsString()));
        }
        this.plan = new CavePlan(tag.getInt("tier"), tag.getInt("radius"), tag.getInt("height"), List.copyOf(kinds),
                List.copyOf(loot), tag.getLong("variant"));
        this.cx = tag.getInt("cx");
        this.cz = tag.getInt("cz");
        this.floor = tag.getInt("floor");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        tag.putInt("tier", plan.tier());
        tag.putInt("radius", plan.radius());
        tag.putInt("height", plan.height());
        ListTag kinds = new ListTag();
        plan.anomalies().forEach(k -> kinds.add(StringTag.valueOf(k.name())));
        tag.put("anomalies", kinds);
        ListTag loot = new ListTag();
        plan.artefacts().forEach(l -> loot.add(StringTag.valueOf(l.name())));
        tag.put("artefacts", loot);
        tag.putLong("variant", plan.variant());
        tag.putInt("cx", cx);
        tag.putInt("cz", cz);
        tag.putInt("floor", floor);
    }

    public CavePlan plan() {
        return plan;
    }

    private synchronized CaveLayout layout() {
        if (layout == null) {
            layout = CaveLayout.build(plan, cx, cz, floor);
        }
        return layout;
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        CavePainter.paint(layout(), level, box);
    }
}
