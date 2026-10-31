package com.wf.wflib.core.api;

/**
 * The cross-system order of {@link ServerTicks} steps; higher runs first. Gaps leave room for dependents. Written
 * down here and nowhere else.
 */
public final class TickOrder {
    private TickOrder() {
    }

    // LEVEL_PRE
    public static final int SIM_PRE = 1000;

    // LEVEL_POST
    public static final int PHYSICS = 2000;
    /** Joints between stepped bodies: constraint gets the last word on poses. */
    public static final int COUPLING = 1990;
    /** Bodies carried by non-stepped carriers, after their carrier moved. */
    public static final int CARRIED = 1980;
    public static final int SIM_EARLY = 1000;
    public static final int DRONE = 900;
    /** After the drones have moved, so a zone is judged on where everyone actually is this tick. */
    public static final int EXCHANGE = 800;
    public static final int WORK = 700;
    public static final int BUILD = 600;
    public static final int INDUSTRY = 500;
    public static final int COLONY = 400;
    /** After the colony tier, because it reassigns bugs the materialiser may have only just placed. */
    public static final int COLONY_SQUADS = 390;
    public static final int SIM_LATE = 300;
    public static final int COLONY_TRACKING = 290;
    public static final int MISSILE_GHOSTS = 280;
    public static final int COLONY_SEPARATION = 270;
    public static final int COLONY_BRIDGES = 260;
    /** Last of the colony steps, so a field built this tick is flooded from terrain the diggers already changed. */
    public static final int COLONY_FLOW = 250;
    public static final int RECON = 200;
    /** After {@link #RECON}, before {@link #RECON_MAP}. */
    public static final int ORBITAL = 190;
    public static final int RECON_MAP = 180;
    public static final int CAM = 170;
    public static final int RAIL = 150;
    /** Last: state snapshots to predicting clients after every pose writer. */
    public static final int PREDICTION = 100;

    // STARTING
    public static final int WORKERS_START = 1000;
    public static final int SIM_START = 900;

    // STOPPING: sim first, then the recon > orbital > recon map chain.
    public static final int SIM_STOP = 1000;
    public static final int RECON_STOP = 900;
    public static final int ORBITAL_STOP = 890;
    public static final int RECON_MAP_STOP = 880;
    public static final int COLONY_STOP = 870;
    public static final int RECON_STATE_STOP = 860;
    public static final int WORKERS_STOP = 850;
    public static final int INDUSTRY_STOP = 840;
    public static final int COLONY_NAV_STOP = 830;
    public static final int BENCH_STOP = 820;
    public static final int RAIL_STOP = 810;
}
