package com.wf.wflib.soldier.api;

/** The mods a soldier works through, supplied by whichever mod owns the guns, grenades and medicine. */
public final class SoldierServices {
    private static volatile SoldierWeapon weapon;
    private static volatile SoldierThrowables throwables;
    private static volatile SoldierMedical medical = SoldierMedical.NONE;

    private SoldierServices() {
    }

    public static void weapon(SoldierWeapon impl) {
        weapon = impl;
    }

    public static void throwables(SoldierThrowables impl) {
        throwables = impl;
    }

    public static void medical(SoldierMedical impl) {
        medical = impl == null ? SoldierMedical.NONE : impl;
    }

    /** @throws IllegalStateException when no gun mod has registered one: a soldier cannot fight without */
    public static SoldierWeapon weapon() {
        SoldierWeapon w = weapon;
        if (w == null) throw new IllegalStateException("No SoldierWeapon registered: a gun mod must call SoldierServices.weapon");
        return w;
    }

    public static boolean hasWeapon() {
        return weapon != null;
    }

    public static SoldierThrowables throwables() {
        SoldierThrowables t = throwables;
        if (t == null) throw new IllegalStateException("No SoldierThrowables registered");
        return t;
    }

    public static boolean hasThrowables() {
        return throwables != null;
    }

    public static SoldierMedical medical() {
        return medical;
    }
}
