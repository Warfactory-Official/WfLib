package com.wf.wflib.soldier.api;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** The mods a soldier works through, supplied by whichever mod owns the guns, grenades and medicine. */
public final class SoldierServices {
    private static volatile SoldierWeapon weapon;
    /** Further gun mods that work their own guns beside {@link #weapon}; see {@link CombinedWeapon}. */
    private static final java.util.List<SoldierWeapon> EXTRA_WEAPONS = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static volatile SoldierWeapon combined;
    private static volatile SoldierThrowables throwables;
    private static volatile SoldierMedical medical = SoldierMedical.NONE;

    private static final Map<String, Supplier<SoldierSpec>> SIDES = new ConcurrentHashMap<>();

    private SoldierServices() {
    }

    /** Names a side that the {@code /soldiers spawn} command can field; the supplier gives what a new soldier of it is issued (null = none installed). */
    public static void side(String name, Supplier<SoldierSpec> spec) {
        SIDES.put(name, spec);
    }

    public static Set<String> sides() {
        return Collections.unmodifiableSet(new TreeSet<>(SIDES.keySet()));
    }

    public static SoldierSpec spec(String side) {
        var s = SIDES.get(side);
        return s == null ? null : s.get();
    }

    public static void weapon(SoldierWeapon impl) {
        weapon = impl;
        combined = null;
    }

    /**
     * Adds a gun mod beside the one registered with {@link #weapon}: a soldier holding one of its guns works it through {@code impl}, any other through the first
     * registration. Call at setup; the order of calls is the order they are asked in.
     */
    public static void addWeapon(SoldierWeapon impl) {
        EXTRA_WEAPONS.add(impl);
        combined = null;
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
        if (EXTRA_WEAPONS.isEmpty()) {
            if (w == null) throw new IllegalStateException("No SoldierWeapon registered: a gun mod must call SoldierServices.weapon");
            return w;
        }
        SoldierWeapon c = combined;
        if (c == null) {
            java.util.List<SoldierWeapon> all = new java.util.ArrayList<>();
            if (w != null) all.add(w);
            all.addAll(EXTRA_WEAPONS);
            c = all.size() == 1 ? all.get(0) : new CombinedWeapon(all);
            combined = c;
        }
        return c;
    }

    public static boolean hasWeapon() {
        return weapon != null || !EXTRA_WEAPONS.isEmpty();
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
