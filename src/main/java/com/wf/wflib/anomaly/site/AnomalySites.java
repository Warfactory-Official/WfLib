package com.wf.wflib.anomaly.site;

import com.wf.wflib.WFLib;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;


/** Anomaly sites: registration, the density hook, and placing one on demand (the command). */
public final class AnomalySites {
    private AnomalySites() {
    }

    public static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE,
            WFLib.MODID);
    public static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(
            Registries.STRUCTURE_PIECE, WFLib.MODID);
    public static final DeferredHolder<StructureType<?>, StructureType<AnomalySiteStructure>> TYPE = TYPES.register(
            "anomaly_site", () -> () -> AnomalySiteStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> PIECE = PIECES.register(
            "anomaly_site", () -> AnomalySitePiece::new);

    /** How much likelier each tier's sites are at a place than their stock rate (0 = none). */
    @FunctionalInterface
    public interface Density {
        double multiplier(int tier, int x, int z);
    }

    /**
     * Site density by place; 1 everywhere unless a worldgen mod sets it (wfcore does, from WFWorldGen's
     * {@code anomalies} section: targets {@code wflib:anomaly_cluster_tier_<n>}).
     */
    public static Density density = (tier, x, z) -> 1.0D;

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        PIECES.register(modBus);
    }

    /** Room round a site for everything it writes: the fringe, props, a mast's guys. */
    static BoundingBox box(int x, int y, int z, int radius) {
        int r = (int) Math.ceil(radius * SitePainter.FRINGE) + 8;
        return new BoundingBox(x - r, y - 64, z - r, x + r, y + 48, z + r);
    }

    /** Dry in the middle and nearly all round, and not a cliff. */
    static boolean suitable(SiteLayout.Terrain t, int x, int z, int radius) {
        if (t.wet(x, z)) {
            return false;
        }
        int wet = 0;
        int n = 0;
        int lo = Integer.MAX_VALUE;
        int hi = Integer.MIN_VALUE;
        for (double ring : new double[] {0.5D, 0.95D}) {
            for (int i = 0; i < 12; i++) {
                double a = Mth.TWO_PI * i / 12;
                int sx = x + (int) Math.round(Math.cos(a) * radius * ring);
                int sz = z + (int) Math.round(Math.sin(a) * radius * ring);
                wet += t.wet(sx, sz) ? 1 : 0;
                int g = t.ground(sx, sz);
                lo = Math.min(lo, g);
                hi = Math.max(hi, g);
                n++;
            }
        }
        return wet <= n / 8 && hi - lo <= radius * 0.6D + 8;
    }

    /** Template sizes from the server's templates (vanilla's village houses). */
    static SiteLayout.Templates templates(ServerLevel level) {
        return id -> level.getStructureManager().get(id).map(t -> (Vec3i) t.getSize()).orElse(null);
    }

    /** What the command made. */
    public record Made(SitePlan plan, BlockPos centre, int anomalies, int artefacts) {
    }

    /**
     * A site near {@code near} in a live world (the command), trying rings out to {@code reach} blocks for ground that
     * takes one; null if none does. Painted whole, at once.
     */
    @Nullable
    public static Made place(ServerLevel level, BlockPos near, int tier, @Nullable SitePlan.Motif motif,
                             @Nullable SitePlan.Overlay overlay, int reach) {
        RandomSource r = RandomSource.create();
        SiteLayout.Terrain terrain = new LiveTerrain(level);
        for (int ring = 0; ring <= reach; ring += 16) {
            int tries = ring == 0 ? 1 : 8;
            for (int i = 0; i < tries; i++) {
                double a = Mth.TWO_PI * i / tries;
                int x = near.getX() + (int) Math.round(Math.cos(a) * ring);
                int z = near.getZ() + (int) Math.round(Math.sin(a) * ring);
                int y = terrain.ground(x, z);
                SiteStyle style = SiteStyle.fromBiome(level.getBiome(new BlockPos(x, y, z)).unwrapKey()
                        .map(k -> k.location().getPath()).orElse(""));
                SitePlan plan = SitePlan.roll(r, tier, style, motif, overlay);
                if (!suitable(terrain, x, z, plan.radius())) {
                    continue;
                }
                SiteLayout layout = SiteLayout.build(plan, x, z, terrain, style, templates(level));
                SitePainter.paint(layout, level, box(x, y, z, plan.radius()), level.getStructureManager());
                return new Made(plan, new BlockPos(x, y, z), layout.anomalies.size(), layout.loot.size());
            }
        }
        return null;
    }

    /** A live world's ground, read off its blocks (the command builds on what is there now). */
    private record LiveTerrain(ServerLevel level) implements SiteLayout.Terrain {
        @Override
        public int ground(int x, int z) {
            return SitePainter.ground(level, x, z);
        }

        @Override
        public boolean wet(int x, int z) {
            int y = SitePainter.ground(level, x, z);
            return !level.getFluidState(new BlockPos(x, y, z)).isEmpty();
        }
    }
}
