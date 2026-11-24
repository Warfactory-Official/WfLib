package com.wf.wflib.physics.regression;

import com.wf.wflib.physics.api.ConvexHull;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Fixed deterministic scenes, rebuilt identically per call. */
final class Scenes {

    record Scene(HeadlessStep.World world, List<HeadlessStep.Body> bodies) {
    }

    private Scenes() {
    }

    /** One hull-sized box at rest 1 mm above a floor. */
    static Scene resting() {
        HeadlessStep.World world = new HeadlessStep.World().floor(0, 0, 64, 64);
        List<HeadlessStep.Body> bodies = new ArrayList<>();
        bodies.add(new HeadlessStep.Body(1, 8, 0.6 + 0.001, 8, 1.8f, 0.6f, 0.9f, 12).rotated(new Quaternionf().rotateY(0.4f)));
        return new Scene(world, bodies);
    }

    /** Same hull prism instead of a box. */
    static Scene restingHull() {
        HeadlessStep.World world = new HeadlessStep.World().floor(0, 0, 64, 64);
        List<HeadlessStep.Body> bodies = new ArrayList<>();
        bodies.add(new HeadlessStep.Body(1, 8, 0.5 + 0.001, 8, 0.9f, 0.5f, 0.9f, 8).hull(prism(6, 0.9f, 1.0f)));
        return new Scene(world, bodies);
    }

    /** {@code islands} clusters 40 blocks apart, each {@code perIsland} boxes in two leaning-free stacks, slightly offset. */
    static Scene islands(int islands, int perIsland) {
        return islands(islands, perIsland, 77);
    }

    /** Jitter, masses, yaws from {@code seed}. */
    static Scene islands(int islands, int perIsland, long seed) {
        HeadlessStep.World world = new HeadlessStep.World().floor(0, 0, 16 * (islands * 3 + 1), 16);
        List<HeadlessStep.Body> bodies = new ArrayList<>();
        Random random = new Random(seed);
        int id = 1;
        for (int i = 0; i < islands; i++) {
            double cx = 8 + i * 48;
            for (int k = 0; k < perIsland; k++) {
                int column = k % 2, level = k / 2;
                double jitter = (random.nextDouble() - 0.5) * 0.05;
                bodies.add(new HeadlessStep.Body(id++, cx + column * 1.05 + jitter, 0.5 + level * 1.02 + 0.001, 8 + jitter,
                        0.5f, 0.5f, 0.5f, 4 + random.nextFloat()).rotated(new Quaternionf().rotateY(random.nextFloat() * 0.3f)));
            }
        }
        return new Scene(world, bodies);
    }

    /** Islands with ids interleaved across clusters (cluster c gets ids c, c + n, ...). */
    static Scene islandsInterleaved(int islands, int perIsland) {
        return islandsInterleaved(islands, perIsland, 77);
    }

    static Scene islandsInterleaved(int islands, int perIsland, long seed) {
        Scene base = islands(islands, perIsland, seed);
        List<HeadlessStep.Body> bodies = new ArrayList<>();
        for (int k = 0; k < perIsland; k++) {
            for (int i = 0; i < islands; i++) {
                HeadlessStep.Body b = base.bodies.get(i * perIsland + k);
                bodies.add(copy(b, k * islands + i + 1));
            }
        }
        return new Scene(base.world, bodies);
    }

    /** Hull launched along +x at 0.5 b/t over ~20 sections of terrain with 1-block bumps. */
    static Scene fast() {
        HeadlessStep.World world = new HeadlessStep.World().floor(0, 0, 16 * 24, 16);
        Random random = new Random(5);
        for (int s = 1; s < 24; s++) {
            world.add(s * 16 + random.nextInt(12), 0, 4 + random.nextInt(8), s * 16 + 14, 0.25, 15);
        }
        List<HeadlessStep.Body> bodies = new ArrayList<>();
        bodies.add(new HeadlessStep.Body(1, 4, 1.0, 8, 1.8f, 0.6f, 0.9f, 12).moving(0.5f, 0, 0));
        return new Scene(world, bodies);
    }

    static HeadlessStep.Body copy(HeadlessStep.Body b, int id) {
        HeadlessStep.Body c = new HeadlessStep.Body(id, b.x, b.y, b.z, b.ex, b.ey, b.ez, b.mass).rotated(b.q).moving(b.v.x, b.v.y, b.v.z);
        c.hull = b.hull;
        return c;
    }

    /** Regular n-gon prism, radius r, height h, centred. */
    static ConvexHull prism(int n, float radius, float height) {
        float[] points = new float[2 * n * 3];
        int[][] faces = new int[n + 2][];
        faces[0] = new int[n];
        faces[1] = new int[n];
        for (int i = 0; i < n; i++) {
            float angle = i * 6.2831855f / n;
            float x = (float) Math.cos(angle) * radius, z = (float) Math.sin(angle) * radius;
            points[i * 3] = x;
            points[i * 3 + 1] = -height / 2;
            points[i * 3 + 2] = z;
            points[(n + i) * 3] = x;
            points[(n + i) * 3 + 1] = height / 2;
            points[(n + i) * 3 + 2] = z;
            faces[0][i] = n - 1 - i;
            faces[1][i] = n + i;
            faces[2 + i] = new int[]{i, (i + 1) % n, n + (i + 1) % n, n + i};
        }
        return ConvexHull.of(points, faces);
    }
}
