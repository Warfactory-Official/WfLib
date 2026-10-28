package com.wf.wflib.anomaly.site;

import com.wf.wflib.anomaly.site.SitePlan.Motif;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Locale;
import java.util.Map;

/**
 * The biome a site sits in, as far as its look goes: which motifs suit it (a bog in a swamp, a ravine in the
 * badlands) and what its generic dirt and stone become there (red sand and banded terracotta in the badlands, sand
 * and sandstone in the desert), so nothing reads as dropped in from elsewhere.
 */
public enum SiteStyle {
    PLAINS(Map.of()),
    SAVANNA(Map.of(Motif.FISSURES, 1.5D, Motif.BOG, 0.4D)),
    TAIGA(Map.of(Motif.DEAD_FOREST, 2.5D, Motif.GLASS_FIELD, 0.6D)),
    SNOWY(Map.of(Motif.CRATER, 2.0D, Motif.BOG, 0.2D, Motif.FISSURES, 0.6D)),
    DESERT(Map.of(Motif.GLASS_FIELD, 3.0D, Motif.CRATER, 2.0D, Motif.BOG, 0.1D, Motif.DEAD_FOREST, 0.2D,
            Motif.SINKHOLE, 1.5D)),
    BADLANDS(Map.of(Motif.RAVINE, 3.0D, Motif.BOULDER_FIELD, 2.0D, Motif.FISSURES, 2.0D, Motif.ARCHES, 2.0D,
            Motif.BOG, 0.05D, Motif.DEAD_FOREST, 0.2D)),
    JUNGLE(Map.of(Motif.BOG, 2.0D, Motif.DEAD_FOREST, 2.0D, Motif.ARCHES, 2.0D, Motif.GLASS_FIELD, 0.5D)),
    SWAMP(Map.of(Motif.BOG, 4.0D, Motif.DEAD_FOREST, 2.0D, Motif.GLASS_FIELD, 0.3D, Motif.FISSURES, 0.4D));

    /** How much likelier (or not) each motif is here than its stock share. */
    final Map<Motif, Double> lean;

    SiteStyle(Map<Motif, Double> lean) {
        this.lean = lean;
    }

    double lean(Motif m) {
        return lean.getOrDefault(m, 1.0D);
    }

    /** From a biome's id path ({@code minecraft:wooded_badlands} -> BADLANDS). */
    public static SiteStyle fromBiome(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        if (p.contains("badlands") || p.contains("mesa")) {
            return BADLANDS;
        }
        if (p.contains("desert") || p.contains("beach") || p.contains("dune")) {
            return DESERT;
        }
        if (p.contains("snow") || p.contains("frozen") || p.contains("ice") || p.contains("grove")
                || p.contains("tundra") || p.contains("glacier")) {
            return SNOWY;
        }
        if (p.contains("jungle") || p.contains("bamboo") || p.contains("rainforest")) {
            return JUNGLE;
        }
        if (p.contains("swamp") || p.contains("marsh") || p.contains("bog") || p.contains("mangrove")) {
            return SWAMP;
        }
        if (p.contains("taiga") || p.contains("boreal") || p.contains("conifer")) {
            return TAIGA;
        }
        if (p.contains("savanna") || p.contains("steppe") || p.contains("shrub")) {
            return SAVANNA;
        }
        return PLAINS;
    }

    /** Badlands strata: the terracotta band at a height, as the mesa's own cliffs run. */
    private static Block stratum(int y) {
        Block[] bands = {Blocks.TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.TERRACOTTA, Blocks.YELLOW_TERRACOTTA,
                Blocks.BROWN_TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.TERRACOTTA, Blocks.WHITE_TERRACOTTA,
                Blocks.LIGHT_GRAY_TERRACOTTA, Blocks.ORANGE_TERRACOTTA};
        return bands[Math.floorMod(y, bands.length)];
    }

    /** What a generic block becomes here (props and ground; templates keep their own). */
    BlockState restyle(BlockState s, int x, int y, int z) {
        Block b = s.getBlock();
        double h = SiteLayout.hash(x * 3 + y, z, 401);
        return switch (this) {
            case BADLANDS -> {
                if (b == Blocks.COARSE_DIRT || b == Blocks.GRASS_BLOCK || b == Blocks.PODZOL) {
                    yield (h < 0.6D ? Blocks.RED_SAND : h < 0.85D ? Blocks.COARSE_DIRT : Blocks.TERRACOTTA)
                            .defaultBlockState();
                }
                if (b == Blocks.DIRT || b == Blocks.ROOTED_DIRT || b == Blocks.MUD || b == Blocks.PACKED_MUD
                        || b == Blocks.MOSS_BLOCK) {
                    yield (h < 0.5D ? Blocks.RED_SAND : Blocks.TERRACOTTA).defaultBlockState();
                }
                if (b == Blocks.GRAVEL) {
                    yield (h < 0.5D ? Blocks.RED_SAND : Blocks.ORANGE_TERRACOTTA).defaultBlockState();
                }
                if (b == Blocks.STONE || b == Blocks.ANDESITE || b == Blocks.TUFF || b == Blocks.COBBLESTONE
                        || b == Blocks.MOSSY_COBBLESTONE || b == Blocks.DEEPSLATE || b == Blocks.COBBLED_DEEPSLATE) {
                    yield stratum(y).defaultBlockState();
                }
                if (b == Blocks.DARK_OAK_LOG || b == Blocks.STRIPPED_DARK_OAK_LOG) {
                    yield copyAxis(s, Blocks.STRIPPED_ACACIA_LOG);
                }
                yield s;
            }
            case DESERT -> {
                if (b == Blocks.COARSE_DIRT || b == Blocks.GRASS_BLOCK || b == Blocks.PODZOL || b == Blocks.GRAVEL
                        || b == Blocks.ROOTED_DIRT) {
                    yield (h < 0.65D ? Blocks.SAND : h < 0.85D ? Blocks.SANDSTONE : Blocks.COARSE_DIRT)
                            .defaultBlockState();
                }
                if (b == Blocks.DIRT || b == Blocks.MUD || b == Blocks.MOSS_BLOCK) {
                    yield Blocks.SANDSTONE.defaultBlockState();
                }
                if (b == Blocks.STONE || b == Blocks.ANDESITE || b == Blocks.TUFF || b == Blocks.COBBLESTONE
                        || b == Blocks.MOSSY_COBBLESTONE) {
                    yield (h < 0.6D ? Blocks.SANDSTONE : h < 0.85D ? Blocks.SMOOTH_SANDSTONE : Blocks.STONE)
                            .defaultBlockState();
                }
                // Fulgurites in sand are glass.
                if (b == Blocks.TINTED_GLASS) {
                    yield Blocks.GLASS.defaultBlockState();
                }
                yield s;
            }
            case SNOWY -> {
                if (b == Blocks.GRASS_BLOCK || b == Blocks.MOSS_BLOCK || b == Blocks.PODZOL) {
                    yield (h < 0.7D ? Blocks.SNOW_BLOCK : Blocks.COARSE_DIRT).defaultBlockState();
                }
                if (b == Blocks.MUD || b == Blocks.PACKED_MUD) {
                    yield (h < 0.5D ? Blocks.PACKED_ICE : Blocks.COARSE_DIRT).defaultBlockState();
                }
                yield s;
            }
            case JUNGLE, SWAMP -> {
                if (b == Blocks.COBBLESTONE) {
                    yield Blocks.MOSSY_COBBLESTONE.defaultBlockState();
                }
                if (b == Blocks.COARSE_DIRT && h < 0.35D) {
                    yield (this == SWAMP ? Blocks.MUD : Blocks.MOSS_BLOCK).defaultBlockState();
                }
                yield s;
            }
            case TAIGA -> b == Blocks.DARK_OAK_LOG || b == Blocks.STRIPPED_DARK_OAK_LOG
                    ? copyAxis(s, Blocks.STRIPPED_SPRUCE_LOG) : s;
            case SAVANNA -> b == Blocks.DARK_OAK_LOG || b == Blocks.STRIPPED_DARK_OAK_LOG
                    ? copyAxis(s, Blocks.STRIPPED_ACACIA_LOG) : s;
            case PLAINS -> s;
        };
    }

    private static BlockState copyAxis(BlockState from, Block to) {
        BlockState s = to.defaultBlockState();
        return from.hasProperty(net.minecraft.world.level.block.RotatedPillarBlock.AXIS)
                ? s.setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS,
                from.getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS)) : s;
    }
}
