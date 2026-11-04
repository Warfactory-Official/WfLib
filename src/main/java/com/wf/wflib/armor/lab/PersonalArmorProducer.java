package com.wf.wflib.armor.lab;

import com.wf.wflib.armor.api.ArmorResolution;
import com.wf.wflib.armor.api.ArmorSetups;
import com.wf.wflib.armor.api.ArmorResult;
import com.wf.wflib.damage.api.Threat;
import com.wf.wflib.labs.api.LabContents;
import com.wf.wflib.labs.api.LabArmors;
import com.wf.wflib.labs.api.LabContext;
import com.wf.wflib.labs.api.LabLauncher;
import com.wf.wflib.labs.api.LabProducer;
import com.wf.wflib.labs.api.LabSink;
import com.wf.wflib.labs.api.LabTable;
import com.wf.wflib.round.api.KineticPreset;
import com.wf.wflib.round.api.RoundDamageSource;
import com.wf.wflib.round.api.Rounds;
import com.wf.wflib.labs.api.LabFlight;
import com.wf.wflib.labs.api.LabRows;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/**
 * Round x launcher x range x garment (+ inserts) x zone: the real per-limb armour door
 * ({@link ArmorResolution#resolveSlot}) on an armour stand. DT/DR carries no incidence term: no angle column.
 * Params: {@code armor=} (garment id glob), ranges default {@code 0,100,300,800} unless {@code ranges=}.
 */
public final class PersonalArmorProducer implements LabProducer {
    public static final String ID = "wflib:personal_armor";
    static final double[] DEFAULT_RANGES = {0, 100, 300, 800};

    private static final LabTable TABLE = LabTable.of("personal_armor")
            .dimStr("launcher").dimStr("round").dimStr("content_origin").dimNum("range_m", "m")
            .dimStr("armor").dimStr("zone")
            .measure("speed_ms", "m/s").measure("damage_base", "hp").measure("headshot_factor", "")
            .measure("damage_in", "hp").measureStr("protection_type").measureStr("outcome")
            .measure("through", "hp").measure("absorbed", "hp").measure("wear_points", "pt")
            .measure("wear_frac_worst", "").measure("worst_condition", "")
            .build();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public LabTable table() {
        return TABLE;
    }

    @Override
    public void produce(LabContext ctx, LabSink sink) {
        double[] ranges = ctx.param("ranges", null) != null ? ctx.ranges() : DEFAULT_RANGES;
        List<ArmorSetups.Setup> sets = LabArmors.sets(ctx);
        ServerLevel level = ctx.level();
        LabRows.eachWithVictim(ctx.executor(), level, ctx.launchers(), (l, victim) -> launcher(level, l, ranges, sets, sink, victim));
    }

    static void launcher(ServerLevel level, LabLauncher l, double[] ranges, List<ArmorSetups.Setup> sets,
                         LabSink sink, FakePlayer stand) {
        KineticPreset p = LabRows.preset(l);
        String launcherOrigin = LabRows.origin(l);
        for (LabFlight.Sample s : LabFlight.fly(p, l.muzzleVelocity(), ranges)) {
            if (!s.reached()) {
                continue;
            }
            Vec3 vel = new Vec3(s.speed() / 20.0, 0.0, 0.0);
            Threat threat = Rounds.threat(p, vel);
            RoundDamageSource src = Rounds.source(level, p, null, null, threat, Vec3.ZERO, vel, 0L, vel,
                    RoundDamageSource.LIVE, null);
            float base = Rounds.impactDamage(p, vel) * (float) l.damageScale();
            for (EquipmentSlot zone : ArmorSetups.worn()) {
                row(sink, l, p, launcherOrigin, s, "none", zone, base, src, stand, null);
            }
            for (ArmorSetups.Setup set : sets) {
                for (EquipmentSlot zone : set.covers()) {
                    row(sink, l, p, launcherOrigin, s, set.label(), zone, base, src, stand, set);
                }
            }
        }
    }

    private static void row(LabSink sink, LabLauncher l, KineticPreset p, String launcherOrigin, LabFlight.Sample s,
                            String armor, EquipmentSlot zone, float base, RoundDamageSource src, FakePlayer stand,
                            ArmorSetups.Setup set) {
        for (EquipmentSlot z : ArmorSetups.worn()) {
            stand.setItemSlot(z, net.minecraft.world.item.ItemStack.EMPTY);
        }
        if (set != null) {
            stand.setItemSlot(set.slot(), set.stack());
        }
        float headshot = zone == EquipmentSlot.HEAD ? p.headshot() : 1.0f;
        float in = base * headshot;
        ArmorResult r = ArmorResolution.resolveSlot(stand, zone, ArmorSetups.zone(zone), src, in, false);
        double factor = Rounds.threat(p, new Vec3(s.speed() / 20.0, 0.0, 0.0)).wearFactor();
        long wear = 0;
        double worst = 0.0;
        for (ArmorResult.LayerWear w : r.wear()) {
            long pts = factor == 1.0 ? w.points() : (long) Math.ceil(w.points() * factor);
            wear += pts;
            if (set != null && w.index() < set.specs().size()) {
                worst = Math.max(worst, (double) pts / set.specs().get(w.index()).maxCondition());
            }
        }
        String origin = set != null && LabContents.DEV_ORIGIN.equals((set.dev() ? LabContents.DEV_ORIGIN : LabContents.SHIPPED)) ? LabContents.DEV_ORIGIN
                : launcherOrigin;
        sink.row(l.id(), p.id().toString(), origin, s.range(), armor, ArmorSetups.zone(zone), s.speed(), base, headshot,
                in, r.type().name().toLowerCase(Locale.ROOT), r.outcome().name().toLowerCase(Locale.ROOT),
                r.through(), r.absorbed(), wear, worst, r.worstCondition());
    }
}
