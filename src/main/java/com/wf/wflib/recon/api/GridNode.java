package com.wf.wflib.recon.api;

import net.minecraft.core.BlockPos;

/** One thing on the grid: a hub, a probe, or anything else that can carry a link. */
public final class GridNode {

    private final long id;
    private final long netId;
    private final BlockPos pos;

    private volatile double linkRange;
    private volatile double mastHeight;
    private volatile boolean root;
    private volatile String label;
    private volatile long lastRefresh;
    private volatile boolean live = true;

    // Published by commitRoute; relink works on the pending* copies so a reader never sees the cleared mid-state.
    private volatile int hops = -1;
    private volatile long uplink;
    private volatile int downstream;
    private int pendingHops = -1;
    private long pendingUplink;
    private int pendingDownstream;

    GridNode(long netId, BlockPos pos, double linkRange, double mastHeight, boolean root, String label, long gameTime) {
        this.id = pos.asLong();
        this.netId = netId;
        this.pos = pos.immutable();
        this.linkRange = linkRange;
        this.mastHeight = mastHeight;
        this.root = root;
        this.label = label;
        this.lastRefresh = gameTime;
    }

    public long id() {
        return id;
    }

    public BlockPos pos() {
        return pos;
    }

    /**
     * @return the height a link is measured from. The same mast that decides what a sensor can see over decides
     *      what it can talk to, which is the whole reason clearance is one mechanic rather than two.
     */
    public double eyeY() {
        return pos.getY() + 0.5 + mastHeight;
    }

    public double linkRange() {
        return linkRange;
    }

    public boolean root() {
        return root;
    }

    public String label() {
        return label;
    }

    /**
     * @return links between this node and the hub, or {@code -1} if there is no path at all. Zero is the hub
     *      itself.
     */
    public int hops() {
        return hops;
    }

    public boolean online() {
        return hops >= 0;
    }

    /**
     * @return the node this one reaches the hub through, or {@code 0} for a root or an orphan.
     */
    public long uplink() {
        return uplink;
    }

    /**
     * @return how many nodes route through this one. Break this block and that many go dark with it.
     */
    public int downstream() {
        return downstream;
    }

    long lastRefresh() {
        return lastRefresh;
    }

    void refresh(double linkRange, double mastHeight, boolean root, String label, long gameTime) {
        this.linkRange = linkRange;
        this.mastHeight = mastHeight;
        this.root = root;
        this.label = label;
        this.lastRefresh = gameTime;
    }

    long netId() {
        return netId;
    }

    /** False once the grid has dropped this node; a holder must re-register. */
    public boolean live() {
        return live;
    }

    void kill() {
        this.live = false;
    }

    /** Owner-thread refresh of an unchanged node: no grid mutation. */
    void touch(long gameTime) {
        this.lastRefresh = gameTime;
    }

    boolean matches(double linkRange, double mastHeight, boolean root, String label) {
        return this.linkRange == linkRange && this.mastHeight == mastHeight && this.root == root
                && this.label.equals(label);
    }

    void clearRoute() {
        this.pendingHops = -1;
        this.pendingUplink = 0L;
        this.pendingDownstream = 0;
    }

    void route(int hops, long uplink) {
        this.pendingHops = hops;
        this.pendingUplink = uplink;
    }

    void addDownstream() {
        this.pendingDownstream++;
    }

    int pendingHops() {
        return pendingHops;
    }

    long pendingUplink() {
        return pendingUplink;
    }

    void commitRoute() {
        this.uplink = pendingUplink;
        this.downstream = pendingDownstream;
        this.hops = pendingHops;
    }

    /**
     * @return true if a link between these two is short enough to try. Range is the shorter of the two sets,
     *      because a link is only as good as its weaker end.
     */
    boolean withinRange(GridNode other) {
        double reach = Math.min(this.linkRange, other.linkRange);
        double dx = this.pos.getX() - other.pos.getX();
        double dy = this.eyeY() - other.eyeY();
        double dz = this.pos.getZ() - other.pos.getZ();
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }
}
