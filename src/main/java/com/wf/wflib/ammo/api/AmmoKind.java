package com.wf.wflib.ammo.api;

import java.util.regex.Pattern;

/**
 * Display class of a load. A load has one: missile > threat > AP > warhead > incendiary > shot > subsonic > tracer. No role field on
 * {@link AmmoVariant}: AP and subsonic come from id role / name; ball blockPen varies too much to compare against.
 */
public enum AmmoKind {
    BALL, TRACER, AP, HE, HEAT, TANDEM, INCENDIARY, SHOT, SUBSONIC, MISSILE;

    private static final Pattern AP_NAME = Pattern.compile("\\b(AP[A-Z]*|SLAP)\\b");
    private static final Pattern HE_NAME = Pattern.compile("\\bHE[A-Z]*\\b");
    private static final Pattern AP_ROLE = Pattern.compile("(^|_)(ap|apc|apds|apfsds|slap|sabot)(_|$)");

    public static AmmoKind of(AmmoVariant v) {
        String path = v.id().getPath();
        String role = path.substring(path.lastIndexOf('/') + 1);
        if (role.contains("missile")) {
            return MISSILE;
        }
        if (v.threat() != null) {
            switch (v.threat()) {
                case TANDEM_HEAT -> {
                    return TANDEM;
                }
                case HEAT -> {
                    return HEAT;
                }
                case HE -> {
                    return HE;
                }
                case KINETIC -> {
                    return AP;
                }
                case SMALL_ARMS -> {
                }
            }
        }
        if (AP_ROLE.matcher(role).find() || AP_NAME.matcher(v.name()).find()) {
            return AP;
        }
        if (v.explosive() != null) {
            return v.explosive().warhead().getPath().equals("shaped_charge") ? HEAT : HE;
        }
        boolean ignites = false;
        for (AmmoVariant.Effect e : v.effects()) {
            String effect = e.id().getPath();
            if (effect.equals("explode")) {
                return HE;
            }
            ignites |= effect.equals("ignite");
        }
        if (ignites) {
            return INCENDIARY;
        }
        if (v.pellets() > 1) {
            return SHOT;
        }
        if (HE_NAME.matcher(v.name()).find()) {
            return HE;
        }
        if (role.contains("subsonic") || v.name().toLowerCase(java.util.Locale.ROOT).contains("subsonic")) {
            return SUBSONIC;
        }
        return v.tracer() != AmmoVariant.NONE ? TRACER : BALL;
    }
}
