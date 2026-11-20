package com.wf.wflib.continuum;

import com.hbm.items.weapon.sedna.BulletConfig;
import com.wf.wflib.damage.api.Threat;
import com.wf.wflib.damage.api.ThreatKind;

import java.util.Locale;

/**
 * What Continuum's rounds would do to armour, which it has no numbers for (docs, 3.7). Each of its bullet configs is named for its cartridge and load, and
 * from those two this works out a {@link Threat}: the bore, the steel it defeats at normal incidence (the figure hull armour is walked with) and the kind.
 * The table is hand-set against WFLib's own rounds: a 5.56 FMJ beats thin plate, a .50 AP beats light armour, a 240 mm AP shell beats a tank.
 */
final class Ballistics {
    private Ballistics() {
    }

    /** A cartridge: bore, and the steel its plain full-metal-jacket load defeats. Matched on the config id's start. */
    private record Cartridge(String prefix, float caliberMm, float penMm, ThreatKind kind, float massG) {
    }

    private static final Cartridge[] CARTRIDGES = {
            new Cartridge("p22", 5.6f, 0.6f, ThreatKind.SMALL_ARMS, 2.6f),
            new Cartridge("p9", 9.0f, 1.2f, ThreatKind.SMALL_ARMS, 7.5f),
            new Cartridge("m357", 9.1f, 2.0f, ThreatKind.SMALL_ARMS, 10.2f),
            new Cartridge("m44", 10.9f, 2.5f, ThreatKind.SMALL_ARMS, 15.6f),
            new Cartridge("p45", 11.5f, 1.6f, ThreatKind.SMALL_ARMS, 14.9f),
            new Cartridge("p35800", 12.7f, 3.0f, ThreatKind.SMALL_ARMS, 19.4f),
            new Cartridge("r556", 5.7f, 8.0f, ThreatKind.SMALL_ARMS, 4.0f),
            new Cartridge("r762", 7.8f, 11.0f, ThreatKind.SMALL_ARMS, 9.7f),
            new Cartridge("bmg50", 12.7f, 26.0f, ThreatKind.SMALL_ARMS, 42.0f),
            new Cartridge("g12", 18.5f, 4.5f, ThreatKind.SMALL_ARMS, 28.0f),
            new Cartridge("g10", 19.7f, 5.0f, ThreatKind.SMALL_ARMS, 34.0f),
            new Cartridge("b75", 19.0f, 16.0f, ThreatKind.SMALL_ARMS, 60.0f),
            new Cartridge("dgk", 14.5f, 18.0f, ThreatKind.SMALL_ARMS, 64.0f),
            new Cartridge("stone", 20.0f, 0.4f, ThreatKind.SMALL_ARMS, 40.0f),
            new Cartridge("g26", 26.5f, 0.0f, ThreatKind.HE, 100.0f),
            new Cartridge("g40", 40.0f, 15.0f, ThreatKind.HE, 230.0f),
            new Cartridge("rocket", 90.0f, 25.0f, ThreatKind.HE, 3000.0f),
            new Cartridge("shell", 240.0f, 120.0f, ThreatKind.HE, 90000.0f),
            new Cartridge("coil", 20.0f, 40.0f, ThreatKind.KINETIC, 150.0f),
            new Cartridge("tau", 10.0f, 14.0f, ThreatKind.SMALL_ARMS, 30.0f),
            new Cartridge("energy", 8.0f, 10.0f, ThreatKind.SMALL_ARMS, 5.0f),
    };

    /** What a load does to its cartridge's plain figure: penetration factor, and the kind it becomes (null = the cartridge's own). */
    private record Load(String suffix, float penFactor, ThreatKind kind) {
    }

    private static final Load[] LOADS = {
            new Load("_du", 2.4f, ThreatKind.KINETIC),
            new Load("_ap", 1.8f, ThreatKind.KINETIC),
            new Load("_heat", 12.0f, ThreatKind.HEAT),
            new Load("_demo", 0.5f, ThreatKind.HE),
            new Load("_he", 0.5f, ThreatKind.HE),
            new Load("_explosive", 0.5f, ThreatKind.HE),
            new Load("_exp", 0.5f, ThreatKind.HE),
            new Load("_jhp", 0.55f, null),
            new Load("_sp", 0.8f, null),
            new Load("_express", 1.2f, null),
            new Load("_bp", 0.3f, null),
            new Load("_inc", 0.9f, null),
            new Load("_phosphorus", 0.9f, null),
            new Load("_flechette", 1.1f, null),
            new Load("_slug", 1.0f, null),
            new Load("_magnum", 1.1f, null),
    };

    /** The figures for one config. */
    record Profile(float caliberMm, float penMm, ThreatKind kind, int tracerRgb) {
        Threat threat() {
            return new Threat(kind, caliberMm, penMm);
        }
    }

    /** Whether the table knows this round's cartridge (the rest are rated by damage). */
    static boolean known(BulletConfig config) {
        String id = config.id == null ? "" : config.id.getPath().toLowerCase(Locale.ROOT);
        for (Cartridge c : CARTRIDGES) if (id.startsWith(c.prefix)) return true;
        return false;
    }

    static Profile of(BulletConfig config) {
        String id = config.id == null ? "" : config.id.getPath().toLowerCase(Locale.ROOT);
        Cartridge cartridge = null;
        for (Cartridge c : CARTRIDGES) {
            if (id.startsWith(c.prefix)) {
                cartridge = c;
                break;
            }
        }
        float caliber;
        float pen;
        ThreatKind kind;
        if (cartridge == null) {
            // A round this table has not met: rated by the damage it does, as a 7 mm rifle round is.
            caliber = 7.0f;
            pen = 3.0f * Math.max(0.2f, config.damageMult);
            kind = ThreatKind.SMALL_ARMS;
        } else {
            caliber = cartridge.caliberMm;
            pen = cartridge.penMm;
            kind = cartridge.kind;
        }
        for (Load load : LOADS) {
            if (id.endsWith(load.suffix) || id.contains(load.suffix + "_")) {
                pen *= load.penFactor;
                if (load.kind != null && (cartridge == null || cartridge.kind == ThreatKind.SMALL_ARMS || load.kind != ThreatKind.HE)) kind = load.kind;
                break;
            }
        }
        // Continuum's own armour-piercing fields say the same thing in its terms: more of the hit ignores armour, a round is more able to defeat it.
        pen *= 1.0f + Math.max(0.0f, config.armorPiercingPercent) * 2.0f + config.armorThresholdNegation * 0.02f;
        if (kind == ThreatKind.SMALL_ARMS && caliber >= Threat.SMALL_ARMS_LIMIT_MM) kind = ThreatKind.KINETIC;
        return new Profile(caliber, pen, kind, tracer(id, kind));
    }

    private static int tracer(String id, ThreatKind kind) {
        if (id.contains("_inc") || id.contains("phosphorus")) return 0xFF6A20;
        if (kind == ThreatKind.HE || kind == ThreatKind.HEAT) return 0xFF4040;
        if (id.endsWith("_ap") || id.endsWith("_du")) return 0xFFC040;
        if (id.startsWith("stone") || id.startsWith("g12") || id.startsWith("g10")) return 0;
        return 0xFFE9A8;
    }
}
