package com.wf.wflib.soldier.api;

/**
 * One soldier a source puts up for hire.
 *
 * @param id unique within the source's offers
 * @param variant a saved soldier variant (the soldier editor's), what arrives
 * @param title what the menu shows
 * @param cost what it costs
 * @param delaySeconds how long until the soldier arrives at the hirer (0: at once, beside them)
 */
public record Offer(String id, String title, String variant, Cost cost, int delaySeconds) {
    public Offer {
        if (id == null || id.isBlank() || variant == null || variant.isBlank()) throw new IllegalArgumentException("An offer needs an id and a variant");
        if (title == null) title = variant;
        if (cost == null) cost = Cost.FREE;
        if (delaySeconds < 0) delaySeconds = 0;
    }
}
