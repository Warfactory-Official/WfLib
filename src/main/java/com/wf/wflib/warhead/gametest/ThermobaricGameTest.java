package com.wf.wflib.warhead.gametest;

import com.mojang.logging.LogUtils;
import com.wf.wflib.testkit.api.ForcedChunks;
import com.wf.wflib.warhead.ThermobaricWarhead;
import com.wf.wflib.warhead.api.WarheadCarrier;
import com.wf.wflib.warhead.api.Warheads;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Locale;
import com.wf.wflib.core.api.WFLib;

/** Thermobaric reach vs STANDARD at one size. */
@PrefixGameTestTemplate(false)
public class ThermobaricGameTest {

    private static final String TEMPLATE = "empty";
    /** Past AgentOrangeGameTest (5700). */
    private static final int LANE_ORIGIN = 6400;
    private static final int LANE_SPACING = 160;
    private static final double ALTITUDE = 20.0;
    private static final float SIZE = 5f;
    private static final double[] DISTANCES = {3, 5, 7, 9, 11, 13};

    /** Golem fan at {@link #DISTANCES}: thermobaric hurts strictly more of them than STANDARD, never fewer. */
    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, timeoutTicks = 200)
    public static void thermobaricOutreachesStandard(GameTestHelper helper) {
        Lane standard = new Lane(helper, 0);
        Lane thermobaric = new Lane(helper, 1);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(standard.ticking() && thermobaric.ticking(), "lanes loading"))
                .thenExecute(() -> {
                    IronGolem[] a = standard.fan();
                    IronGolem[] b = thermobaric.fan();
                    Warheads.STANDARD.detonate(carrier(standard.level), standard.centre());
                    ThermobaricWarhead.detonate(carrier(thermobaric.level), thermobaric.centre());
                    helper.runAfterDelay(2, () -> {
                        standard.release();
                        thermobaric.release();
                        StringBuilder table = new StringBuilder("d std tbx:");
                        int hitA = 0;
                        int hitB = 0;
                        for (int i = 0; i < DISTANCES.length; i++) {
                            float da = a[i].getMaxHealth() - a[i].getHealth();
                            float db = b[i].getMaxHealth() - b[i].getHealth();
                            hitA += da > 0 ? 1 : 0;
                            hitB += db > 0 ? 1 : 0;
                            table.append(String.format(Locale.ROOT, " [%.0f %.1f %.1f]", DISTANCES[i], da, db));
                        }
                        LogUtils.getLogger().info("ThermobaricGameTest {}", table);
                        helper.assertTrue(hitB > hitA && hitA > 0, table.toString());
                        helper.succeed();
                    });
                });
    }

    private static WarheadCarrier carrier(Level level) {
        return new WarheadCarrier() {
            @Override
            public Level level() {
                return level;
            }

            @Override
            public int getFragmentCount() {
                return 0;
            }

            @Override
            public Vec3 angle() {
                return new Vec3(0, -1, 0);
            }

            @Override
            public float blastSize() {
                return SIZE;
            }

            @Override
            public boolean breaksBlocks() {
                return false;
            }
        };
    }

    public static final class Lane {
        public final ServerLevel level;
        private final BlockPos origin;

        public Lane(GameTestHelper helper, int index) {
            this.level = helper.getLevel();
            this.origin = helper.absolutePos(BlockPos.ZERO).offset(0, (int) ALTITUDE,
                    LANE_ORIGIN + index * LANE_SPACING);
            force(true);
        }

        public boolean ticking() {
            return this.level.isPositionEntityTicking(this.origin);
        }

        public Vec3 centre() {
            return Vec3.atCenterOf(this.origin);
        }

        /** One golem per distance, 60 deg apart: none shadows another. */
        IronGolem[] fan() {
            IronGolem[] out = new IronGolem[DISTANCES.length];
            for (int i = 0; i < out.length; i++) {
                double a = Math.toRadians(60.0 * i);
                out[i] = golem(centre().add(Math.cos(a) * DISTANCES[i], -0.5, Math.sin(a) * DISTANCES[i]));
            }
            return out;
        }

        public IronGolem golem(Vec3 at) {
            IronGolem g = EntityType.IRON_GOLEM.create(this.level);
            g.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
            g.setNoAi(true);
            g.setNoGravity(true);
            this.level.addFreshEntity(g);
            return g;
        }

        public void release() {
            force(false);
        }

        private void force(boolean forced) {
            int cx = SectionPos.blockToSectionCoord(this.origin.getX());
            int cz = SectionPos.blockToSectionCoord(this.origin.getZ());
            for (int ox = -1; ox <= 1; ox++) {
                for (int oz = -1; oz <= 1; oz++) {
                    ForcedChunks.set(this.level, cx + ox, cz + oz, forced);
                }
            }
        }
    }
}
