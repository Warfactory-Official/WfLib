package com.wf.wflib.physics.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Client predicting ahead of a lagging server that diverges every few dozen ticks (corrections + replays), with idle
 * gaps (prediction dropped and restarted) and synced-data resyncs. Hash of counters + every predicted snapshot,
 * recorded against the source copy.
 */
class PredictionGoldenTest {

    static final long GOLDEN = 8953088154427941732L;
    private static final int TICKS = 600, LAG = 5;

    static final class Input {
        float throttle, steer;
    }

    /** Snapshot: pos (3 d), rot (3 f), vel (3 d), power (f). */
    static final class Body implements Predictable<Input> {
        double x, y, z, vx, vy, vz;
        float pitch, yaw, roll, power;
        final Input control = new Input();
        Prediction<Input> prediction;
        int afterReplay;

        void step() {
            power = power * 0.9f + control.throttle * 0.1f;
            yaw += control.steer * 2.5f;
            double heading = Math.toRadians(yaw);
            vx = vx * 0.98 + Math.sin(heading) * power * 0.05;
            vz = vz * 0.98 + Math.cos(heading) * power * 0.05;
            vy = (vy - 0.04) * 0.98;
            x += vx;
            y += vy;
            z += vz;
            if (y < 0) {
                y = 0;
                vy = 0;
            }
            pitch = (float) (vy * 10);
            roll = control.steer * (float) Math.hypot(vx, vz) * 4;
        }

        @Override public int getId() { return 1; }
        @Override public Level level() { return null; }
        @Override public boolean isRemoved() { return false; }
        @Override public Input newInput() { return new Input(); }
        @Override public void copyInput(Input from, Input into) { into.throttle = from.throttle; into.steer = from.steer; }
        @Override public void applyInput(Input input) { copyInput(input, control); }
        @Override public void predictStep() { step(); }
        @Override public void afterReplayStep() { afterReplay++; }

        @Override
        public byte[] snapshot() {
            return ByteBuffer.allocate(3 * 8 + 3 * 4 + 3 * 8 + 4).putDouble(x).putDouble(y).putDouble(z)
                    .putFloat(pitch).putFloat(yaw).putFloat(roll).putDouble(vx).putDouble(vy).putDouble(vz)
                    .putFloat(power).array();
        }

        @Override
        public void restoreSnapshot(byte[] state) {
            ByteBuffer b = ByteBuffer.wrap(state);
            x = b.getDouble(); y = b.getDouble(); z = b.getDouble();
            pitch = b.getFloat(); yaw = b.getFloat(); roll = b.getFloat();
            vx = b.getDouble(); vy = b.getDouble(); vz = b.getDouble();
            power = b.getFloat();
        }

        @Override public Prediction<Input> prediction() { return prediction; }
        @Override public ServerPlayer predictionOperator() { return null; }
        @Override public int inputSeq() { return 0; }
    }

    private static long mix(long h, long v) {
        return h * 0x9E3779B97F4A7C15L + v;
    }

    @Test
    void predictionFollowsTheSource() {
        long m0 = Prediction.matched(), c0 = Prediction.corrected(), r0 = Prediction.replayed();
        Body client = new Body(), server = new Body();
        client.y = server.y = 3;
        ArrayDeque<Object[]> wire = new ArrayDeque<>();
        Input input = new Input();
        long h = 1;
        int seq = 0;
        for (int t = 0; t < TICKS; t++) {
            boolean idle = t % 97 >= 90;
            if (!idle) {
                input.throttle = (float) Math.sin(t * 0.05) * 0.5f + 0.5f;
                input.steer = (float) Math.sin(t * 0.13 + 1);
                if (client.prediction == null) {
                    client.prediction = new Prediction<>(client);
                }
                client.prediction.input(client, input, seq);
                server.applyInput(input);
                server.step();
                if (t % 41 == 17) {
                    server.vx += 0.02;
                    server.yaw += 0.3f;
                }
                wire.add(new Object[]{t + LAG, seq, server.snapshot()});
                seq++;
            }
            while (!wire.isEmpty() && (int) wire.peek()[0] <= t) {
                Object[] ack = wire.poll();
                if (client.prediction != null) {
                    client.prediction.received((int) ack[1], (byte[]) ack[2]);
                }
            }
            if (client.prediction != null && t % 11 == 5) {
                client.prediction.resync(client);
            }
            if (client.prediction != null && !client.prediction.tick(client)) {
                client.prediction = null;
                client.restoreSnapshot(server.snapshot());
            }
            for (byte b : client.snapshot()) {
                h = mix(h, b);
            }
        }
        long matched = Prediction.matched() - m0, corrected = Prediction.corrected() - c0;
        long replayed = Prediction.replayed() - r0;
        assertTrue(matched > 0 && corrected > 0 && replayed > 0, matched + "/" + corrected + "/" + replayed);
        h = mix(mix(mix(mix(h, matched), corrected), replayed), client.afterReplay);
        assertEquals(GOLDEN, h, "prediction hash; matched " + matched + " corrected " + corrected + " replayed " + replayed);
    }
}
