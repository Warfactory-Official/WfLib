package com.wf.wflib.geom.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every glTF/GLB under {@code assets/}: names, whole bounds, per-node origin + subtree; recorded before {@link Glb}. */
class GltfBoundsGoldenTest {

    static final long GOLDEN = -4387854197496352581L;

    private static long mix(long h, long v) {
        return h * 0x9E3779B97F4A7C15L + v;
    }

    private static long bounds(long h, ObjBounds.Bounds b) {
        h = mix(h, b.any() ? 1 : 0);
        for (double v : new double[]{b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()}) {
            h = mix(h, Double.doubleToLongBits(v));
        }
        return h;
    }

    @Test
    void everyAssetReadsAsBefore() throws IOException {
        Path assets = Path.of("src/main/resources/assets");
        List<Path> files;
        try (Stream<Path> walk = Files.walk(assets)) {
            files = walk.filter(p -> p.toString().endsWith(".gltf") || p.toString().endsWith(".glb")).sorted().toList();
        }
        assertTrue(files.size() > 50, "assets: " + files.size());
        long h = 1;
        int nodes = 0;
        for (Path file : files) {
            Path rel = assets.relativize(file);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(rel.getName(0).toString(),
                    rel.subpath(1, rel.getNameCount()).toString().replace('\\', '/'));
            GltfBounds g = GltfBounds.of(id);
            h = mix(h, id.toString().hashCode());
            h = bounds(h, g.bounds());
            for (String name : g.names()) {
                nodes++;
                h = mix(h, name.hashCode());
                Vec3 o = g.origin(name);
                h = mix(mix(mix(h, Double.doubleToLongBits(o.x)), Double.doubleToLongBits(o.y)),
                        Double.doubleToLongBits(o.z));
                h = bounds(h, g.subtree(name));
            }
        }
        assertEquals(GOLDEN, h, files.size() + " assets, " + nodes + " nodes");
    }
}
