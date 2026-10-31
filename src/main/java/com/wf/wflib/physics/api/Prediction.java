package com.wf.wflib.physics.api;

import com.wf.wflib.physics.predict.PredictionAcks;

import java.nio.ByteBuffer;

/**
 * Operator-side prediction of one {@link Predictable}: inputs and post-step snapshots by seq, reconciled against the
 * server's acked snapshot. Client tick thread; {@link #due} is the server's sender.
 */
public final class Prediction<I> {

    private static final int HISTORY = 64;
    private static final double POSITION = 1.0e-3, VELOCITY = 1.0e-3;
    private static final float ROTATION = 0.05f;
    /** Ticks without an input before the body falls back to the server's pose. */
    private static final int IDLE = 2;

    /** Client counters (harness probes): acks matching the prediction, corrected, steps replayed. */
    private static long matched, corrected, replayed;

    public static long matched() {
        return matched;
    }

    public static long corrected() {
        return corrected;
    }

    public static long replayed() {
        return replayed;
    }

    private final int[] seqs = new int[HISTORY];
    private final Object[] inputs = new Object[HISTORY];
    private final byte[][] states = new byte[HISTORY][];
    /** Input to predict this tick, -1 none. */
    private int pending = -1;
    /** Last predicted seq, -1 none. */
    private int latest = -1;
    private int ack = -1;
    private byte[] ackState;
    private int idle;

    public Prediction(Predictable<I> body) {
        for (int i = 0; i < HISTORY; i++) {
            seqs[i] = -1;
            inputs[i] = body.newInput();
        }
    }

    @SuppressWarnings("unchecked")
    private I inputAt(int seq) {
        return (I) inputs[seq & (HISTORY - 1)];
    }

    public void input(Predictable<I> body, I input, int seq) {
        int slot = seq & (HISTORY - 1);
        seqs[slot] = seq;
        body.copyInput(input, inputAt(seq));
        pending = seq;
    }

    public void received(int ack, byte[] state) {
        if (ack > this.ack) {
            this.ack = ack;
            ackState = state;
        }
    }

    /** Body client tick. False => no input for {@link #IDLE} ticks: stop predicting. */
    public boolean tick(Predictable<I> body) {
        if (pending < 0) {
            return ++idle <= IDLE;
        }
        idle = 0;
        resync(body);
        reconcile(body);
        body.applyInput(inputAt(pending));
        body.predictStep();
        store(pending, body.snapshot());
        latest = pending;
        pending = -1;
        return true;
    }

    /**
     * Synced data written since the last step is the server's, ack-old: back to the latest prediction. Also on
     * arrival: packets apply per frame => HUD read the stale value between ticks.
     */
    public void resync(Predictable<I> body) {
        if (latest >= 0 && seqs[latest & (HISTORY - 1)] == latest) {
            body.restoreSnapshot(states[latest & (HISTORY - 1)]);
        }
    }

    private void reconcile(Predictable<I> body) {
        byte[] server = ackState;
        if (server == null) {
            return;
        }
        ackState = null;
        int slot = ack & (HISTORY - 1);
        if (seqs[slot] == ack && states[slot] != null && close(states[slot], server)) {
            matched++;
            return;
        }
        corrected++;
        body.restoreSnapshot(server);
        for (int seq = ack + 1; seq <= latest && seqs[seq & (HISTORY - 1)] == seq; seq++) {
            body.applyInput(inputAt(seq));
            body.predictStep();
            body.afterReplayStep();
            store(seq, body.snapshot());
            replayed++;
        }
    }

    private void store(int seq, byte[] state) {
        states[seq & (HISTORY - 1)] = state;
    }

    private static boolean close(byte[] a, byte[] b) {
        ByteBuffer x = ByteBuffer.wrap(a), y = ByteBuffer.wrap(b);
        for (int i = 0; i < 3; i++) {
            if (!(Math.abs(x.getDouble() - y.getDouble()) <= POSITION)) {
                return false;
            }
        }
        for (int i = 0; i < 3; i++) {
            float d = x.getFloat() - y.getFloat();
            if (!(Math.abs(d - 360 * Math.round(d / 360)) <= ROTATION)) {
                return false;
            }
        }
        for (int i = 0; i < 3; i++) {
            if (!(Math.abs(x.getDouble() - y.getDouble()) <= VELOCITY)) {
                return false;
            }
        }
        return true;
    }

    /** Server tick thread: consumed an input this tick => snapshot to its operator after the step. */
    public static void due(Predictable<?> body) {
        PredictionAcks.due(body);
    }
}
