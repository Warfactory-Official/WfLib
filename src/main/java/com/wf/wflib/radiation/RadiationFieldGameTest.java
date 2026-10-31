package com.wf.wflib.radiation;

import com.wf.wflib.hazard.api.ContaminationUtil;
import com.wf.wflib.hazard.api.LivingHazardProps;
import com.wf.wflib.explosion.api.MiniNuke;
import com.wf.wflib.explosion.api.EntityNukeTorex;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.GlobalPalette;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import com.wf.wflib.radiation.api.RadiationConfig;
import com.wf.wflib.core.api.WFLib;

/**
 * NTM NEXT ChunkRadiation / Emission / ConstantSource / RadShieldBlock / RadAbsorber / AreaRadiation server
 * tests, one {@link RadiationLane} each. NTM blocks -> vanilla stand-ins marked here and unmarked on release:
 * fallout (0.04, 100), schrabidium ore (0.005, 0), absorbers (-0.25 / -1000, 0), shield blocks -> resistant.
 */
@PrefixGameTestTemplate(false)
public class RadiationFieldGameTest {

    private static final String TEMPLATE = "empty";
    static final String BATCH = "wflib_radiation";

    private static final BlockPos FLOOR = new BlockPos(1, 1, 1);
    private static final BlockPos SITE = new BlockPos(1, 2, 1);
    private static final BlockPos SEC0 = new BlockPos(1, 2, 1);
    private static final BlockPos SEC1 = new BlockPos(1, 18, 1);
    private static final BlockPos SEC2 = new BlockPos(1, 34, 1);
    private static final BlockPos SEC3 = new BlockPos(1, 50, 1);
    private static final BlockPos SEC4 = new BlockPos(1, 66, 1);
    private static final BlockPos SEC5 = new BlockPos(1, 82, 1);
    private static final BlockPos SPLIT = new BlockPos(1, 98, 1);

    private static final Block FALLOUT = Blocks.RAW_IRON_BLOCK;
    private static final Block FALLOUT_REMOVED = Blocks.RAW_GOLD_BLOCK;
    private static final Block CONSTANT_ADD = Blocks.RAW_COPPER_BLOCK;
    private static final Block ABSORBER = Blocks.WAXED_COPPER_BLOCK;
    private static final Block ABSORBER_ELITE = Blocks.WAXED_EXPOSED_COPPER;
    private static final Block SHIELD = Blocks.GILDED_BLACKSTONE;
    private static final Block SHIELD_DOOR = Blocks.IRON_TRAPDOOR;
    private static final Block WALL = Blocks.REINFORCED_DEEPSLATE;
    private static final BlockState REMAP_WALL = Blocks.CRYING_OBSIDIAN.defaultBlockState();

    private static final double FALLOUT_SATURATION = 100.0D;

    // ------------------------------------------------------------------ emission

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void emissionShippedPathEmitsAndAbsorbsOnTheSameRule(GameTestHelper helper) {
        double seed = 500.0D;
        double saturation = 100.0D;
        double absorberRate = 50.0D;
        RadiationLane lane = new RadiationLane(helper, 0);
        lane.run(() -> {
            ServerLevel level = lane.level;
            level.setBlockAndUpdate(lane.pos(FLOOR), Blocks.STONE.defaultBlockState());
            BlockPos site = lane.pos(SITE);
            RadiationSystemNT.setRadForCoord(level, site, 0D);
            helper.runAfterDelay(3, () -> {
                RadiationSystemNT.incrementRad(level, site, seed);
                helper.runAfterDelay(3, () -> {
                    double seeded = RadiationSystemNT.getRadForCoord(level, site);
                    helper.assertTrue(seeded > seed * 0.9D, "the fixture must land its seed at the site; got " + seeded);
                    RadiationSystemNT.incrementRad(level, site, seed, saturation);
                    helper.runAfterDelay(3, () -> {
                        double afterEmitter = RadiationSystemNT.getRadForCoord(level, site);
                        helper.assertTrue(afterEmitter < seeded,
                                "an emitter below the voxel must not raise it at all; " + seeded + " -> " + afterEmitter);
                        RadiationSystemNT.incrementRad(level, site, -absorberRate, saturation);
                        helper.runAfterDelay(3, () -> {
                            double afterAbsorber = RadiationSystemNT.getRadForCoord(level, site);
                            double predicted = afterEmitter + (absorberRate / saturation) * (saturation - afterEmitter);
                            helper.assertTrue(Math.abs(afterAbsorber - predicted) < predicted * 0.1D,
                                    "a ceiling absorber must take |e|/|s| of the distance to its saturation; predicted "
                                            + predicted + " from " + afterEmitter + ", got " + afterAbsorber);
                            helper.assertTrue(afterAbsorber > saturation,
                                    "one step of a |e|/|s| = 0.5 absorber must not pass its saturation; got " + afterAbsorber);
                            lane.release();
                            helper.succeed();
                        });
                    });
                });
            });
        });
    }

    // ------------------------------------------------------------------ nukes

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void falloutDrivesTheSurfacePocketAndDecays(GameTestHelper helper) {
        double peak = 200.0D;
        RadiationLane lane = new RadiationLane(helper, 16);
        lane.run(() -> {
            ServerLevel level = lane.level;
            BlockPos centre = lane.pos(8, 0, 8);
            long key = ChunkPos.asLong(centre);
            FalloutData.deposit(level, centre.getX(), centre.getZ(), 12.0D, peak);
            FalloutData fallout = FalloutData.get(level);
            long now = level.getGameTime();
            helper.assertTrue(Math.abs(fallout.strengthAt(key, now) - peak) < 1.0e-6D, "ground zero must take the peak");
            helper.assertTrue(fallout.strengthAt(ChunkPos.asLong((centre.getX() >> 4) + 1, centre.getZ() >> 4), now) == 0D,
                    "a chunk centre past the range must take nothing");
            long halfLife = (long) (RadiationConfig.get().falloutHalfLifeSeconds() * 20.0D);
            helper.assertTrue(Math.abs(fallout.strengthAt(key, now + halfLife) - peak / 2) < 1.0e-6D,
                    "one half-life must halve the source");
            BlockPos surface = new BlockPos(centre.getX(),
                    level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre.getX(), centre.getZ()), centre.getZ());
            helper.runAfterDelay(45, () -> {
                double rad;
                try {
                    rad = RadiationSystemNT.getRadForCoord(level, surface);
                } finally {
                    FalloutData.clear(level);
                }
                helper.assertTrue(rad > peak / 4, "a loaded fallout chunk must charge its surface pocket; got " + rad);
                lane.release();
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void miniNukeSeedsTheField(GameTestHelper helper) {
        RadiationLane lane = new RadiationLane(helper, 17);
        lane.run(() -> {
            ServerLevel level = lane.level;
            BlockPos site = lane.pos(8, 2, 8);
            // Lane section 0 written on the load tick reads 0 two ticks on; primed + 3 ticks holds.
            RadiationSystemNT.setRadForCoord(level, site, 0D);
            helper.runAfterDelay(3, () -> {
                MiniNuke.MukeParams params = new MiniNuke.MukeParams();
                params.safe = true;
                params.killRadius = 0F;
                params.radiationLevel = 3F;
                MiniNuke.detonate(level, Vec3.atCenterOf(site), params);
                level.getEntitiesOfClass(EntityNukeTorex.class, new AABB(site).inflate(8.0D)).forEach(Entity::discard);
                helper.runAfterDelay(3, () -> {
                    double rad = RadiationSystemNT.getRadForCoord(level, site);
                    helper.assertTrue(rad > 25.0D, "a level-3 mini nuke must seed ~50 RAD at its centre; got " + rad);
                    lane.release();
                    helper.succeed();
                });
            });
        });
    }

    // ------------------------------------------------------------------ constant sources

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 260)
    public static void constantSourceFalloutEmitsWithoutBeingTicked(GameTestHelper helper) {
        RadiationLane lane = new RadiationLane(helper, 1);
        lane.run(() -> {
            ServerLevel level = lane.level;
            RadiationLane.markRadSource(FALLOUT, 0.04D, FALLOUT_SATURATION);
            level.setBlockAndUpdate(lane.pos(FLOOR), Blocks.STONE.defaultBlockState());
            BlockPos site = lane.pos(SITE);
            RadiationSystemNT.setRadForCoord(level, site, 0D);
            helper.runAfterDelay(3, () -> {
                level.setBlockAndUpdate(site, FALLOUT.defaultBlockState());
                helper.runAfterDelay(100, () -> {
                    try {
                        double climbed = RadiationSystemNT.getRadForCoord(level, site);
                        helper.assertTrue(climbed > 1.0D,
                                "a source block must raise the field it sits in without being ticked; after 100 ticks "
                                        + "the section reads " + climbed);
                        helper.assertTrue(climbed <= FALLOUT_SATURATION,
                                "and must not pass its own saturation of " + FALLOUT_SATURATION + "; it reads " + climbed);
                    } finally {
                        level.setBlockAndUpdate(site, Blocks.AIR.defaultBlockState());
                        RadiationLane.unmarkRadSource(FALLOUT);
                        RadiationSystemNT.markSectionForRebuild(level, site);
                    }
                    lane.release();
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 760)
    public static void constantSourceRemovingTheBlockStopsTheEmission(GameTestHelper helper) {
        RadiationLane lane = new RadiationLane(helper, 2);
        lane.run(() -> {
            ServerLevel level = lane.level;
            RadiationLane.markRadSource(FALLOUT_REMOVED, 0.04D, FALLOUT_SATURATION);
            level.setBlockAndUpdate(lane.pos(FLOOR), Blocks.STONE.defaultBlockState());
            BlockPos site = lane.pos(SITE);
            RadiationSystemNT.setRadForCoord(level, site, 0D);
            helper.runAfterDelay(3, () -> {
                level.setBlockAndUpdate(site, FALLOUT_REMOVED.defaultBlockState());
                helper.runAfterDelay(100, () -> {
                    double withSource = RadiationSystemNT.getRadForCoord(level, site);
                    helper.assertTrue(withSource > 1.0D,
                            "the fixture must land a climbing field before removing the source; got " + withSource);
                    level.setBlockAndUpdate(site, Blocks.AIR.defaultBlockState());
                    RadiationSystemNT.setRadForCoord(level, site, 0D);
                    helper.runAfterDelay(5, () -> {
                        RadiationSystemNT.setRadForCoord(level, site, 0D);
                        helper.runAfterDelay(100, () -> {
                            double without = RadiationSystemNT.getRadForCoord(level, site);
                            RadiationLane.unmarkRadSource(FALLOUT_REMOVED);
                            helper.assertTrue(without < withSource * 0.25D,
                                    "removing the block must stop the emission; the same window climbed to " + without
                                            + " against " + withSource + " with the block in place");
                            lane.release();
                            helper.succeed();
                        });
                    });
                });
            });
        });
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 700)
    public static void constantSourceConstantAddClimbsWithoutATarget(GameTestHelper helper) {
        RadiationLane lane = new RadiationLane(helper, 3);
        lane.run(() -> {
            ServerLevel level = lane.level;
            RadiationLane.markRadSource(CONSTANT_ADD, 0.005D, 0.0D);
            level.setBlockAndUpdate(lane.pos(FLOOR), Blocks.STONE.defaultBlockState());
            BlockPos site = lane.pos(SITE);
            RadiationSystemNT.setRadForCoord(level, site, 0D);
            helper.runAfterDelay(3, () -> {
                level.setBlockAndUpdate(site, CONSTANT_ADD.defaultBlockState());
                helper.runAfterDelay(120, () -> {
                    double early = RadiationSystemNT.getRadForCoord(level, site);
                    helper.assertTrue(early > 0.0D, "a constant add must raise the field it sits in; it reads " + early);
                    helper.runAfterDelay(120, () -> {
                        double late = RadiationSystemNT.getRadForCoord(level, site);
                        level.setBlockAndUpdate(site, Blocks.AIR.defaultBlockState());
                        RadiationLane.unmarkRadSource(CONSTANT_ADD);
                        RadiationSystemNT.markSectionForRebuild(level, site);
                        helper.assertTrue(late - early > 0.05D,
                                "a constant add has no target to flatten against, so the field must keep gaining across "
                                        + "the second window; it read " + early + " then " + late);
                        lane.release();
                        helper.succeed();
                    });
                });
            });
        });
    }

    // ------------------------------------------------------------------ chunk field

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 140)
    public static void chunkRadiationSetAndReadChunkRad(GameTestHelper helper) {
        RadiationLane lane = new RadiationLane(helper, 4);
        lane.run(() -> {
            ServerLevel level = lane.level;
            BlockPos pos = lane.pos(SEC0);
            RadiationSystemNT.setRadForCoord(level, pos, 0D);
            helper.runAfterDelay(2, () -> {
                RadiationSystemNT.incrementRad(level, pos, 100D);
                helper.succeedWhen(() -> {
                    double rad = RadiationSystemNT.getRadForCoord(level, pos);
                    helper.assertTrue(rad > 0D, "getRadForCoord must return > 0 after incrementRad; got " + rad);
                    lane.release();
                });
            });
        });
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 140)
    public static void chunkRadiationDiffusesToAdjacentSection(GameTestHelper helper) {
        RadiationLane lane = new RadiationLane(helper, 5);
        lane.run(() -> {
            ServerLevel level = lane.level;
            BlockPos src = lane.pos(SEC2);
            BlockPos dst = lane.pos(SEC3);
            RadiationSystemNT.setRadForCoord(level, src, 0D);
            RadiationSystemNT.setRadForCoord(level, dst, 0D);
            helper.runAfterDelay(2, () -> {
                RadiationSystemNT.incrementRad(level, src, 10000D);
                RadiationSystemNT.setRadForCoord(level, dst, 0D);
                helper.succeedWhen(() -> {
                    double got = RadiationSystemNT.getRadForCoord(level, dst);
                    helper.assertTrue(got > 0D, "rad must diffuse from section 2 to section 3; dst got " + got);
                    lane.release();
                });
            });
        });
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 140)
    public static void chunkRadiationDiffusesAcrossChunkBoundary(GameTestHelper helper) {
        RadiationLane lane = new RadiationLane(helper, 6);
        lane.run(() -> {
            ServerLevel level = lane.level;
            BlockPos anchor = lane.pos(FLOOR);
            int slot = level.getSectionsCount() - 2;
            helper.assertTrue(slot > 15, "chunk-boundary test requires a variable-height slot above 15; got " + slot);
            int sourceX = (anchor.getX() >> 4 << 4) + 15;
            int sourceY = SectionPos.sectionToBlockCoord(level.getSectionYFromSectionIndex(slot), 2);
            BlockPos src = new BlockPos(sourceX, sourceY, anchor.getZ());
            BlockPos dst = src.east();
            RadiationSystemNT.setRadForCoord(level, src, 0D);
            RadiationSystemNT.setRadForCoord(level, dst, 0D);
            helper.runAfterDelay(2, () -> {
                RadiationSystemNT.incrementRad(level, src, 10000D);
                RadiationSystemNT.setRadForCoord(level, dst, 0D);
                helper.succeedWhen(() -> {
                    double got = RadiationSystemNT.getRadForCoord(level, dst);
                    helper.assertTrue(got > 0D, "rad must diffuse across the east chunk boundary; dst got " + got);
                    lane.release();
                });
            });
        });
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 140)
    public static void chunkRadiationGlobalPaletteResistantPlaneSplitsSection(GameTestHelper helper) {
        RadiationLane lane = new RadiationLane(helper, 7);
        lane.run(() -> {
            ServerLevel level = lane.level;
            BlockPos anchor = lane.pos(SEC5);
            LevelChunk chunk = level.getChunkAt(anchor);
            int slot = chunk.getSectionIndex(anchor.getY());
            LevelChunkSection section = chunk.getSections()[slot];

            List<BlockState> states = new ArrayList<>(300);
            BlockState resistant = null;
            for (int id = 0, size = Block.BLOCK_STATE_REGISTRY.size(); id < size && states.size() < 300; id++) {
                BlockState state = Block.BLOCK_STATE_REGISTRY.byId(id);
                if (state == null) continue;
                states.add(state);
                if (id > 255 && resistant == null) resistant = state;
            }
            helper.assertTrue(states.size() == 300, "global-palette test requires 300 registered block states");
            helper.assertTrue(resistant != null, "global-palette test requires a block-state ID above 255");

            int stateIndex = 0;
            for (int y = 0; y < 16 && stateIndex < states.size(); y++) {
                for (int z = 0; z < 16 && stateIndex < states.size(); z++) {
                    for (int x = 0; x < 16 && stateIndex < states.size(); x++) {
                        if (x == 8) continue;
                        section.setBlockState(x, y, z, states.get(stateIndex++), false);
                    }
                }
            }
            BlockState wall = resistant;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) section.setBlockState(8, y, z, wall, false);
            }
            section.recalcBlockCounts();
            helper.assertTrue(section.getStates().data.palette() instanceof GlobalPalette<?>,
                    "section must use GlobalPalette after 300 distinct states");

            boolean added = RadiationLane.markRadResistant(wall);
            int baseX = chunk.getPos().getMinBlockX();
            int baseY = SectionPos.sectionToBlockCoord(level.getSectionYFromSectionIndex(slot));
            int baseZ = chunk.getPos().getMinBlockZ();
            BlockPos west = new BlockPos(baseX + 4, baseY + 8, baseZ + 8);
            BlockPos east = new BlockPos(baseX + 12, baseY + 8, baseZ + 8);
            RadiationSystemNT.markSectionForRebuild(level, west);

            helper.runAfterDelay(2, () -> {
                RadiationSystemNT.setRadForCoord(level, east, 0D);
                RadiationSystemNT.setRadForCoord(level, west, 100D);
                helper.runAfterDelay(2, () -> {
                    try {
                        double westRad = RadiationSystemNT.getRadForCoord(level, west);
                        double eastRad = RadiationSystemNT.getRadForCoord(level, east);
                        helper.assertTrue(westRad > 0D, "west pocket must retain the injected radiation; got " + westRad);
                        // Not exactly zero: diffusion around the plane through neighbour sections.
                        helper.assertTrue(eastRad < westRad * 1.0e-3D,
                                "high-ID resistant plane must isolate the east pocket; west=" + westRad + " east="
                                        + eastRad + " (" + (eastRad / westRad) + " of west)");
                    } finally {
                        RadiationLane.unmarkRadResistant(wall, added);
                        clearSection(section);
                        RadiationSystemNT.markSectionForRebuild(level, west);
                    }
                    lane.release();
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 1200)
    public static void chunkRadiationDecaysOverTime(GameTestHelper helper) {
        RadiationLane lane = new RadiationLane(helper, 10);
        lane.run(() -> {
            ServerLevel level = lane.level;
            level.setBlockAndUpdate(lane.pos(FLOOR), Blocks.STONE.defaultBlockState());
            BlockPos pos = lane.pos(SEC1);
            RadiationSystemNT.setRadForCoord(level, pos, 0D);
            helper.runAfterDelay(2, () -> {
                RadiationSystemNT.setRadForCoord(level, pos, 50000D);
                helper.runAfterDelay(2, () -> {
                    double baseline = RadiationSystemNT.getRadForCoord(level, pos);
                    helper.assertTrue(baseline > 0D, "baseline must be > 0 after set; got " + baseline);
                    helper.runAfterDelay(400, () -> {
                        double later = RadiationSystemNT.getRadForCoord(level, pos);
                        helper.assertTrue(later < baseline,
                                "rad must decrease over 400 ticks; baseline=" + baseline + " later=" + later);
                        lane.release();
                        helper.succeed();
                    });
                });
            });
        });
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 140)
    public static void chunkRadiationEntityAbsorbsChunkRad(GameTestHelper helper) {
        RadiationLane lane = new RadiationLane(helper, 11);
        lane.run(() -> {
            ServerLevel level = lane.level;
            BlockPos pos = lane.pos(SEC4);
            RadiationSystemNT.setRadForCoord(level, pos, 0D);
            helper.runAfterDelay(2, () -> {
                RadiationSystemNT.setRadForCoord(level, pos, 1D);
                Pig pig = EntityType.PIG.create(level);
                pig.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
                // Held in the seeded section; NTM's pig falls through it.
                pig.setNoAi(true);
                pig.setNoGravity(true);
                pig.setPersistenceRequired();
                level.addFreshEntity(pig);
                helper.succeedWhen(() -> {
                    helper.assertTrue(pig.isAlive(), "pig must survive 1-rad field; dose is sub-lethal");
                    double entityRad = LivingHazardProps.getRadiation(pig);
                    helper.assertTrue(entityRad > 0D, "pig must accumulate radiation from chunk field; got " + entityRad);
                    pig.discard();
                    lane.release();
                });
            });
        });
    }

    // ------------------------------------------------------------------ persistence

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 140)
    public static void chunkRadiationPersistenceFmt8RoundTripAndStrictRejection(GameTestHelper helper) {
        RadiationLane lane = new RadiationLane(helper, 8);
        lane.run(() -> {
            ServerLevel level = lane.level;
            BlockPos pos = lane.pos(SEC0);
            LevelChunk chunk = level.getChunkAt(pos);
            RadiationSystemNT.setRadForCoord(level, pos, 321.25D);
            helper.runAfterDelay(2, () -> {
                double expected = RadiationSystemNT.getRadForCoord(level, pos);
                byte[] payload = encode(level, chunk);
                helper.assertTrue(payload[3] == 8, "radiation persistence must emit FMT 8");
                RadiationSystemNT.setRadForCoord(level, pos, 0D);
                helper.runAfterDelay(2, () -> {
                    double cleared = RadiationSystemNT.getRadForCoord(level, pos);
                    helper.assertTrue(cleared < expected * 0.01D,
                            "the fixture must clear the density before the restore means anything; got " + cleared);
                    try {
                        apply(level, chunk, payload);
                    } catch (Exception ex) {
                        helper.fail("FMT 8 round-trip failed: " + ex);
                        return;
                    }
                    helper.runAfterDelay(2, () -> {
                        double restored = RadiationSystemNT.getRadForCoord(level, pos);
                        helper.assertTrue(restored > expected * 0.9D && restored <= expected,
                                "FMT 8 must restore the snapshotted density, less the sweep that lands it; expected="
                                        + expected + " got=" + restored);
                        strictRejections(helper, level, chunk, payload);
                        lane.release();
                        helper.succeed();
                    });
                });
            });
        });
    }

    private static void strictRejections(GameTestHelper helper, ServerLevel level, LevelChunk chunk, byte[] payload) {
        byte[] fmt7 = payload.clone();
        fmt7[3] = 7;
        helper.assertTrue(rejects(level, chunk, fmt7), "FMT 7 must be rejected");
        byte[] trailing = Arrays.copyOf(payload, payload.length + 1);
        helper.assertTrue(rejects(level, chunk, trailing), "FMT 8 trailing bytes must be rejected");
        byte[] foreignSection = payload.clone();
        Arrays.fill(foreignSection, 5, 9, (byte) 0x7F);
        helper.assertTrue(rejects(level, chunk, foreignSection), "FMT 8 sections outside the dimension must be rejected");

        ByteBuffer corrupt = ByteBuffer.allocate(4 + 1 + 4 + 1 + 2 + 2 + 512 + 10);
        corrupt.put((byte) 'N').put((byte) 'T').put((byte) 'X').put((byte) 8);
        corrupt.put((byte) 1);
        corrupt.putInt(level.getMinSection());
        corrupt.put((byte) 1);
        corrupt.putShort((short) 1);
        corrupt.putShort((short) 1);
        for (int word = 0; word < 64; word++) {
            long w = 0L;
            int base = word << 6;
            for (int bit = 0; bit < 64; bit++) {
                if (((base + bit) & 0xF) == 8) w |= 1L << bit;
            }
            corrupt.putLong(w);
        }
        corrupt.putShort((short) 0);
        corrupt.putDouble(100.0D);
        try {
            apply(level, chunk, corrupt.array());
            helper.fail("mask/pocket-count mismatch must be rejected");
        } catch (DecodeException rejected) {
            // expected
        } catch (RuntimeException ex) {
            helper.fail("mask/pocket-count mismatch must throw DecodeException, not "
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 140)
    public static void chunkRadiationPersistenceRemapsAcrossTopologyChange(GameTestHelper helper) {
        RadiationLane lane = new RadiationLane(helper, 9);
        lane.run(() -> {
            ServerLevel level = lane.level;
            BlockPos anchor = lane.pos(SEC2);
            LevelChunk chunk = level.getChunkAt(anchor);
            int slot = chunk.getSectionIndex(anchor.getY());
            LevelChunkSection section = chunk.getSections()[slot];
            int baseX = chunk.getPos().getMinBlockX();
            int baseY = SectionPos.sectionToBlockCoord(level.getSectionYFromSectionIndex(slot));
            int baseZ = chunk.getPos().getMinBlockZ();
            BlockPos west = new BlockPos(baseX + 4, baseY + 8, baseZ + 8);
            BlockPos east = new BlockPos(baseX + 12, baseY + 8, baseZ + 8);

            RadiationSystemNT.setRadForCoord(level, west, 100D);
            helper.runAfterDelay(2, () -> {
                byte[] payload = encode(level, chunk);
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) section.setBlockState(8, y, z, REMAP_WALL, false);
                }
                section.recalcBlockCounts();
                boolean added = RadiationLane.markRadResistant(REMAP_WALL);
                try {
                    RadiationSystemNT.markSectionForRebuild(level, west);
                    apply(level, chunk, payload);
                } catch (Exception ex) {
                    restorePlane(level, section, west, added);
                    helper.fail("topology remap failed: " + ex);
                    return;
                }
                // Plane comes down after the read: a remap lands at the rebuild that sees it.
                helper.runAfterDelay(2, () -> {
                    try {
                        double westRad = RadiationSystemNT.getRadForCoord(level, west);
                        double eastRad = RadiationSystemNT.getRadForCoord(level, east);
                        helper.assertTrue(westRad > 0D && eastRad > 0D,
                                "uniform source mass must remap into both split pockets; west=" + westRad + " east=" + eastRad);
                        double split = Math.abs(westRad - eastRad) / Math.max(westRad, eastRad);
                        helper.assertTrue(split < 0.02D, "symmetric split must receive near-equal density; west="
                                + westRad + " east=" + eastRad + " (" + (split * 100D) + "% apart)");
                    } finally {
                        restorePlane(level, section, west, added);
                    }
                    lane.release();
                    helper.succeed();
                });
            });
        });
    }

    private static void restorePlane(ServerLevel level, LevelChunkSection section, BlockPos inside, boolean added) {
        RadiationLane.unmarkRadResistant(REMAP_WALL, added);
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) section.setBlockState(8, y, z, Blocks.AIR.defaultBlockState(), false);
        }
        section.recalcBlockCounts();
        RadiationSystemNT.markSectionForRebuild(level, inside);
    }

    private static byte[] encode(ServerLevel level, LevelChunk chunk) {
        RadiationSystemNT.awaitSimulation();
        WorldRadiationData data = RadiationSystemNT.getWorldRadData(level);
        int id = data.onChunkLoaded(chunk.getPos().x, chunk.getPos().z, chunk);
        data.rebuildLoadedSections();
        data.applyAndClearQueuedWrites(id);
        byte[] payload = data.tryEncodePayload(chunk.getPos().toLong());
        if (payload == null) throw new IllegalStateException("Expected a nonempty radiation payload");
        return payload;
    }

    /** Payload read before the rebuild: a staged row attaches to its section when that section arrives. */
    private static void apply(ServerLevel level, LevelChunk chunk, byte[] payload) throws DecodeException {
        RadiationSystemNT.awaitSimulation();
        byte[] verified = RadiationSystemNT.verifyPayload(payload);
        if (verified == null) throw new DecodeException("Unexpected empty payload");
        WorldRadiationData data = RadiationSystemNT.getWorldRadData(level);
        data.onChunkLoaded(chunk.getPos().x, chunk.getPos().z, chunk);
        data.readPayload(chunk.getPos().x, chunk.getPos().z, verified);
        data.rebuildLoadedSections();
    }

    private static boolean rejects(ServerLevel level, LevelChunk chunk, byte[] payload) {
        try {
            apply(level, chunk, payload);
            return false;
        } catch (DecodeException | RuntimeException expected) {
            return true;
        }
    }

    // ------------------------------------------------------------------ shielding

    /** NTM ported shield roster -> one resistant block + a state-filtered "door" (closed seals, open does not). */
    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 140)
    public static void radShieldBlocksRefuseTheField(GameTestHelper helper) {
        RadiationLane lane = new RadiationLane(helper, 12);
        lane.run(() -> {
            ServerLevel level = lane.level;
            BlockPos probe = lane.pos(SPLIT);
            BlockState air = Blocks.AIR.defaultBlockState();
            RadiationLane.markRadResistant(SHIELD, s -> true);
            RadiationLane.markRadResistant(SHIELD_DOOR, s -> !s.getValue(TrapDoorBlock.OPEN));
            List<BlockState> refuse = List.of(SHIELD.defaultBlockState(),
                    SHIELD_DOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, false));
            List<BlockState> accept = List.of(Blocks.STONE.defaultBlockState(),
                    SHIELD_DOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, true));
            RadiationSystemNT.setRadForCoord(level, probe, 0D);
            helper.runAfterDelay(2, () -> helper.succeedWhen(() -> {
                level.setBlock(probe, air, Block.UPDATE_CLIENTS);
                double seed = RadiationSystemNT.getRadForCoord(level, probe);
                RadiationSystemNT.incrementRad(level, probe, 100D);
                helper.assertTrue(seed > 0D, "the probe must hold a field before either side means anything");
                try {
                    for (BlockState state : refuse) {
                        level.setBlock(probe, state, Block.UPDATE_CLIENTS);
                        double rad = RadiationSystemNT.getRadForCoord(level, probe);
                        helper.assertTrue(rad == 0D, state + " shields and must refuse the field; got " + rad);
                    }
                    for (BlockState state : accept) {
                        level.setBlock(probe, state, Block.UPDATE_CLIENTS);
                        double rad = RadiationSystemNT.getRadForCoord(level, probe);
                        helper.assertTrue(rad > 0D, state + " does not shield and must read the field; got " + rad);
                    }
                } finally {
                    level.setBlock(probe, air, Block.UPDATE_CLIENTS);
                    RadiationLane.unmarkRadResistant(SHIELD);
                    RadiationLane.unmarkRadResistant(SHIELD_DOOR);
                    RadiationSystemNT.markSectionForRebuild(level, probe);
                }
                lane.release();
            }));
        });
    }

    /** NTM reinforced_ducrete plane; set through the level, so the setBlockState hook must rebuild the section. */
    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 140)
    public static void radShieldPlaneSplitsASection(GameTestHelper helper) {
        RadiationLane lane = new RadiationLane(helper, 13);
        lane.run(() -> {
            ServerLevel level = lane.level;
            RadiationLane.markRadResistant(WALL, s -> true);
            BlockPos anchor = lane.pos(SPLIT);
            LevelChunk chunk = level.getChunkAt(anchor);
            int baseX = chunk.getPos().getMinBlockX();
            int baseY = SectionPos.blockToSectionCoord(anchor.getY()) << 4;
            int baseZ = chunk.getPos().getMinBlockZ();
            BlockState wall = WALL.defaultBlockState();
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) level.setBlock(new BlockPos(baseX + 8, baseY + y, baseZ + z), wall, Block.UPDATE_CLIENTS);
            }
            BlockPos west = new BlockPos(baseX + 4, baseY + 8, baseZ + 8);
            BlockPos east = new BlockPos(baseX + 12, baseY + 8, baseZ + 8);
            helper.runAfterDelay(2, () -> {
                RadiationSystemNT.setRadForCoord(level, east, 0D);
                RadiationSystemNT.setRadForCoord(level, west, 100D);
                helper.runAfterDelay(2, () -> {
                    try {
                        double westRad = RadiationSystemNT.getRadForCoord(level, west);
                        double eastRad = RadiationSystemNT.getRadForCoord(level, east);
                        helper.assertTrue(westRad > 0D, "the west pocket must keep the injected field; got " + westRad);
                        helper.assertTrue(eastRad < westRad * 1.0e-3D, "a resistant wall must isolate the east pocket; west="
                                + westRad + " east=" + eastRad + " (" + (eastRad / westRad) + " of west)");
                    } finally {
                        for (int y = 0; y < 16; y++) {
                            for (int z = 0; z < 16; z++) {
                                level.setBlock(new BlockPos(baseX + 8, baseY + y, baseZ + z), Blocks.AIR.defaultBlockState(),
                                        Block.UPDATE_CLIENTS);
                            }
                        }
                        RadiationLane.unmarkRadResistant(WALL);
                        RadiationSystemNT.markSectionForRebuild(level, west);
                    }
                    lane.release();
                    helper.succeed();
                });
            });
        });
    }

    // ------------------------------------------------------------------ absorbers

    /** NTM rad_absorber = constant source -0.25/tick: 2.5 per 10-tick cycle. */
    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 300)
    public static void radAbsorberBaseTierDrainsItsOwnRate(GameTestHelper helper) {
        double baseRate = 2.5D;
        int cycle = 10;
        int window = cycle * 4 + 5;
        double seed = 20D;
        RadiationLane lane = new RadiationLane(helper, 14);
        lane.run(() -> {
            ServerLevel level = lane.level;
            RadiationLane.markRadSource(ABSORBER, -0.25D, 0.0D);
            level.setBlockAndUpdate(lane.pos(FLOOR), Blocks.STONE.defaultBlockState());
            BlockPos site = lane.pos(SITE);
            double[] control = new double[2];
            double[] measured = new double[1];
            RadiationSystemNT.setRadForCoord(level, site, 0D);
            helper.runAfterDelay(4, () -> {
                RadiationSystemNT.incrementRad(level, site, seed);
                helper.runAfterDelay(4, () -> {
                    control[0] = RadiationSystemNT.getRadForCoord(level, site);
                    helper.assertTrue(control[0] > 0D, "the fixture must land a field at the absorber; got " + control[0]);
                    helper.runAfterDelay(window, () -> {
                        control[1] = control[0] - RadiationSystemNT.getRadForCoord(level, site);
                        RadiationSystemNT.setRadForCoord(level, site, 0D);
                        RadiationSystemNT.incrementRad(level, site, seed);
                        helper.runAfterDelay(4, () -> {
                            measured[0] = RadiationSystemNT.getRadForCoord(level, site);
                            level.setBlockAndUpdate(site, ABSORBER.defaultBlockState());
                            helper.runAfterDelay(window, () -> {
                                double drained = measured[0] - RadiationSystemNT.getRadForCoord(level, site);
                                double absorbed = drained - control[1];
                                level.setBlockAndUpdate(site, Blocks.AIR.defaultBlockState());
                                RadiationLane.unmarkRadSource(ABSORBER);
                                RadiationSystemNT.markSectionForRebuild(level, site);
                                helper.assertTrue(absorbed >= baseRate * 3 && absorbed <= baseRate * 6,
                                        "over " + window + " ticks the base absorber must drain 3..6 cycles of 2.5 rads; it took "
                                                + absorbed + " over the " + control[1] + " the same window took without it");
                                lane.release();
                                helper.succeed();
                            });
                        });
                    });
                });
            });
        });
    }

    /** NTM rad_absorber_pink = constant source -1000/tick. */
    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 140)
    public static void radAbsorberEliteTierEmptiesWhatTheBaseTierCannot(GameTestHelper helper) {
        double seed = 500D;
        RadiationLane lane = new RadiationLane(helper, 15);
        lane.run(() -> {
            ServerLevel level = lane.level;
            RadiationLane.markRadSource(ABSORBER_ELITE, -1000.0D, 0.0D);
            level.setBlockAndUpdate(lane.pos(FLOOR), Blocks.STONE.defaultBlockState());
            BlockPos site = lane.pos(SITE);
            RadiationSystemNT.setRadForCoord(level, site, 0D);
            helper.runAfterDelay(4, () -> {
                RadiationSystemNT.incrementRad(level, site, seed);
                helper.runAfterDelay(4, () -> {
                    helper.assertTrue(RadiationSystemNT.getRadForCoord(level, site) > 0D,
                            "the fixture must land a field at the absorber's position before it is emptied");
                    level.setBlockAndUpdate(site, ABSORBER_ELITE.defaultBlockState());
                    helper.runAfterDelay(25, () -> {
                        double left = RadiationSystemNT.getRadForCoord(level, site);
                        level.setBlockAndUpdate(site, Blocks.AIR.defaultBlockState());
                        RadiationLane.unmarkRadSource(ABSORBER_ELITE);
                        RadiationSystemNT.markSectionForRebuild(level, site);
                        helper.assertTrue(left <= seed * 0.01D,
                                "the elite absorber must empty a " + seed + "-rad field within two cycles; " + left + " is left");
                        lane.release();
                        helper.succeed();
                    });
                });
            });
        });
    }

    // ------------------------------------------------------------------ area radiation

    /** NTM AreaRadiationServerTests: a 9-block strip overruns a 1x1x1 arena into its neighbours' walls. */
    private static void areaRow(GameTestHelper helper, int index, BlockPos[] pigs, Consumer<Pig[]> body) {
        RadiationLane lane = new RadiationLane(helper, index);
        lane.run(() -> {
            Pig[] spawned = new Pig[pigs.length];
            for (int i = 0; i < pigs.length; i++) {
                BlockPos at = lane.pos(pigs[i]);
                lane.level.setBlockAndUpdate(at.below(), Blocks.STONE.defaultBlockState());
                Pig pig = EntityType.PIG.create(lane.level);
                pig.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0.0F, 0.0F);
                pig.setNoAi(true);
                pig.setPersistenceRequired();
                lane.level.addFreshEntity(pig);
                spawned[i] = pig;
            }
            helper.runAfterDelay(2, () -> {
                for (Pig p : spawned) LivingHazardProps.setRadiation(p, 0D);
                try {
                    body.accept(spawned);
                } finally {
                    for (Pig p : spawned) p.discard();
                }
                lane.release();
                helper.succeed();
            });
        });
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void areaRadiationCloseAccumulatesMoreThanFar(GameTestHelper helper) {
        areaRow(helper, 16, new BlockPos[]{new BlockPos(3, 2, 1), new BlockPos(8, 2, 1)}, pigs -> {
            Pig near = pigs[0];
            Pig far = pigs[1];
            BlockPos origin = near.blockPosition().offset(-2, 0, 0);
            ContaminationUtil.radiate(near.level(), origin.getX(), origin.getY(), origin.getZ(), 10D, 400D);
            double dNear = LivingHazardProps.getRadiation(near);
            double dFar = LivingHazardProps.getRadiation(far);
            helper.assertTrue(dFar > 0D, "the farther pig (in range) must take some dose; got " + dFar);
            helper.assertTrue(dNear > dFar, "the nearer pig must take a larger dose (inverse-square); near=" + dNear + " far=" + dFar);
        });
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void areaRadiationWallAttenuatesDose(GameTestHelper helper) {
        areaRow(helper, 17, new BlockPos[]{new BlockPos(1, 2, 1), new BlockPos(9, 2, 1)}, pigs -> {
            Pig unshielded = pigs[0];
            Pig shielded = pigs[1];
            ServerLevel level = (ServerLevel) unshielded.level();
            BlockPos origin = unshielded.blockPosition().offset(4, 0, 0);
            BlockPos wall = unshielded.blockPosition().offset(6, 0, 0);
            level.setBlockAndUpdate(wall, Blocks.OBSIDIAN.defaultBlockState());
            level.setBlockAndUpdate(wall.above(), Blocks.OBSIDIAN.defaultBlockState());
            try {
                ContaminationUtil.radiate(level, origin.getX(), origin.getY(), origin.getZ(), 10D, 1000D);
            } finally {
                level.setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(wall.above(), Blocks.AIR.defaultBlockState());
            }
            double dOpen = LivingHazardProps.getRadiation(unshielded);
            double dWall = LivingHazardProps.getRadiation(shielded);
            helper.assertTrue(dOpen > 0D, "the unobstructed pig must take a dose; got " + dOpen);
            helper.assertTrue(dWall < dOpen, "the obsidian-shielded pig must take less than the unobstructed one; open="
                    + dOpen + " wall=" + dWall);
        });
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void areaRadiationOutOfRangeAccumulatesNothing(GameTestHelper helper) {
        areaRow(helper, 18, new BlockPos[]{new BlockPos(2, 2, 1), new BlockPos(2, 2, 8)}, pigs -> {
            Pig inRange = pigs[0];
            Pig outOfRange = pigs[1];
            ContaminationUtil.radiate(inRange.level(), inRange.getX(), inRange.getY(), inRange.getZ(), 4D, 100D);
            double dIn = LivingHazardProps.getRadiation(inRange);
            double dOut = LivingHazardProps.getRadiation(outOfRange);
            helper.assertTrue(dIn > 0D, "the pig at the origin must take a dose; got " + dIn);
            helper.assertTrue(dOut == 0D, "a pig beyond range must take no dose; got " + dOut);
        });
    }

    private static void clearSection(LevelChunkSection section) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) section.setBlockState(x, y, z, air, false);
            }
        }
        section.recalcBlockCounts();
    }
}
