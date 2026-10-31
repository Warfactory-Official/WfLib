package com.wf.wflib.physics.rigid;

import com.mojang.logging.LogUtils;
import com.sun.management.HotSpotDiagnosticMXBean;
import com.wf.wflib.physics.api.ConvexHull;
import org.joml.Options;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Native (`native/narrow.zig`) world narrowphase: shapes x (boxes, fill prisms) => contacts, as the Java loops of
 * {@code StepBody.worldContacts} with {@link ConvexContacts}. One per thread.
 * <p>Input words: shapeCount, boxCount, triCount, margin, depth, cull, envelope[6]; per shape: hull handle (lo, hi),
 * frame[{@link #FRAME}]; boxes [6] (origin-relative); triangles [13]: section-local vertices[9], ox, oy, oz, tag bits.
 * Output record {@link #RECORD} words: point, normal, separation, tagA, tagB, shape, source (box i, fill -1 - triangle).
 */
public final class NarrowNative {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int VERSION = 2;
    public static final int RECORD = 11;
    public static final int FRAME = 11;
    private static final Map<ConvexHull, Long> HULLS = new WeakHashMap<>();
    private static final boolean AVAILABLE = load();

    private static final Cleaner CLEANER = Cleaner.create();

    private final long context = create();
    private ByteBuffer in = ByteBuffer.allocateDirect(1 << 16).order(ByteOrder.nativeOrder());
    private FloatBuffer inFloats = in.asFloatBuffer();
    private IntBuffer inInts = in.asIntBuffer();
    private long inAddress = address(in);
    private ByteBuffer out = ByteBuffer.allocateDirect(RECORD * 4 * 256).order(ByteOrder.nativeOrder());
    private FloatBuffer outFloats = out.asFloatBuffer();
    private IntBuffer outInts = out.asIntBuffer();
    private long outAddress = address(out);
    private int at;
    private int count;

    public NarrowNative() {
        long handle = context;
        CLEANER.register(this, () -> destroy(handle));
    }

    public static boolean available() {
        return AVAILABLE;
    }

    /**
     * Native copy of {@code hull} (local frame), freed once the hull is unreachable. Same handle per hull.
     * Words: vertexCount, faceCount, axisCount, edgeCount, edgeFaceCount, loopCount, local bounds[6], points, planes,
     * axes, edges, faceStart[faceCount + 1], loops, edgeFaces.
     */
    public static long hull(ConvexHull hull) {
        synchronized (HULLS) {
            Long known = HULLS.get(hull);
            if (known != null) {
                return known;
            }
            int vertices = hull.vertexCount(), faces = hull.faceCount(), loopCount = hull.faceEnd(faces - 1);
            float[] axes = hull.axes(), edges = hull.edgeDirections();
            int[] edgeFaces = hull.edgeFaces();
            int words = 12 + vertices * 3 + faces * 4 + axes.length + edges.length + faces + 1 + loopCount + edgeFaces.length;
            ByteBuffer buffer = ByteBuffer.allocateDirect(words * 4).order(ByteOrder.nativeOrder());
            IntBuffer ints = buffer.asIntBuffer().put(new int[]{vertices, faces, axes.length / 3, edges.length / 3,
                    edgeFaces.length / 3, loopCount});
            buffer.asFloatBuffer().position(6)
                    .put(new float[]{hull.minX(), hull.minY(), hull.minZ(), hull.maxX(), hull.maxY(), hull.maxZ()})
                    .put(hull.points(), 0, vertices * 3).put(hull.planes(), 0, faces * 4).put(axes).put(edges);
            ints.position(words - (faces + 1 + loopCount + edgeFaces.length));
            for (int f = 0; f < faces; f++) {
                ints.put(hull.faceStart(f));
            }
            ints.put(loopCount).put(hull.faceVertices(), 0, loopCount).put(edgeFaces);
            long handle = createHull(address(buffer));
            if (handle == 0) {
                throw new OutOfMemoryError("wfnarrow hull");
            }
            CLEANER.register(hull, () -> destroyHull(handle));
            HULLS.put(hull, handle);
            return handle;
        }
    }

    /**
     * @param frames per shape {@link #FRAME} floats: position, rotation (x, y, z, w), half extents, cull radius
     *               (boxes; hulls cull by their posed local bounds)
     * @param hulls  per shape {@link #hull} handle, 0 = box
     * @param cull   shapes missing envelope (min xyz, max xyz) + margin skipped
     * @return contacts, shape = frame index; read with {@link #x} ... {@link #source}.
     */
    public int worldContacts(int shapeCount, float[] frames, long[] hulls, boolean cull, float[] envelope, float[] boxes,
                             int boxCount, float[] triangles, int triangleCount, float margin, float depth) {
        int words = 12 + shapeCount * (FRAME + 2) + boxCount * 6 + triangleCount * 13;
        if (in.capacity() < words * 4) {
            in = ByteBuffer.allocateDirect(Integer.highestOneBit(words * 4) * 2).order(ByteOrder.nativeOrder());
            inFloats = in.asFloatBuffer();
            inInts = in.asIntBuffer();
            inAddress = address(in);
        }
        at = 0;
        putInt(shapeCount);
        putInt(boxCount);
        putInt(triangleCount);
        putFloat(margin);
        putFloat(depth);
        putInt(cull ? 1 : 0);
        putFloats(envelope, 6);
        for (int s = 0; s < shapeCount; s++) {
            putInt((int) hulls[s]);
            putInt((int) (hulls[s] >>> 32));
            inFloats.put(at, frames, s * FRAME, FRAME);
            at += FRAME;
        }
        putFloats(boxes, boxCount * 6);
        putFloats(triangles, triangleCount * 13);
        int capacity = out.capacity() / (RECORD * 4);
        count = worldContacts(context, inAddress, outAddress, capacity);
        if (count > capacity) {
            out = ByteBuffer.allocateDirect(Integer.highestOneBit(count) * 2 * RECORD * 4).order(ByteOrder.nativeOrder());
            outFloats = out.asFloatBuffer();
            outInts = out.asIntBuffer();
            outAddress = address(out);
            count = worldContacts(context, inAddress, outAddress, out.capacity() / (RECORD * 4));
        }
        return count;
    }

    public float x(int i) { return outFloats.get(i * RECORD); }
    public float y(int i) { return outFloats.get(i * RECORD + 1); }
    public float z(int i) { return outFloats.get(i * RECORD + 2); }
    public float normalX(int i) { return outFloats.get(i * RECORD + 3); }
    public float normalY(int i) { return outFloats.get(i * RECORD + 4); }
    public float normalZ(int i) { return outFloats.get(i * RECORD + 5); }
    public float separation(int i) { return outFloats.get(i * RECORD + 6); }
    public int tagA(int i) { return outInts.get(i * RECORD + 7); }
    public int tagB(int i) { return outInts.get(i * RECORD + 8); }
    public int shape(int i) { return outInts.get(i * RECORD + 9); }
    public int source(int i) { return outInts.get(i * RECORD + 10); }

    private void putInt(int v) {
        inInts.put(at++, v);
    }

    private void putFloat(float v) {
        inFloats.put(at++, v);
    }

    private void putFloats(float[] v, int n) {
        inFloats.put(at, v, 0, n);
        at += n;
    }

    private void putInts(int[] v, int n) {
        inInts.put(at, v, 0, n);
        at += n;
    }

    private static boolean load() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        String platform = (os.startsWith("windows") ? "windows" : os.contains("mac") ? "macos" : os.contains("linux") ? "linux" : null)
                + "-" + switch (arch) {
                    case "amd64", "x86_64" -> "x64";
                    case "aarch64", "arm64" -> "arm64";
                    default -> null;
                };
        String name = System.mapLibraryName(platform.endsWith("x64") && avx2() ? "wfnarrow_v3" : "wfnarrow");
        String resource = "/natives/" + platform + "/" + name;
        try (InputStream stream = NarrowNative.class.getResourceAsStream(resource)) {
            if (stream == null) {
                LOGGER.warn("No native narrowphase for {} ({}): Java path", platform, resource);
                return false;
            }
            Path file = Files.createTempFile("wfnarrow-", "-" + name);
            file.toFile().deleteOnExit();
            Files.copy(stream, file, StandardCopyOption.REPLACE_EXISTING);
            System.load(file.toAbsolutePath().toString());
        } catch (IOException | UnsatisfiedLinkError e) {
            LOGGER.warn("Native narrowphase failed to load: Java path", e);
            return false;
        }
        if (version() != VERSION) {
            throw new IllegalStateException("wfnarrow version " + version() + " != " + VERSION);
        }
        ByteBuffer edgeFaces = ByteBuffer.allocateDirect(36 * 4).order(ByteOrder.nativeOrder());
        edgeFaces.asIntBuffer().put(Polytope.boxEdgeFaces());
        init(address(edgeFaces), Options.USE_MATH_FMA);
        LOGGER.info("Native narrowphase: {}", name);
        return true;
    }

    /** HotSpot UseAVX >= 2 (CPU and OS); other VMs => baseline. */
    private static boolean avx2() {
        try {
            HotSpotDiagnosticMXBean bean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
            return bean != null && Integer.parseInt(bean.getVMOption("UseAVX").getValue()) >= 2;
        } catch (IllegalArgumentException | UnsupportedOperationException | LinkageError e) {
            return false;
        }
    }

    // TODO: FFM downcall (Linker.Option.critical) once toolchain >= 22; on 21 FFM = preview => --enable-preview at launch.
    private static native int version();
    private static native long address(ByteBuffer buffer);
    private static native void init(long edgeFaces, boolean fusedJoml);
    private static native long createHull(long in);
    private static native void destroyHull(long hull);
    private static native long create();
    private static native void destroy(long context);
    private static native int worldContacts(long context, long in, long out, int capacity);
}
