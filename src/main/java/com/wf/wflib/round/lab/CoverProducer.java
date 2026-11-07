package com.wf.wflib.round.lab;

import com.wf.wflib.labs.api.LabContext;
import com.wf.wflib.labs.api.LabCover;
import com.wf.wflib.labs.api.LabFlight;
import com.wf.wflib.labs.api.LabRows;
import com.wf.wflib.labs.api.LabLauncher;
import com.wf.wflib.labs.api.LabProducer;
import com.wf.wflib.labs.api.LabSink;
import com.wf.wflib.labs.api.LabTable;
import com.wf.wflib.round.RoundConfig;
import com.wf.wflib.round.api.KineticPreset;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Round x launcher x range x block x thickness x incidence: verdict of the real ricochet / block-pen / drill rules.
 * Rounds with no block penetration and no drill get one row per block at 1 m, 0 deg. Params: {@code thickness=},
 * {@code angles=}, {@code blocks=} (id glob), {@code samples=}; ranges default {@code 0,100,300,800} unless {@code ranges=}.
 */
public final class CoverProducer implements LabProducer {
    public static final String ID = "wflib:cover";
    static final double[] DEFAULT_RANGES = {0, 100, 300, 800};
    static final String DEFAULT_THICKNESS = "0.25,0.5,1,2";
    static final String DEFAULT_ANGLES = "0,45,70,85";

    private static final LabTable TABLE = LabTable.of("cover")
            .dimStr("launcher").dimStr("round").dimStr("content_origin").dimNum("range_m", "m").dimStr("block")
            .dimNum("thickness_m", "m").dimNum("angle_deg", "deg")
            .measure("speed_ms", "m/s").measureStr("verdict")
            .measure("p_ricochet", "").measure("p_perforate", "").measure("p_stop", "")
            .measure("spent", "").measure("exit_speed_ms", "m/s").measure("exit_pen_mm", "mm")
            .measure("stop_depth_m", "m").measure("ricochet_speed_ms", "m/s")
            .measure("impulse_kg_ms", "kg_m/s").measure("block_toughness", "kg_m/s").measure("block_damage_frac", "")
            .measure("samples", "")
            .build();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String domain() {
        return "shared";
    }

    @Override
    public LabTable table() {
        return TABLE;
    }

    @Override
    public void produce(LabContext ctx, LabSink sink) {
        double[] ranges = ctx.param("ranges", null) != null ? ctx.ranges() : DEFAULT_RANGES;
        double[] thick = nums(ctx.param("thickness", DEFAULT_THICKNESS));
        double[] angles = nums(ctx.param("angles", DEFAULT_ANGLES));
        int samples = (int) ctx.paramNum("samples", 128);
        long seed = ctx.seed();
        String filter = ctx.param("blocks", null);
        List<CoverBlocks.Entry> blocks = CoverBlocks.all().stream()
                .filter(b -> filter == null || matches(filter, b.id())).toList();
        float scale = RoundConfig.snapshot().blockToughnessScale();
        LabRows.each(ctx.executor(), ctx.launchers(), l -> launcher(l, ranges, blocks, thick, angles, samples, seed,
                scale, sink));
    }

    private static boolean matches(String globs, String id) {
        for (String g : globs.split(",")) {
            if (id.matches(java.util.regex.Pattern.quote(g).replace("*", "\\E.*\\Q"))) {
                return true;
            }
        }
        return false;
    }

    private static double[] nums(String csv) {
        return Arrays.stream(csv.split(",")).mapToDouble(Double::parseDouble).toArray();
    }

    static void launcher(LabLauncher l, double[] ranges, List<CoverBlocks.Entry> blocks, double[] thick,
                         double[] angles, int samples, long seed, float scale, LabSink sink) {
        KineticPreset p = LabRows.preset(l);
        String origin = LabRows.origin(l);
        boolean grid = p.blockPen() > 0.0f || p.penetration() > 0;
        LabFlight.Sample[] flight = LabFlight.fly(p, l.muzzleVelocity(), ranges);
        for (LabFlight.Sample s : flight) {
            if (!s.reached()) {
                continue;
            }
            for (CoverBlocks.Entry b : blocks) {
                for (double t : grid ? thick : new double[]{1.0}) {
                    for (double a : grid ? angles : new double[]{0.0}) {
                        float tough = b.toughness() * scale;
                        LabCover.Result r = LabCover.evaluate(p, s.speed(), b.resistance(), tough, b.ricochet(), t, a,
                                samples, seed);
                        sink.row(l.id(), p.id().toString(), origin, s.range(), b.id(), t, a, s.speed(),
                                r.verdict().name().toLowerCase(Locale.ROOT), r.pRicochet(), r.pPerforate(), r.pStop(),
                                r.spent(), r.exitSpeed(), r.exitPen(), r.depth(), r.ricochetSpeed(), r.impulse(),
                                tough, r.damageFrac(), samples);
                    }
                }
            }
        }
    }
}
