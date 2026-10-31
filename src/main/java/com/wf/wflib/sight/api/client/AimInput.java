package com.wf.wflib.sight.api.client;

/**
 * Right mouse, Arma: press {@code < HOLD_TICKS} => ADS toggled on release; longer => {@link #hold} (temporary zoom)
 * while down, ADS unchanged. Time in client ticks, injected.
 */
public final class AimInput {
    /** 0.2 s. */
    public static final int HOLD_TICKS = 4;
    /**
     * World FOV divisor at full hold, on top of the sight zoom. 70 deg -> 50 deg: close look without losing the
     * periphery; PiP lens and picture scale with it.
     */
    public static final float HOLD_ZOOM = 1.4F;
    /** Hold ease progress per tick: 0.15 s in or out. */
    public static final float HOLD_STEP = 1 / 3F;

    public boolean ads;
    public boolean hold;
    private boolean down;
    private int pressAt;

    public void tick(boolean down, int now) {
        if (down && !this.down) {
            pressAt = now;
        } else if (!down && this.down && !hold) {
            ads = !ads;
        }
        hold = down && now - pressAt >= HOLD_TICKS;
        this.down = down;
    }

    /** Press dropped without a toggle (screen opened); ADS kept. */
    public void cancel() {
        down = hold = false;
    }

    public void off() {
        cancel();
        ads = false;
    }

    /** {@code adsProgress}: linear ADS; sprint waits until it is back at hip. */
    public boolean sprintAllowed(float adsProgress) {
        return !ads && adsProgress <= 0;
    }

    /** World FOV divisor of eased hold progress {@code e}. */
    public static float zoom(float e) {
        return 1 + (HOLD_ZOOM - 1) * e;
    }
}
