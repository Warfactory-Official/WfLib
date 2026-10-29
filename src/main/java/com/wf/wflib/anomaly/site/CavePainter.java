package com.wf.wflib.anomaly.site;

import com.wf.wflib.anomaly.site.SiteLayout.LootSpot;
import com.wf.wflib.anomaly.site.SiteLayout.Placed;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Writes the part of a cave field in {@code box} (one chunk): the chamber cut or filled to the layout's floor and
 * dome, then anomalies and artefacts. A field inside a natural cave only reshapes the rock it overlaps.
 */
final class CavePainter {
    /** Fill under a floor that has none, at most this deep. */
    private static final int FOOT = 10;

    private CavePainter() {
    }

    static void paint(CaveLayout l, WorldGenLevel level, BoundingBox box) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int reach = l.reach();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;
        for (int x = Math.max(box.minX(), l.cx - reach); x <= Math.min(box.maxX(), l.cx + reach); x++) {
            for (int z = Math.max(box.minZ(), l.cz - reach); z <= Math.min(box.maxZ(), l.cz + reach); z++) {
                if (l.u(x, z) >= 1.0D) {
                    continue;
                }
                int f = l.floor(x, z);
                int c = Math.min(l.ceiling(x, z), maxY);
                for (int y = Math.max(f, minY); y <= c; y++) {
                    at.set(x, y, z);
                    boolean wall = y == f || y == c;
                    BlockState s = level.getBlockState(at);
                    if (wall) {
                        if (!solid(s)) {
                            level.setBlock(at, filler(x, y, z), SitePainter.FLAGS);
                        }
                    } else if (!s.isAir()) {
                        level.setBlock(at, Blocks.AIR.defaultBlockState(), SitePainter.FLAGS);
                    }
                }
                for (int y = f - 1; y >= Math.max(f - FOOT, minY); y--) {
                    at.set(x, y, z);
                    if (solid(level.getBlockState(at))) {
                        break;
                    }
                    level.setBlock(at, filler(x, y, z), SitePainter.FLAGS);
                }
            }
        }
        for (Placed p : l.anomalies) {
            if (box.isInside(p.pos())) {
                level.setBlock(p.pos(), SitePainter.block(p.kind().id).defaultBlockState(), SitePainter.FLAGS);
            }
        }
        for (LootSpot s : l.loot) {
            if (box.isInside(s.pos())) {
                SitePainter.spawnArtefact(level, s);
            }
        }
    }

    private static boolean solid(BlockState s) {
        return s.getFluidState().isEmpty() && s.blocksMotion();
    }

    private static BlockState filler(int x, int y, int z) {
        double h = SiteLayout.hash(x, z + y * 131, 301);
        return (h < 0.10D ? Blocks.TUFF : h < 0.15D ? Blocks.SMOOTH_BASALT : Blocks.DEEPSLATE).defaultBlockState();
    }
}
