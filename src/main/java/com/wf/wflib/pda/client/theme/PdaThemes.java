package com.wf.wflib.pda.client.theme;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Installed themes by id, in menu order. Spike: the built-ins only; resource-pack themes plug in here later. */
public final class PdaThemes {
    private static final Map<String, PdaTheme> THEMES = new LinkedHashMap<>();

    static {
        for (PdaTheme theme : List.of(PdaTheme.CLASSIC, PdaTheme.GAMMA, PdaTheme.ZONE, PdaTheme.PHOSPHOR)) {
            THEMES.put(theme.id(), theme);
        }
    }

    private PdaThemes() {
    }

    public static PdaTheme get(String id) {
        return THEMES.getOrDefault("tactical".equals(id) ? "gamma" : id, PdaTheme.CLASSIC);
    }

    public static List<PdaTheme> all() {
        return List.copyOf(THEMES.values());
    }

    /** Next installed theme after {@code id}, wrapping; {@code step} -1 goes back. */
    public static PdaTheme step(String id, int step) {
        List<PdaTheme> all = all();
        int at = 0;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id().equals(id)) {
                at = i;
            }
        }
        return all.get(Math.floorMod(at + step, all.size()));
    }
}
