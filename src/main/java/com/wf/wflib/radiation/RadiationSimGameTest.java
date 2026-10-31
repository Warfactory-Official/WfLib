package com.wf.wflib.radiation;

import com.wf.wflib.hazard.api.Hazards;
import com.wf.wflib.hazard.api.HazardRules;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import com.wf.wflib.core.api.WFLib;

/** NTM NEXT EmissionServerTests / UniformDecayServerTests / RadiationSettingsServerTests et al.: no world field. */
@PrefixGameTestTemplate(false)
public class RadiationSimGameTest {

    private static final String TEMPLATE = "empty";
    static final String BATCH = "wflib_radiation";
    private static final double TOL = 1.0e-9D;

    // ------------------------------------------------------------------ emission

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH)
    public static void emissionSignLawGatesEverySourceToItsOwnSide(GameTestHelper helper) {
        assertClose(helper, Emission.single(0.0D, 1.0D, 100.0D), 1.0D, "an emitter at zero must emit its rate");
        assertClose(helper, Emission.single(200.0D, 1.0D, 100.0D), 200.0D,
                "an emitter above its saturation must not pull the voxel down");
        helper.assertTrue(!Emission.active(1.0D, 100.0D, 100.0D), "a source at equilibrium must be inactive");
        assertClose(helper, Emission.single(200.0D, -1.0D, 100.0D), 199.0D,
                "a ceiling absorber above its saturation must drain toward it");
        assertClose(helper, Emission.single(50.0D, -1.0D, 100.0D), 50.0D,
                "a ceiling absorber below its saturation must be inert");
        assertClose(helper, Emission.single(10.0D, -1.0D, -50.0D), 10.0D - 60.0D / 50.0D,
                "a sink absorber must drive toward a negative saturation");
        helper.assertTrue(Emission.single(-80.0D, -1.0D, -50.0D) == -80.0D,
                "a sink absorber below its saturation must be inert");
        double c = 0.0D;
        for (int i = 0; i < 4096; i++) c = Emission.single(c, 25.0D, 100.0D);
        helper.assertTrue(c <= 100.0D && c > 99.999D,
                "iterating an emitter must converge to its saturation without passing it; got " + c);
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH)
    public static void emissionSourcesComposeIntoTheirWeightedHarmonicMean(GameTestHelper helper) {
        double w = Emission.weight(1.0D, 100.0D) + Emission.weight(1.0D, 300.0D);
        double n = Emission.numeratorTerm(1.0D, 100.0D) + Emission.numeratorTerm(1.0D, 300.0D);
        assertClose(helper, n / w, 150.0D, "two equal emitters must aim at the harmonic mean of their saturations");
        assertClose(helper, Emission.relax(150.0D, w, n), 150.0D, "the composed target must be a fixed point");
        assertClose(helper, Emission.relax(0.0D, w, n), 150.0D * w, "the composed step must be A-weighted");

        double mixW = Emission.weight(2.0D, 100.0D) + Emission.weight(-1.0D, 40.0D);
        double mixN = Emission.numeratorTerm(2.0D, 100.0D) + Emission.numeratorTerm(-1.0D, 40.0D);
        double target = mixN / mixW;
        helper.assertTrue(target > 40.0D && target < 100.0D,
                "a mixed pair must settle between the two saturations; got " + target);

        double hotW = 3.0D * Emission.weight(50.0D, 50.0D);
        double hotN = 3.0D * Emission.numeratorTerm(50.0D, 50.0D);
        assertClose(helper, Emission.relax(0.0D, hotW, hotN), hotN / hotW,
                "past unit weight the step must snap to the composed target, not overshoot it");
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH)
    public static void emissionSourceSetGatesPerPocketAndPerTick(GameTestHelper helper) {
        RadiationSystemNT.SectionSources sources = new RadiationSystemNT.SectionSources();
        sources.add(0, 1.0D, 100.0D);
        sources.add(0, 1.0D, 1000.0D);
        sources.add(0, 1.0D, 100.0D);
        helper.assertTrue(sources.size == 2,
                "identical sources must fold into one entry with a count; the set holds " + sources.size);

        double low = sources.relax(0, 0.0D);
        double bothWeights = 2.0D * Emission.weight(1.0D, 100.0D) + Emission.weight(1.0D, 1000.0D);
        double bothNumerator = 2.0D * Emission.numeratorTerm(1.0D, 100.0D) + Emission.numeratorTerm(1.0D, 1000.0D);
        assertClose(helper, low, Emission.relax(0.0D, bothWeights, bothNumerator),
                "below both saturations every source must act");

        double c = 500.0D;
        double high = sources.relax(0, c);
        assertClose(helper, high,
                Emission.relax(c, Emission.weight(1.0D, 1000.0D), Emission.numeratorTerm(1.0D, 1000.0D)),
                "above the lower saturation only the higher source may act");
        helper.assertTrue(high > c, "the surviving emitter must still raise the pocket; " + c + " -> " + high);
        helper.assertTrue(high != Emission.relax(c, bothWeights, bothNumerator),
                "and the gated answer must differ from the ungated one, or this row proves nothing");
        helper.assertTrue(sources.relax(1, 42.0D) == 42.0D, "a pocket with no source must be left alone");
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH)
    public static void emissionDensityScalesInverselyWithPocketVolume(GameTestHelper helper) {
        RadiationSystemNT.RadSource source = new RadiationSystemNT.RadSource(1.0D, 100.0D);
        int[] open = {4096};
        int[] sealed = {64};

        RadiationSystemNT.SectionSources inOpen = new RadiationSystemNT.SectionSources();
        inOpen.add(0, source, open);
        RadiationSystemNT.SectionSources inSealed = new RadiationSystemNT.SectionSources();
        inSealed.add(0, source, sealed);

        assertClose(helper, inOpen.emission[0], 1.0D, "a uniform section must scale the declared rate by one");
        assertClose(helper, inOpen.saturation[0], 100.0D, "and must leave the saturation alone");
        assertClose(helper, inSealed.emission[0], 64.0D, "a 64-voxel pocket must concentrate the emission 64x");
        assertClose(helper, inSealed.saturation[0], 6400.0D, "and its equilibrium with it");
        assertClose(helper, inSealed.emission[0] / inSealed.saturation[0],
                inOpen.emission[0] / inOpen.saturation[0],
                "the rate coefficient is a time constant and must NOT depend on the volume");

        double openStep = inOpen.relax(0, 0.0D);
        double sealedStep = inSealed.relax(0, 0.0D);
        helper.assertTrue(sealedStep > openStep * 60.0D,
                "the sealed pocket must climb far faster from the same density; " + sealedStep + " against "
                        + openStep);

        RadiationSystemNT.SectionSources sink = new RadiationSystemNT.SectionSources();
        sink.add(0, new RadiationSystemNT.RadSource(-0.25D, 0.0D), sealed);
        helper.assertTrue(sink.saturation[0] == 0.0D, "a constant sink must keep its zero-saturation marker");
        assertClose(helper, sink.emission[0], -16.0D, "and its rate must scale like any other");
        helper.succeed();
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH)
    public static void emissionSourceIsABoundaryConditionOfOneOfThreeKinds(GameTestHelper helper) {
        double sinkLow = Emission.relax(10.0D, 0.0D, 0.0D);
        helper.assertTrue(sinkLow == 10.0D, "no active source must leave the density alone; got " + sinkLow);

        double far = Emission.single(0.0D, 1.0D, 100.0D);
        double near = Emission.single(90.0D, 1.0D, 100.0D) - 90.0D;
        helper.assertTrue(far > near * 5.0D,
                "a convective source must slow as it nears its ambient; " + far + " against " + near);

        double e = 500.0D;
        double s = 250.0D;
        double w = Emission.weight(e, s);
        double n = Emission.numeratorTerm(e, s);
        helper.assertTrue(w >= 1.0D, "the fixture must actually reach the pinned arm; the weight is " + w);
        double fromZero = Emission.relax(0.0D, w, n);
        double fromMid = Emission.relax(120.0D, w, n);
        double fromHigh = Emission.relax(99999.0D, w, n);
        assertClose(helper, fromZero, s, "a pinned pocket must land on its declared value");
        helper.assertTrue(fromZero == fromMid && fromMid == fromHigh,
                "a pinned pocket must not depend on the density it started from; " + fromZero + ", " + fromMid
                        + ", " + fromHigh);
        helper.succeed();
    }

    // ------------------------------------------------------------------ uniform decay

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 60)
    public static void uniformDecayVectorMatchesScalar(GameTestHelper helper) {
        if (!VectorApi.AVAILABLE) {
            helper.fail("jdk.incubator.vector did not resolve, so no vector lane ran and this row proves nothing");
        }
        assertNegativeZeroUnreachable(helper);

        UniformDecay vector = new UniformDecayVector();
        UniformDecay scalar = new UniformDecayScalar();
        RandomSource rand = RandomSource.create(20260817L);
        List<String> bad = new ArrayList<>();
        long[] wantMasks = new long[4];
        long[] gotMasks = new long[4];
        int nans = 0, tinies = 0, overMax = 0, overFog = 0, overDestroy = 0, zeros = 0, negatives = 0;

        for (int n = 1; n <= 32; n++) {
            for (double retention : new double[]{RadiationSettings.DEFAULT.resolve().retentionDt(), 0.5D, 1.0D, 0.0D}) {
                double[] seed = new double[n];
                for (int i = 0; i < n; i++) {
                    // -0.0 excluded: assertNegativeZeroUnreachable.
                    double v = switch (rand.nextInt(9)) {
                        case 0, 1 -> 0.0D;
                        case 2 -> Double.NaN;
                        case 3 -> rand.nextDouble() * 1.0e-5D;
                        case 4 -> Double.MAX_VALUE;
                        case 5 -> Double.POSITIVE_INFINITY;
                        case 6 -> -rand.nextDouble() * 50.0D;
                        case 7 -> rand.nextDouble() * 200.0D;
                        default -> rand.nextDouble() * 10.0D;
                    };
                    seed[i] = v;
                    if (Double.isNaN(v)) nans++;
                    else if (v < 0.0D) negatives++;
                    else if (v == 0.0D) zeros++;
                    else if (Math.abs(v) < RadiationSystemNT.RAD_EPSILON) tinies++;
                    else if (v > RadiationSystemNT.RAD_MAX) overMax++;
                }
                double[] want = seed.clone();
                double[] got = seed.clone();
                scalar.decay(want, 0, n, retention, 0.0D, 100.0D, 5.0D, wantMasks);
                vector.decay(got, 0, n, retention, 0.0D, 100.0D, 5.0D, gotMasks);
                overFog += Long.bitCount(wantMasks[UniformDecay.FOG]);
                overDestroy += Long.bitCount(wantMasks[UniformDecay.DESTROY]);
                for (int m = 0; m < 4 && bad.size() < 8; m++) {
                    if (wantMasks[m] != gotMasks[m]) {
                        bad.add("n=" + n + " retention=" + retention + " mask" + m + " scalar="
                                + Long.toBinaryString(wantMasks[m]) + " vector=" + Long.toBinaryString(gotMasks[m]));
                    }
                }
                for (int i = 0; i < n && bad.size() < 8; i++) {
                    if (Double.doubleToRawLongBits(want[i]) != Double.doubleToRawLongBits(got[i])) {
                        bad.add("n=" + n + " retention=" + retention + " slot=" + i + " in=" + seed[i]
                                + " scalar=" + want[i] + " vector=" + got[i]);
                    }
                }
            }
        }
        if (nans == 0 || tinies == 0 || overMax == 0 || negatives == 0 || zeros == 0) {
            helper.fail("an input class went unreached (NaN " + nans + ", sub-epsilon " + tinies + ", over-max "
                    + overMax + ", under-minBound " + negatives + ", zero " + zeros + ")");
        }
        if (overFog == 0 || overDestroy == 0) {
            helper.fail("no section crossed the fog (" + overFog + ") or destroy (" + overDestroy + ") threshold");
        }
        if (!bad.isEmpty()) {
            helper.fail("the vector uniform decay diverges from the scalar kernel (" + bad.size() + " shown): "
                    + String.join("; ", bad));
        }
        helper.succeed();
    }

    private static void assertNegativeZeroUnreachable(GameTestHelper helper) {
        double[] probes = {-0.0D, 0.0D, -1.0e-300D, -1.0e-6D, -5.0D, -Double.MAX_VALUE, Double.NEGATIVE_INFINITY,
                Double.NaN, 1.0e-300D, RadiationSystemNT.RAD_MAX};
        long negZero = Double.doubleToRawLongBits(-0.0D);
        for (double v : probes) {
            if (Double.doubleToRawLongBits(RadiationSystemNT.sanitize(v, 0.0D)) == negZero) {
                helper.fail("sanitize(" + v + ") answers a raw -0.0, so a stored negative zero IS reachable");
            }
        }
        double[] pair = new double[2];
        double[] values = {0.0D, 1.0e-300D, 5.0D, RadiationSystemNT.RAD_MAX};
        for (double a : values) {
            for (double b : values) {
                pair[0] = a;
                pair[1] = b;
                RadiationSystemNT.exchangeUniExactXZ(pair, 0, 1, RadiationSettings.DEFAULT.resolve().uuE());
                for (double out : pair) {
                    if (Double.doubleToRawLongBits(out) == negZero) {
                        helper.fail("exchangeUniExactXZ(" + a + ", " + b + ") answers a raw -0.0");
                    }
                }
            }
        }
    }

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 200)
    public static void uniformDecayAllocatesNothingPerColumn(GameTestHelper helper) {
        if (!VectorApi.AVAILABLE) {
            helper.fail("jdk.incubator.vector did not resolve, so there is no kernel to screen");
        }
        double[] column = new double[24];
        for (int i = 0; i < column.length; i++) column[i] = 500.0D - i * 7.0D;
        long vector = allocationPerColumn(new UniformDecayVector(), column);
        long scalar = allocationPerColumn(new UniformDecayScalar(), column);
        VectorBackend backend = VectorApi.backend();
        if (scalar > 64L) {
            helper.fail("the scalar control allocates " + scalar + " B/column; the harness, not the kernel");
        }
        if (backend.arithmetic() && backend.masks() && vector > 64L) {
            helper.fail("the vector uniform decay allocates " + vector + " B/column against the scalar control's "
                    + scalar + " on backend " + backend);
        }
        helper.succeed();
    }

    private static long allocationPerColumn(UniformDecay kernel, double[] column) {
        double[] scratch = new double[column.length];
        long[] masks = new long[4];
        for (int i = 0; i < 20_000; i++) {
            System.arraycopy(column, 0, scratch, 0, column.length);
            kernel.decay(scratch, 0, column.length, 0.9D, 0.0D, 100.0D, 5.0D, masks);
        }
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long before = bean.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < 5_000; i++) {
            System.arraycopy(column, 0, scratch, 0, column.length);
            kernel.decay(scratch, 0, column.length, 0.9D, 0.0D, 100.0D, 5.0D, masks);
        }
        return (bean.getCurrentThreadAllocatedBytes() - before) / 5_000;
    }

    // ------------------------------------------------------------------ settings

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH)
    public static void settingsResolveToTheirDeclaredPhysics(GameTestHelper helper) {
        double dT = 0.05D;
        double retention = settings(1, 10.0D).resolve(dT).retentionDt();
        double afterOneHalfLife = Math.pow(retention, 10.0D / dT);
        helper.assertTrue(Math.abs(afterOneHalfLife - 0.5D) < 1.0e-12D,
                "a 10-second half life must leave exactly half the field after 10 seconds; it leaves "
                        + afterOneHalfLife);
        double single = settings(0, 4.0D).resolve(dT).uuE();
        double doubled = settings(0, 8.0D).resolve(dT).uuE();
        helper.assertTrue(Math.abs(doubled - single * single) < 1.0e-12D,
                "doubling the diffusivity must square the exchange coefficient; " + doubled + " against "
                        + single * single);
        helper.assertTrue(settings(3, Double.MAX_VALUE).resolve(dT).fogProbU64() == 0L,
                "an unreachably rare fog chance must resolve to a zero probability rather than wrapping");
        helper.succeed();
    }

    /** diffusivity_transport is outside Resolved: it selects the exchange arm, not a constant. */
    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH)
    public static void settingsEveryDeclaredFieldReachesTheSolver(GameTestHelper helper) {
        double dT = 0.05D;
        RadiationSettings d = RadiationSettings.DEFAULT;
        RadiationSettings.Resolved base = d.resolve(dT);
        RadiationSettings[] probes = {
                settings(0, d.diffusivityOrDefault() * 4.2D),
                settings(1, d.halfLifeSecondsOrDefault() * 0.5D),
                settings(2, d.fogRadOrDefault() + 7.0D),
                settings(3, d.fogChanceOrDefault() + 7.0D),
                new RadiationSettings(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.of(!d.worldRadEffectsOrDefault()), Optional.empty(), Optional.empty(),
                        Optional.empty()),
                settings(4, 0.5D),
                settings(5, -1.0D),
        };
        String[] names = {"diffusivity", "half_life_seconds", "fog_rad", "fog_chance", "world_rad_effects",
                "ambient_rad", "min_bound"};
        int declared = RadiationSettings.class.getRecordComponents().length;
        if (declared != probes.length + 1) {
            helper.fail("RadiationSettings declares " + declared + " fields and this row probes " + probes.length
                    + " plus diffusivity_transport");
        }
        for (int i = 0; i < probes.length; i++) {
            if (probes[i].resolve(dT).equals(base)) {
                helper.fail("`" + names[i] + "` is declared by the codec and reaches NOTHING");
            }
        }
        helper.succeed();
    }

    /** NTM ships a nether entry; wflib ships none. A stale build-output file would show up here. */
    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH)
    public static void settingsRegistryHoldsOnlyTheEntriesWeShip(GameTestHelper helper) {
        List<String> loaded = helper.getLevel().registryAccess().registryOrThrow(RadiationSettings.REGISTRY)
                .keySet().stream().map(ResourceLocation::toString).sorted().toList();
        if (!loaded.isEmpty()) helper.fail("radiation_settings holds " + loaded + " where wflib ships none");
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(nether != null && RadiationSystemNT.ambientRad(nether) == 0.0D,
                "without a shipped entry no dimension may carry an ambient floor");
        helper.succeed();
    }

    /** NTM ships four classes; wflib ships none => trivial table, transport resolves off. */
    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH)
    public static void diffusivityRegistryHoldsOnlyTheClassesWeShip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> loaded = level.registryAccess().registryOrThrow(RadiationDiffusivity.REGISTRY)
                .keySet().stream().map(ResourceLocation::toString).sorted().toList();
        if (!loaded.isEmpty()) helper.fail("rad_diffusivity holds " + loaded + " where wflib ships none");
        helper.assertTrue(!RadiationDiffusivity.transportEnabled(level) && RadiationDiffusivity.isTrivial(),
                "an empty mapping must resolve trivial with transport off");
        helper.assertTrue(!RadiationSystemNT.getWorldRadData(level).diffusivityTransport,
                "the world data must take the plain exchange arm");
        helper.succeed();
    }

    /** NTM constantSourceRatesMatchTheOracle, item_hazard arm on a code-registered stand-in. */
    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void constantSourceItemHazardRateMatchesTheOracle(GameTestHelper helper) {
        Block block = Blocks.LODESTONE;
        HazardRules.register(block, Hazards.makeData(Hazards.RADIATION, 25.0D));
        HazardRules.clearCaches();
        helper.runAfterDelay(2, () -> {
            try {
                float perSecond = (float) HazardRules.getRawRadsFromBlock(block) * 0.1F;
                helper.assertTrue(perSecond > 0.0F, block + " must carry a radiation hazard for this row to mean anything");
                RadSourceRule.Rate rate = new RadSourceRule.Rate.ItemHazard();
                helper.assertTrue(rate.emission(block) == perSecond / 20.0D,
                        block + " must emit the oracle's rawRads*0.1 per second; it declares " + rate.emission(block)
                                + " against " + perSecond / 20.0D);
                helper.assertTrue(rate.saturation(block) == (float) HazardRules.getRawRadsFromBlock(block),
                        "and saturate at its own dose; it declares " + rate.saturation(block));
            } finally {
                HazardRules.unregister(block.asItem());
                HazardRules.clearCaches();
            }
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ persistence

    @GameTest(templateNamespace = WFLib.MODID, template = TEMPLATE, batch = BATCH)
    public static void persistenceMaximumBoundAndAbsentDelete(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int encoded = encodeMaximumPayload(level);
        helper.assertTrue(encoded == RadiationSystemNT.FMT8_MAX_BYTES,
                "maximum FMT 8 payload must exactly fill its bound " + RadiationSystemNT.FMT8_MAX_BYTES + "; got " + encoded);
        helper.assertTrue(!absentDeleteCreatesRegion(level),
                "deleting an absent sidecar entry must not create a zero-byte region file");
        verifyAcknowledgedRetryAndCoalescing();
        verifyAtomicDirectoryRetirement();
        verifyResetRejectsInflightRead();
        helper.succeed();
    }

    private static int encodeMaximumPayload(ServerLevel level) {
        WorldRadiationData data = RadiationSystemNT.getWorldRadData(level);
        ByteBuffer body = ByteBuffer.allocate(RadiationSystemNT.FMT8_MAX_BYTES - 4);
        body.put((byte) RadiationSystemNT.MAX_SECTIONS_PER_CHUNK);
        short[] pocketData = new short[SectionPos.SECTION_SIZE * SectionPos.SECTION_SIZE * SectionPos.SECTION_SIZE];
        Arrays.fill(pocketData, RadiationSystemNT.NO_POCKET);
        double[] densities = new double[RadiationSystemNT.MAX_POCKETS];
        Arrays.fill(densities, 1.0D);
        int pocket = 0;
        for (int block = 0; block < pocketData.length; block++) {
            if (((SectionKeys.getLocalX(block) + SectionKeys.getLocalY(block) + SectionKeys.getLocalZ(block)) & 1) == 0) {
                pocketData[block] = (short) pocket++;
            }
        }
        if (pocket != RadiationSystemNT.MAX_POCKETS) throw new IllegalStateException("Expected 2048 pockets, got " + pocket);
        for (int sy = 0; sy < RadiationSystemNT.MAX_SECTIONS_PER_CHUNK; sy++) {
            if (!data.writeSectionRecord(body, sy, RadiationSystemNT.MAX_POCKETS, pocketData, densities)) {
                throw new IllegalStateException("Maximum section unexpectedly encoded empty");
            }
        }
        return 4 + body.position();
    }

    private static boolean absentDeleteCreatesRegion(ServerLevel level) {
        Path root;
        try {
            Method m = RegionChunkRadiationStorage.class.getDeclaredMethod("dimensionRoot", ServerLevel.class);
            m.setAccessible(true);
            root = (Path) m.invoke(null, level);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("sidecar root seam broke", e);
        }
        for (int attempt = 0; attempt < 256; attempt++) {
            int coordinate = 1_500_000 + attempt * 32;
            ChunkPos pos = new ChunkPos(coordinate, coordinate);
            Path region = root.resolve("r." + pos.getRegionX() + "." + pos.getRegionZ() + ".mca");
            if (Files.exists(region)) continue;
            RadiationSystemNT.SIDE_CAR.write(level, pos, null);
            return Files.exists(region);
        }
        throw new IllegalStateException("Could not find an unused radiation region coordinate");
    }

    private static void verifyAcknowledgedRetryAndCoalescing() {
        final class FakeBackend implements RegionChunkRadiationStorage.RadiationRegionStorage.Backend {
            final Map<Long, byte[]> values = new HashMap<>();
            boolean failNextWrite = true;
            boolean failNextFlush;
            int flushCount;

            @Override
            public boolean hasRegion(ChunkPos pos) {
                return values.containsKey(pos.toLong());
            }

            @Override
            public byte[] read(ChunkPos pos) {
                return values.get(pos.toLong());
            }

            @Override
            public void write(ChunkPos pos, byte[] payload) throws IOException {
                if (failNextWrite) {
                    failNextWrite = false;
                    throw new IOException("injected write failure");
                }
                if (payload == null) values.remove(pos.toLong());
                else values.put(pos.toLong(), payload.clone());
            }

            @Override
            public void flush() throws IOException {
                flushCount++;
                if (failNextFlush) {
                    failNextFlush = false;
                    throw new IOException("injected flush failure");
                }
            }

            @Override
            public void close() {
            }
        }

        FakeBackend backend = new FakeBackend();
        RegionChunkRadiationStorage.RadiationRegionStorage storage =
                new RegionChunkRadiationStorage.RadiationRegionStorage(backend);
        ChunkPos pos = new ChunkPos(3, 5);
        byte[] first = {1, 2, 3};
        try {
            storage.write(pos, first);
            throw new IllegalStateException("Injected sidecar write unexpectedly succeeded");
        } catch (UncheckedIOException expected) {
        }
        if (!Arrays.equals(storage.read(pos), first)) {
            throw new IllegalStateException("Failed write must remain pending and visible to reads");
        }
        storage.flush();
        if (!Arrays.equals(backend.read(pos), first)) {
            throw new IllegalStateException("Flush must retry and persist the failed write");
        }

        backend.failNextWrite = true;
        try {
            storage.write(pos, new byte[]{4});
            throw new IllegalStateException("Second injected sidecar write unexpectedly succeeded");
        } catch (UncheckedIOException expected) {
        }
        byte[] newer = {5, 6};
        storage.write(pos, newer);
        storage.flush();
        if (!Arrays.equals(backend.read(pos), newer) || !Arrays.equals(storage.read(pos), newer)) {
            throw new IllegalStateException("Newer coalesced write must dominate an older failed sequence");
        }

        backend.failNextFlush = true;
        storage.write(pos, new byte[]{7});
        try {
            storage.flush();
            throw new IllegalStateException("Injected sidecar flush unexpectedly succeeded");
        } catch (UncheckedIOException expected) {
        }
        int failedFlushCount = backend.flushCount;
        storage.flush();
        if (backend.flushCount != failedFlushCount + 1) {
            throw new IllegalStateException("Failed flush must leave the storage marked for another force");
        }
    }

    private static void verifyAtomicDirectoryRetirement() {
        Path parent = null;
        try {
            parent = Files.createTempDirectory("wflib-radiation-retire-");
            Path root = parent.resolve("radiation");
            Path marker = root.resolve("nested").resolve("marker");
            Files.createDirectories(marker.getParent());
            Files.write(marker, new byte[]{1});
            Path retired = RegionChunkRadiationStorage.retireDirectory(root);
            if (Files.exists(root)) throw new IllegalStateException("Retired radiation directory remains live");
            if (!Files.exists(retired.resolve("nested").resolve("marker"))) {
                throw new IllegalStateException("Atomic retirement did not preserve the complete directory tree");
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } finally {
            if (parent != null) {
                try (var paths = Files.walk(parent)) {
                    for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            }
        }
    }

    private static void verifyResetRejectsInflightRead() {
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath(WFLib.MODID, "persistence_epoch_test"));
        AtomicLong epoch = RadiationSystemNT.sidecarEpochs.computeIfAbsent(dimension, ignored -> new AtomicLong());
        byte[] carry = RadiationSystemNT.wrapSidecarCarry(epoch.get(), new byte[]{RadiationSystemNT.MAGIC_0,
                RadiationSystemNT.MAGIC_1, RadiationSystemNT.MAGIC_2, RadiationSystemNT.FMT, 1});
        epoch.incrementAndGet();
        try {
            if (RadiationSystemNT.unwrapSidecarCarry(dimension, carry) != null) {
                throw new IllegalStateException("Reset must reject a pre-reset in-flight sidecar read");
            }
        } catch (DecodeException ex) {
            throw new IllegalStateException(ex);
        } finally {
            RadiationSystemNT.sidecarEpochs.remove(dimension, epoch);
        }
    }

    // ------------------------------------------------------------------ support

    /** Slots: 0 diffusivity, 1 half life, 2 fog rad, 3 fog chance, 4 ambient, 5 min bound. */
    private static RadiationSettings settings(int slot, double value) {
        @SuppressWarnings("unchecked")
        Optional<Double>[] f = new Optional[6];
        Arrays.fill(f, Optional.empty());
        f[slot] = Optional.of(value);
        return new RadiationSettings(f[0], f[1], f[2], f[3], Optional.empty(), f[4], f[5], Optional.empty());
    }

    private static void assertClose(GameTestHelper helper, double actual, double expected, String what) {
        helper.assertTrue(Math.abs(actual - expected) <= TOL * Math.max(1.0D, Math.abs(expected)),
                what + "; expected " + expected + " got " + actual);
    }
}
