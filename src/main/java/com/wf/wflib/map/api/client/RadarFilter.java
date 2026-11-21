package com.wf.wflib.map.api.client;

import net.minecraft.world.entity.Entity;

/**
 * Decides how a map's entity radar treats one entity: a mod that knows what an entity is (a soldier, a drone) says whether
 * it belongs on the map and how it is labelled. Registered with {@link WFMaps#radar}; the first filter with an opinion wins.
 */
@FunctionalInterface
public interface RadarFilter {
    /** @return null for no opinion (the map's own rules apply) */
    Verdict judge(Entity entity);

    /**
     * @param show false keeps the entity off the map
     * @param label what to write next to it (null: the map's own label)
     * @param color the dot's colour as 0xRRGGBB, or -1 for the map's own
     * @param small draw a small dot instead of the map's own icon
     */
    record Verdict(boolean show, String label, int color, boolean small) {
        public static final Verdict HIDE = new Verdict(false, null, -1, false);

        public static Verdict dot(String label, int color) {
            return new Verdict(true, label, color, true);
        }
    }
}
