package com.wf.wflib.anomaly.site;

import com.wf.wflib.anomaly.api.AnomalyMix.Kind;
import com.wf.wflib.anomaly.api.AnomalyMix.Loot;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
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
import com.wf.wflib.anomaly.api.AnomalySites;
import com.wf.wflib.anomaly.api.SitePlan;
import com.wf.wflib.anomaly.api.SiteStyle;

/**
 * One anomaly site as a structure piece: its plan, saved; each chunk it crosses works out the same layout (cached on
 * the piece while the site is loaded) and paints its own part.
 */
public final class AnomalySitePiece extends StructurePiece {
    private final SitePlan plan;
    private final SiteStyle style;
    private final int cx;
    private final int cz;
    private SiteLayout layout;

    public AnomalySitePiece(SitePlan plan, SiteStyle style, int cx, int cz, BoundingBox box) {
        super(AnomalySites.PIECE.get(), 0, box);
        this.plan = plan;
        this.style = style;
        this.cx = cx;
        this.cz = cz;
    }

    public AnomalySitePiece(StructurePieceSerializationContext ctx, CompoundTag tag) {
        super(AnomalySites.PIECE.get(), tag);
        List<Kind> kinds = new ArrayList<>();
        for (Tag t : tag.getList("anomalies", Tag.TAG_STRING)) {
            kinds.add(Kind.valueOf(t.getAsString()));
        }
        List<Loot> loot = new ArrayList<>();
        for (Tag t : tag.getList("artefacts", Tag.TAG_STRING)) {
            loot.add(Loot.valueOf(t.getAsString()));
        }
        String overlay = tag.getString("overlay");
        this.plan = new SitePlan(tag.getInt("tier"), SitePlan.Motif.valueOf(tag.getString("motif")),
                overlay.isEmpty() ? null : SitePlan.Overlay.valueOf(overlay), tag.getInt("radius"), List.copyOf(kinds),
                List.copyOf(loot), tag.getLong("variant"));
        this.style = SiteStyle.valueOf(tag.getString("style"));
        this.cx = tag.getInt("cx");
        this.cz = tag.getInt("cz");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        tag.putInt("tier", plan.tier());
        tag.putString("motif", plan.motif().name());
        tag.putString("overlay", plan.overlay() == null ? "" : plan.overlay().name());
        tag.putInt("radius", plan.radius());
        ListTag kinds = new ListTag();
        plan.anomalies().forEach(k -> kinds.add(StringTag.valueOf(k.name())));
        tag.put("anomalies", kinds);
        ListTag loot = new ListTag();
        plan.artefacts().forEach(l -> loot.add(StringTag.valueOf(l.name())));
        tag.put("artefacts", loot);
        tag.putLong("variant", plan.variant());
        tag.putString("style", style.name());
        tag.putInt("cx", cx);
        tag.putInt("cz", cz);
    }

    public SitePlan plan() {
        return plan;
    }

    private synchronized SiteLayout layout(WorldGenLevel level, ChunkGenerator generator) {
        if (layout == null) {
            ServerLevel server = level.getLevel();
            GeneratorTerrain terrain = new GeneratorTerrain(generator, level, server.getChunkSource().randomState());
            layout = SiteLayout.build(plan, cx, cz, terrain, style, AnomalySites.templates(server));
        }
        return layout;
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        SitePainter.paint(layout(level, generator), level, box, level.getLevel().getStructureManager());
    }
}
