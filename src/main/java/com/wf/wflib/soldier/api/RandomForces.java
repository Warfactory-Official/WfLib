package com.wf.wflib.soldier.api;

import com.wf.wflib.soldier.SoldierCatalogue;
import com.wf.wflib.soldier.SoldierRoster;
import com.wf.wflib.soldier.SoldierTemplate;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * The soldiers a faction fields when nobody has set it up in the soldier editor: three variants for each of four grades (conscript to
 * elite), with a random gun, armour and role each, redrawn every in-game day. A faction that has editor variants of its own is left alone.
 * Variant ids start with {@code pmc_}, which is how they are told apart from the editor's.
 */
public final class RandomForces {
    private static final int PER_GRADE = 3;
    private static final double[] SPREAD = {3.0, 2.2, 1.6, 1.0};
    private static final int[] RANGE = {60, 80, 100, 120}, SPARES = {2, 3, 4, 6};
    private static final double[][] ARMOUR_CHANCE = {{0.25, 0.1, 0, 0}, {0.6, 0.6, 0.3, 0.2}, {0.9, 0.9, 0.7, 0.5}, {1, 1, 1, 0.9}};
    private static final String[] SLOTS = {"head", "chest", "legs", "feet"};

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("wflib/soldier");
    private static SoldierCatalogue catalogue;

    /** The grade's name, 0 (conscript) to 3 (elite); out-of-range grades clamp. */
    public static String gradeName(int grade) {
        return switch (Math.max(0, Math.min(3, grade))) {
            case 0 -> "Conscript";
            case 1 -> "Regular";
            case 2 -> "Veteran";
            default -> "Elite";
        };
    }

    private RandomForces() {
    }

    static synchronized SoldierCatalogue catalogue() {
        if (catalogue == null) catalogue = SoldierCatalogue.create();
        return catalogue;
    }

    /** Server stopping: the catalogue follows the installed mods, so it is rebuilt next time. */
    public static synchronized void forget() {
        catalogue = null;
    }

    public static boolean managed(String id) {
        return id.startsWith("pmc_");
    }

    /** The first version of this mod made {@code <faction>_rifle} and {@code <faction>_assault} (both named just for their role); they are replaced. */
    private static boolean legacy(String id, String faction) {
        return id.equals(faction + "_rifle") || id.equals(faction + "_assault");
    }

    /** Server start: the pool exists and has no leftovers of the first version, so a hire list never shows the old plain-role soldiers. */
    public static void ensureCurrent(MinecraftServer server, String faction) {
        SoldierRoster roster = SoldierRoster.get(server);
        if (!own(roster, faction).isEmpty() || !roster.factions().containsKey(faction)) return;
        if (pool(roster, faction).isEmpty() || roster.units().values().stream().anyMatch(u -> u.faction().equals(faction) && legacy(u.id(), faction))) {
            regenerate(server, faction, server.overworld().getGameTime() / 24000L);
        }
    }

    static String id(String faction, int grade, int k) {
        String id = "pmc_" + faction + "_g" + grade + "_" + k;
        return id.length() <= 32 ? id : null;
    }

    /** Variant ids the editor made for {@code faction}. */
    private static List<String> own(SoldierRoster roster, String faction) {
        return roster.units().values().stream().filter(u -> u.faction().equals(faction) && !managed(u.id()) && !legacy(u.id(), faction)).map(SoldierTemplate::id).toList();
    }

    /** Every variant of {@code faction}: its editor ones, else the generated pool (made now if there is none). Empty with no gun installed. */
    public static List<String> all(MinecraftServer server, String faction) {
        SoldierRoster roster = SoldierRoster.get(server);
        List<String> own = own(roster, faction);
        if (!own.isEmpty() || !roster.factions().containsKey(faction)) return own;
        List<String> pool = pool(roster, faction);
        if (pool.isEmpty()) regenerate(server, faction, server.overworld().getGameTime() / 24000L);
        return pool(roster, faction);
    }

    /** Variants fit for {@code grade}: the generated ones of that grade (or the nearest below), else all of the editor's. */
    public static List<String> units(MinecraftServer server, String faction, int grade) {
        SoldierRoster roster = SoldierRoster.get(server);
        List<String> own = own(roster, faction);
        if (!own.isEmpty()) return own;
        all(server, faction);
        for (int g = Math.max(0, Math.min(3, grade)); g >= 0; g--) {
            final int gg = g;
            List<String> ids = roster.units().values().stream().filter(u -> u.faction().equals(faction) && managed(u.id())
                    && u.id().contains("_g" + gg + "_")).map(SoldierTemplate::id).toList();
            if (!ids.isEmpty()) return ids;
        }
        return List.of();
    }

    private static List<String> pool(SoldierRoster roster, String faction) {
        return roster.units().values().stream().filter(u -> u.faction().equals(faction) && managed(u.id())).map(SoldierTemplate::id).toList();
    }

    /** Redraws the generated pool for {@code faction} from {@code day}; no effect when the editor has variants for it. */
    public static void regenerate(MinecraftServer server, String faction, long day) {
        SoldierRoster roster = SoldierRoster.get(server);
        if (!own(roster, faction).isEmpty() || !roster.factions().containsKey(faction)) return;
        SoldierCatalogue cat = catalogue();
        List<SoldierCatalogue.Choice> guns = cat.all().stream().filter(c -> c.slot().equals("gun")).toList();
        if (guns.isEmpty()) {
            LOGGER.warn("No gun is installed, so faction '{}' cannot be given soldiers", faction);
            return;
        }
        for (SoldierTemplate old : List.copyOf(roster.units().values())) {
            if (old.faction().equals(faction) && legacy(old.id(), faction)) roster.removeUnit(old.id());
        }
        Random rnd = new Random(server.overworld().getSeed() * 31 + day * 7919L + faction.hashCode());
        boolean hostile = roster.factions().get(faction).hostile();
        int made = 0;
        for (int grade = 0; grade < 4; grade++) {
            for (int k = 0; k < PER_GRADE; k++) {
                String id = id(faction, grade, k);
                if (id == null) continue;
                SoldierTemplate t = draw(rnd, cat, guns, faction, id, grade, hostile);
                try {
                    roster.put(t);
                    made++;
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Generated soldier {} refused: {}", id, e.getMessage());
                }
            }
        }
        LOGGER.info("Faction '{}': {} soldiers drawn for day {}", faction, made, day + 1);
    }

    /** One soldier of {@code grade}: a role, a gun, armour and grenades from what the catalogue offers, validated. */
    static SoldierTemplate draw(Random rnd, SoldierCatalogue cat, List<SoldierCatalogue.Choice> guns, String faction, String id, int grade, boolean hostile) {
        SoldierRole role = role(rnd);
        String gun = guns.get(rnd.nextInt(guns.size())).key();
        List<String> grenades = new ArrayList<>();
        List<SoldierCatalogue.Choice> nades = cat.all().stream().filter(c -> c.slot().equals("grenade")).toList();
        if (!nades.isEmpty() && grade >= 2) {
            for (int i = 0; i < grade - 1; i++) grenades.add(nades.get(rnd.nextInt(nades.size())).key());
        }
        int range = Math.min(160, RANGE[grade] + (role == SoldierRole.SNIPER ? 50 : 0));
        float spread = (float) (SPREAD[grade] * (role == SoldierRole.SNIPER ? 0.5 : 1));
        String name = com.wf.wflib.soldier.api.SoldierNames.random(rnd, hostile).replace('_', ' ') + " (" + gradeName(grade) + ")";
        for (int attempt = 0; attempt < 6; attempt++) {
            Map<String, String> armour = attempt == 5 ? Map.of() : pick(rnd, cat, grade);
            SoldierTemplate t = new SoldierTemplate(id, name.length() > 48 ? name.substring(0, 48) : name, faction, role, gun, List.copyOf(grenades), armour,
                    SPARES[grade], range, Math.max(0.25f, spread));
            try {
                cat.validate(t);
                return t;
            } catch (IllegalArgumentException e) {
                // overlapping pieces or an unusable gun: draw again
            }
            if (attempt == 4) grenades.clear();
        }
        return new SoldierTemplate(id, name, faction, SoldierRole.RIFLEMAN, gun, List.of(), Map.of(), SPARES[grade], RANGE[grade], (float) SPREAD[grade]);
    }

    private static Map<String, String> pick(Random rnd, SoldierCatalogue cat, int grade) {
        Map<String, String> armour = new TreeMap<>();
        for (int s = 0; s < SLOTS.length; s++) {
            if (rnd.nextDouble() >= ARMOUR_CHANCE[grade][s]) continue;
            final String slot = SLOTS[s];
            List<SoldierCatalogue.Choice> options = cat.all().stream().filter(c -> c.slot().equals(slot) && !c.exo()).toList();
            if (!options.isEmpty()) armour.put(slot, options.get(rnd.nextInt(options.size())).key());
        }
        return armour;
    }

    private static SoldierRole role(Random rnd) {
        int r = rnd.nextInt(100);
        return r < 50 ? SoldierRole.RIFLEMAN : r < 65 ? SoldierRole.ASSAULT : r < 75 ? SoldierRole.MACHINEGUNNER : r < 85 ? SoldierRole.SNIPER
                : r < 95 ? SoldierRole.MEDIC : SoldierRole.GRENADIER;
    }
}
