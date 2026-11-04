package com.wf.wflib.round.lab;

import com.wf.wflib.ammo.api.Ammo;
import com.wf.wflib.ammo.api.AmmoVariant;
import com.wf.wflib.labs.api.LabContext;
import com.wf.wflib.labs.api.LabFlight;
import com.wf.wflib.labs.api.LabRows;
import com.wf.wflib.labs.api.LabLauncher;
import com.wf.wflib.labs.api.LabProducer;
import com.wf.wflib.labs.api.LabSink;
import com.wf.wflib.labs.api.LabTable;
import com.wf.wflib.round.api.BlockPen;
import com.wf.wflib.round.api.KineticPreset;
import com.wf.wflib.round.api.Rounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Every round x launcher x range: level flight through the real recurrence, then what the round does on arrival. */
public final class RoundFlightProducer implements LabProducer {
    public static final String ID = "wflib:round_flight";

    private static final LabTable TABLE = LabTable.of("round_flight")
            .dimStr("launcher").dimStr("round").dimStr("calibre").dimStr("content_origin").dimNum("range_m", "m")
            .measure("muzzle_ms", "m/s").measure("dispersion_moa", "moa").measure("damage_scale", "")
            .measure("mass_kg", "kg").measure("caliber_mm", "mm").measure("pellets", "")
            .measure("reached", "").measure("tick", "tick").measure("time_s", "s").measure("speed_ms", "m/s")
            .measure("energy_j", "J").measure("drop_m", "m")
            .measure("armor_pen_mm_rha", "mm_rha").measure("block_pen_mm", "mm").measure("damage_hp", "hp")
            .measure("pierce_dt", "hp").measure("pierce_dr", "")
            .measureStr("threat_kind").measure("motor", "").measureStr("launcher_extra")
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
        double[] ranges = ctx.ranges();
        LabRows.each(ctx.executor(), ctx.launchers(), l -> launcher(l, ranges, sink));
    }

    static void launcher(LabLauncher l, double[] ranges, LabSink sink) {
        KineticPreset p = LabRows.preset(l);
        AmmoVariant v = Ammo.frozen() ? Ammo.variant(ResourceLocation.parse(l.roundId())) : null;
        String calibre = v == null ? null : v.calibre().toString();
        int pellets = v == null ? 1 : v.pellets();
        String origin = LabRows.origin(l);
        String extra = LabRows.extra(l);
        LabFlight.Sample[] flight = LabFlight.fly(p, l.muzzleVelocity(), ranges);
        for (LabFlight.Sample s : flight) {
            if (!s.reached()) {
                sink.row(l.id(), p.id().toString(), calibre, origin, s.range(), l.muzzleVelocity(), l.dispersionMoa(),
                        l.damageScale(), p.mass(), p.caliberMm(), pellets, 0, null, null, null, null, null, null, null,
                        null, null, null, null, null, null);
                continue;
            }
            double blocksPerTick = s.speed() / 20.0;
            Vec3 vel = new Vec3(blocksPerTick, 0.0, 0.0);
            var threat = Rounds.threat(p, vel);
            double energy = 0.5 * p.mass() * s.speed() * s.speed();
            sink.row(l.id(), p.id().toString(), calibre, origin, s.range(), l.muzzleVelocity(), l.dispersionMoa(),
                    l.damageScale(), p.mass(), p.caliberMm(), pellets, 1, s.tick(), s.time(), s.speed(), energy,
                    s.drop(), threat.penetrationMm(),
                    BlockPen.capacity(p.blockPen(), p.muzzleSpeed(), blocksPerTick),
                    Rounds.impactDamage(p, vel) * (float) l.damageScale(), p.pierceDT(), p.pierceDR(),
                    threat.kind().name(), p.motorAccel() > 0.0f ? 1 : 0, extra);
        }
    }
}
