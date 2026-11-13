package com.wf.wflib.ammo.api;

import com.wf.wflib.damage.api.ThreatKind;
import com.wf.wflib.explosion.api.BlastPayload;
import com.wf.wflib.explosion.api.BlockAllocatorShapedCharge;
import org.jetbrains.annotations.Nullable;
import com.wf.wflib.round.api.KineticPreset;
import com.wf.wflib.warhead.api.Warheads;

/** Variant -> preset under the variant id; TANK kind alone loads chunks. Effect unbound / bad params => IAE. */
public final class AmmoPresets {
    private static final String FRAGMENTATION = "fragmentation";
    private static final String SHAPED = "shaped_charge";

    private AmmoPresets() {
    }

    public static KineticPreset preset(Calibre c, AmmoVariant v) {
        KineticPreset.Builder b = KineticPreset.builder(v.id(), null,
                        v.explosive() != null ? v.explosive().warhead() : Warheads.rl("inert"))
                .speed(v.speed() / 20.0)
                .drag(v.drag())
                .quadraticDrag(v.quadraticDrag())
                .gravity(v.gravity() / 400.0)
                .mass(v.projectileMassKg())
                .energyDamage(v.damagePerKJ())
                .caliber(c.bulletMm())
                .blockPen(v.blockPen())
                .armorPenetration(v.armorPenMm())
                .penExponent(v.penExponent())
                .filler(v.fillerKg())
                .stackSize(c.stackSize());
        if (c.kind() != CalibreKind.TANK) {
            b.noChunkLoading();
        }
        if (v.tracer() != AmmoVariant.NONE) {
            b.tracer(v.tracer());
        }
        if (v.light() > 0) {
            b.light(v.light());
        }
        if (v.pierce() != null) {
            b.pierce(v.pierce().dt(), v.pierce().dr());
        }
        if (v.explosive() != null && v.explosive().size() > 0) {
            b.blast(v.explosive().size(), true);
        }
        if (v.explosive() != null) {
            b.payload(payload(c, v));
        } else if (v.fillerKg() > 0) {
            b.payload(BlastPayload.blast(v.fillerKg()));
        }
        if (v.motor() != null) {
            b.motor(v.motor().accel() / 400.0, Math.round(v.motor().burn() * 20));
        }
        if (v.threat() != null) {
            b.threat(v.threat());
        } else if (v.explosive() == null && v.armorPenMm() > 0 && c.bulletMm() >= 20) {
            b.threat(ThreatKind.KINETIC);
        }
        if (!Float.isNaN(v.ricochetDeg())) {
            b.ricochet(v.ricochetDeg());
        }
        if (!Float.isNaN(v.slopeExp())) {
            b.slopeExponent(v.slopeExp());
        }
        if (!Float.isNaN(v.overmatchTD())) {
            b.overmatchTD(v.overmatchTD());
        }
        if (v.fillerKg() > 0) {
            b.postPen();
        }
        for (AmmoVariant.Effect e : v.effects()) {
            b.effect(e.id(), e.on(), e.chance(), e.params());
        }
        return b.build();
    }

    /** Explicit, else the declared warhead payload with the variant's size and pen; HESH adds the squash head. */
    @Nullable
    static BlastPayload payload(Calibre c, AmmoVariant v) {
        AmmoVariant.Explosive e = v.explosive();
        if (e.payload() != null) {
            return e.payload();
        }
        BlastPayload declared = Warheads.payload(e.warhead());
        if (declared == null) {
            declared = switch (e.warhead().getPath()) {
                case FRAGMENTATION -> {
                    float kg = BlastPayload.kgForSize(e.size() > 0 ? e.size() : 3f);
                    // TODO estimates: ~0.6 kg of 2 g fragments per kg TNT-eq of fill
                    yield BlastPayload.blast(kg).withFragments(Math.max(20, Math.round(kg * 300f)), 2f, 1500f);
                }
                case SHAPED -> BlastPayload.ofSize(Warheads.SHAPED_CHARGE_SIZE)
                        .withJet(Warheads.SHAPED_CHARGE_JET_MM, BlockAllocatorShapedCharge.DEFAULT_HALF_ANGLE_DEG);
                default -> null;
            };
        }
        BlastPayload p = BlastPayload.resolve(declared, e.size(), v.armorPenMm());
        if (p != null && v.threat() == ThreatKind.HESH) {
            p = p.withHesh(c.bulletMm());
        }
        return p;
    }
}
