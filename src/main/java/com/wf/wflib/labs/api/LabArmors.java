package com.wf.wflib.labs.api;

import com.wf.wflib.armor.api.ArmorSetups;
import com.wf.wflib.labs.LabsConfig;

import java.util.List;
import java.util.regex.Pattern;

/** Armour sets of a lab run: {@link ArmorSetups#all()} minus {@code armorExclude} (wflib-labs.toml), then {@code armor=} globs and dev content per context. */
public final class LabArmors {
    private LabArmors() {
    }

    /** Sets whose garment and inserts match no {@code armorExclude} glob and whose garment matches {@code armor=} (comma globs); dev content only with {@code dev=1}. */
    public static List<ArmorSetups.Setup> sets(LabContext ctx) {
        String filter = ctx.param("armor", null);
        List<String> exclude = LabsConfig.snapshot().armorExclude();
        return ArmorSetups.all().stream()
                .filter(s -> !excluded(exclude, s))
                .filter(s -> filter == null || glob(filter, s.garment()))
                .filter(s -> ctx.includeDev() || !s.dev()).toList();
    }

    /** True when the garment or any insert of {@code s} matches an exclusion glob. */
    public static boolean excluded(List<String> exclude, ArmorSetups.Setup s) {
        if (exclude.isEmpty()) {
            return false;
        }
        if (glob(String.join(",", exclude), s.garment())) {
            return true;
        }
        if (!s.inserts().isEmpty()) {
            for (String i : s.inserts().split("\\+")) {
                if (glob(String.join(",", exclude), i.replaceAll("x\\d+$", ""))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Comma-separated globs, {@code *} wildcard, whole-id match. */
    public static boolean glob(String globs, String id) {
        for (String g : globs.split(",")) {
            if (id.matches(Pattern.quote(g.trim()).replace("*", "\\E.*\\Q"))) {
                return true;
            }
        }
        return false;
    }
}
