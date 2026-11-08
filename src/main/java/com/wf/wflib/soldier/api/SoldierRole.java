package com.wf.wflib.soldier.api;

import java.util.Locale;

/** Tactical doctrine; weapons and protection still come from the equipment actually worn. */
public enum SoldierRole {
    RIFLEMAN("rifle", "Rifleman", "Bounds with the squad and fights at medium range.", 5, 16, 2f, 80, 0, 0),
    GRENADIER("grenadier", "Grenadier", "Uses fragmentation grenades against covered contacts more often.", 4, 18, 2.2f, 80, 0, 0),
    MEDIC("medic", "Field medic", "Carries extra medical supplies; prioritizes rescue behind the firing line.", 3, 24, 2f, 70, 4, 18),
    MACHINEGUNNER("mg", "Machinegunner", "Holds overwatch, fires long suppressive bursts, advances after riflemen.", 16, 22, 2.5f, 100, 6, 24),
    SNIPER("sniper", "Sniper", "Keeps distance, holds covered firing positions and takes deliberate shots.", 1, 45, .45f, 150, 14, 40),
    HEAVY("heavy", "Exoskeleton heavy", "Armored point unit; advances steadily with sustained fire. Requires an exoskeleton.", 10, 16, 1.5f, 90, -2, 0),
    ASSAULT("assault", "Assault", "Takes the forward bound and closes to short-range cover.", 7, 10, 2.4f, 60, -4, 0);

    public final String id, title, description;
    public final int burst, pause, range, formationBack, standOff;
    public final float spread;
    SoldierRole(String id, String title, String description, int burst, int pause, float spread, int range, int back, int standOff) {
        this.id=id; this.title=title; this.description=description; this.burst=burst; this.pause=pause;
        this.spread=spread; this.range=range; this.formationBack=back; this.standOff=standOff;
    }
    public static SoldierRole of(String id) {
        for (var r:values()) if (r.id.equals(id) || r.name().toLowerCase(Locale.ROOT).equals(id)) return r;
        throw new IllegalArgumentException("Unknown combat role: " + id);
    }
    /** Legacy filming orders may have a custom role; their baseline remains rifleman. */
    public static SoldierRole tactics(String id) {
        for (var r:values()) if (r.id.equals(id)) return r;
        return RIFLEMAN;
    }
    public boolean support() { return standOff > 0; }
    public int movePriority() { return this==ASSAULT || this==HEAVY ? -1 : this==SNIPER ? 3 : this==MACHINEGUNNER ? 2 : this==MEDIC ? 1 : 0; }
}
