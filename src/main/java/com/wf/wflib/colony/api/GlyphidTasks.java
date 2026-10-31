package com.wf.wflib.colony.api;

/** The orders a glyphid can be under. */
public final class GlyphidTasks {

    public static final int TASK_IDLE = 0;
    static final int TASK_RETREAT_FOR_REINFORCEMENTS = 1;
    public static final int TASK_BUILD_HIVE = 2;
    public static final int TASK_INITIATE_RETREAT = 3;
    public static final int TASK_FOLLOW = 4;
    static final int TASK_TERRAFORM = 5;
    public static final int TASK_DIG = 6;

    /** Radius of the waypoint dropped on a block a glyphid decided to dig through. */
    static final int DIG_WAYPOINT_RADIUS = 5;
    /** Squared distance at which a glyphid with no waypoint counts as having arrived. */
    static final int DEFAULT_DESTINATION_RADIUS_SQ = 25;
    /** How often a glyphid standing on its dig site takes another bite. */
    static final int DIG_EXPLOSION_INTERVAL_TICKS = 20;

    private GlyphidTasks() {
    }
}
