package com.wf.wflib.anomaly.site;

import com.wf.wflib.WFLib;
import com.wf.wflib.anomaly.site.SiteLayout.Column;
import com.wf.wflib.anomaly.site.SiteLayout.LootSpot;
import com.wf.wflib.anomaly.site.SiteLayout.Placed;
import com.wf.wflib.anomaly.site.SiteLayout.Ruin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import com.wf.wflib.artifact.detect.ZoneArtifact;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockAgeProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockRotProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Writes the part of a site that falls in {@code box} (one chunk): the reshaped ground, props, rotted ruins,
 * anomalies and artefacts. Shapes are applied as a change to the real ground under each column (the layout's
 * height less the terrain it was worked out from), so the site's edge meets the land without a step.
 */
final class SitePainter {
    /** Blocks cleared above a new surface: trees, the hill that was there. */
    private static final int CLEAR_UP = 28;
    /** Past the outline (in outlines) the fringe reaches: bare patches thinning into the land. */
    static final double FRINGE = 1.12D;
    /** No shape updates and no drops: in a live world (the command) plants beside an edit would pop off. */
    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private SitePainter() {
    }

    static void paint(SiteLayout l, WorldGenLevel level, BoundingBox box, StructureTemplateManager templates) {
        int reach = (int) Math.ceil(l.radius() * FRINGE) + 1;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = Math.max(box.minX(), l.cx - reach); x <= Math.min(box.maxX(), l.cx + reach); x++) {
            for (int z = Math.max(box.minZ(), l.cz - reach); z <= Math.min(box.maxZ(), l.cz + reach); z++) {
                double u = l.u(x + 0.5D, z + 0.5D);
                if (u > FRINGE) {
                    continue;
                }
                int real = ground(level, x, z);
                if (u > 1.0D) {
                    fringe(l, level, x, z, real, u, at);
                    continue;
                }
                Column c = l.column(x, z);
                int y = real + c.y() - l.terrain.ground(x, z);
                y = Math.max(y, level.getMinBuildHeight() + 4);
                // The land's own surface (grass stays grass, trees stand) unless scorched, reshaped or the motif is
                // bare here.
                boolean keep = c.kind() == SiteLayout.Surface.PLAIN && !l.bare(x, z);
                if (keep && y == real) {
                    continue;
                }
                BlockState top = switch (c.kind()) {
                    case BOWL -> (l.bowlPalette != null ? l.bowlPalette : l.palette).at(l, x, z);
                    case TRENCH -> real - y >= c.trench().depth() * 0.75D ? c.trench().bottom() : c.trench().lip();
                    case PAD -> padTop(level, x, real, z, at);
                    case PLAIN -> keep ? nativeTop(level, x, real, z, at) : l.palette.at(l, x, z);
                };
                BlockState under = c.kind() == SiteLayout.Surface.TRENCH ? c.trench().lip() : l.under;
                boolean asIs = keep || c.kind() == SiteLayout.Surface.PAD;
                for (int k = y + 1; k <= Math.max(real, y) + CLEAR_UP; k++) {
                    if (!level.getBlockState(at.set(x, k, z)).isAir()) {
                        level.setBlock(at, Blocks.AIR.defaultBlockState(), FLAGS);
                    }
                }
                level.setBlock(at.set(x, y, z), asIs ? top : l.style.restyle(top, x, y, z), FLAGS);
                for (int k = y - 1; k >= Math.min(y - 2, real + 1); k--) {
                    level.setBlock(at.set(x, k, z), l.style.restyle(under, x, k, z), FLAGS);
                }
            }
        }
        for (var e : l.props.entrySet()) {
            if (box.isInside(e.getKey())) {
                BlockPos p = e.getKey();
                level.setBlock(p, l.style.restyle(e.getValue(), p.getX(), p.getY(), p.getZ()), FLAGS);
            }
        }
        for (Ruin r : l.ruins) {
            templates.get(r.template()).ifPresent(t -> {
                StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(r.rotation())
                        .setBoundingBox(box).setIgnoreEntities(true)
                        .addProcessor(JigsawReplacementProcessor.INSTANCE)
                        .addProcessor(new BlockRotProcessor(r.integrity()))
                        .addProcessor(new BlockAgeProcessor(r.moss()));
                t.placeInWorld(level, r.origin(), r.origin(), settings, net.minecraft.util.RandomSource.create(
                        r.origin().asLong()), FLAGS);
            });
        }
        for (Placed p : l.anomalies) {
            if (box.isInside(p.pos())) {
                level.setBlock(p.pos(), block(p.kind().id).defaultBlockState(), FLAGS);
            }
        }
        for (LootSpot s : l.loot) {
            if (box.isInside(s.pos())) {
                spawnArtefact(level, s);
            }
        }
    }

    /** An artefact where it lies: hidden till a detector comes near ({@link ZoneArtifact}). */
    private static void spawnArtefact(WorldGenLevel level, LootSpot s) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(WFLib.MODID,
                s.loot().id)));
        level.addFreshEntity(new ZoneArtifact(level.getLevel(), stack, s.pos().getX() + 0.5D, s.pos().getY() + 0.05D,
                s.pos().getZ() + 0.5D));
    }

    static Block block(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(WFLib.MODID, id));
    }

    /** A ruin's pad keeps the ground it had (grass stays grass), unless that was sand on a slope or water. */
    private static BlockState padTop(WorldGenLevel level, int x, int y, int z, BlockPos.MutableBlockPos at) {
        BlockState s = level.getBlockState(at.set(x, y, z));
        return s.is(BlockTags.DIRT) || s.is(Blocks.SAND) || s.is(Blocks.RED_SAND) || s.is(Blocks.SNOW_BLOCK)
                ? s : Blocks.COARSE_DIRT.defaultBlockState();
    }

    /** Whatever solid ground a column had (grass, stone, sand), for ground that is only raised or lowered. */
    private static BlockState nativeTop(WorldGenLevel level, int x, int y, int z, BlockPos.MutableBlockPos at) {
        BlockState s = level.getBlockState(at.set(x, y, z));
        return s.getFluidState().isEmpty() && !vegetation(s) ? s : Blocks.COARSE_DIRT.defaultBlockState();
    }

    /** Bare patches and dead plants past the outline, thinning out over the band. */
    private static void fringe(SiteLayout l, WorldGenLevel level, int x, int z, int y, double u,
                               BlockPos.MutableBlockPos at) {
        double fade = 1.0D - (u - 1.0D) / (FRINGE - 1.0D);
        if (SiteLayout.hash(x, z, 201) + 0.25D * l.noise(x, z, 3.0D) > fade * 0.8D) {
            return;
        }
        BlockState top = level.getBlockState(at.set(x, y, z));
        if (!(top.is(BlockTags.DIRT) || top.is(Blocks.SAND) || top.is(Blocks.SNOW_BLOCK))) {
            return;
        }
        for (int k = 1; k <= 2; k++) {
            if (level.getBlockState(at.set(x, y + k, z)).canBeReplaced()) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), FLAGS);
            }
        }
        level.setBlock(at.set(x, y, z), l.style.restyle(l.palette.at(l, x, z), x, y, z), FLAGS);
        if (SiteLayout.hash(x, z, 202) < 0.06D) {
            level.setBlock(at.set(x, y + 1, z), Blocks.DEAD_BUSH.defaultBlockState(), FLAGS);
        }
    }

    /** Top of the solid ground at a column, under trees and plants (water counts as ground: a pond's surface). */
    static int ground(WorldGenLevel level, int x, int z) {
        Heightmap.Types top = level instanceof WorldGenRegion ? Heightmap.Types.WORLD_SURFACE_WG
                : Heightmap.Types.WORLD_SURFACE;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(x, level.getHeight(top, x, z), z);
        while (at.getY() > level.getMinBuildHeight()) {
            at.move(0, -1, 0);
            BlockState s = level.getBlockState(at);
            if (!s.getFluidState().isEmpty() || !vegetation(s)) {
                return at.getY();
            }
        }
        return level.getMinBuildHeight();
    }

    static boolean vegetation(BlockState s) {
        return s.isAir() || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.canBeReplaced() || !s.blocksMotion()
                || s.is(Blocks.BAMBOO) || s.is(Blocks.CACTUS) || s.is(BlockTags.SNOW) && !s.is(Blocks.SNOW_BLOCK);
    }
}
