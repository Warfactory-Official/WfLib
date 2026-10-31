package com.wf.wflib.packs.api;

import com.google.gson.JsonObject;
import com.wf.wflib.packs.api.PackHost.Provider;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackHostTest {
    static final PackSpec SPEC = PackSpec.builder("fakemod", "Fake").keys("tab").packFormat(34)
            .hashed(path -> path.startsWith("fakemod/") && path.endsWith(".json")).build();

    @TempDir
    Path tmp;

    static String mcmeta(String section, String format) {
        return "{\"pack\":{\"pack_format\":" + format + ",\"description\":\"d\"},\"fakemod\":" + section + "}";
    }

    static Path pack(Path parent, String dir, String section) throws IOException {
        Path p = parent.resolve(dir);
        Files.createDirectories(p);
        Files.writeString(p.resolve("pack.mcmeta"), mcmeta(section, "34"));
        return p;
    }

    static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    PackHost host(Path jar, Path dir) {
        List<Provider> providers = jar == null ? List.of() : List.of(new Provider("addon", jar));
        return PackHost.detached(SPEC, providers, dir);
    }

    static String problems(PackHost h) {
        return String.join("\n", assertThrows(PackException.class, h::rescan).problems());
    }

    @Test
    void originsAndOrder() throws IOException {
        Path jar = tmp.resolve("jar"), dir = tmp.resolve("packs");
        pack(jar, "zeta", "{\"id\":\"zeta\"}");
        pack(dir, "beta", "{\"id\":\"beta\",\"requires\":[\"zeta\"],\"tab\":\"t\"}");
        Path zipped = Files.createTempDirectory(tmp, "z");
        Files.writeString(zipped.resolve("pack.mcmeta"), mcmeta("{\"id\":\"alpha\"}", "34"));
        try (OutputStream out = Files.newOutputStream(dir.resolve("alpha.zip")); ZipOutputStream z = new ZipOutputStream(out)) {
            z.putNextEntry(new ZipEntry("pack.mcmeta"));
            z.write(Files.readAllBytes(zipped.resolve("pack.mcmeta")));
            z.closeEntry();
        }
        PackHost h = host(jar, dir);
        h.rescan();
        List<Pack> packs = h.packs();
        assertEquals(List.of("zeta", "alpha", "beta"), packs.stream().map(Pack::id).toList());
        assertEquals(List.of(Pack.Origin.JAR, Pack.Origin.ARCHIVE, Pack.Origin.DIRECTORY),
                packs.stream().map(Pack::origin).toList());
        assertEquals("addon", packs.get(0).mod());
        assertEquals("", packs.get(2).mod());
        assertEquals("t", packs.get(2).manifest().get("tab").getAsString());
        assertEquals(List.of("zeta"), packs.get(2).requires());
        assertEquals(64, packs.get(0).sha256().length());
    }

    @Test
    void rejectsEachEnvelopeFault() throws IOException {
        Path dir = tmp.resolve("packs");
        pack(dir, "a", "{\"id\":\"dup\"}");
        pack(dir, "b", "{\"id\":\"dup\"}");
        assertTrue(problems(host(null, dir)).contains("id dup also used by a"));
        Path d2 = tmp.resolve("p2");
        pack(d2, "a", "{\"id\":\"BAD\"}");
        pack(d2, "b", "{\"id\":\"b\",\"nope\":1}");
        pack(d2, "c", "{\"id\":\"c\",\"requires\":[\"ghost\"]}");
        Files.writeString(pack(d2, "d", "{\"id\":\"d\"}").resolve("pack.mcmeta"), mcmeta("{\"id\":\"d\"}", "33"));
        String all = problems(host(null, d2));
        assertTrue(all.contains("invalid pack id"), all);
        assertTrue(all.contains("fakemod.nope: unknown field"), all);
        Path junk = tmp.resolve("junk");
        write(junk.resolve("junk.txt"), "x");
        assertTrue(problems(host(null, junk)).contains("junk.txt: not a pack"));
        d2 = tmp.resolve("p3");
        pack(d2, "c", "{\"id\":\"c\",\"requires\":[\"ghost\"]}");
        assertTrue(problems(host(null, d2)).contains("requires ghost not installed"));
        d2 = tmp.resolve("p4");
        Files.writeString(pack(d2, "d", "{\"id\":\"d\"}").resolve("pack.mcmeta"), mcmeta("{\"id\":\"d\"}", "33"));
        assertTrue(problems(host(null, d2)).contains("pack.pack_format: 33 != 34"));
    }

    @Test
    void jarDuplicateNamesTheFileToRemove() throws IOException {
        Path jar = tmp.resolve("jar"), dir = tmp.resolve("packs");
        pack(jar, "x", "{\"id\":\"same\"}");
        pack(dir, "y", "{\"id\":\"same\"}");
        assertTrue(problems(host(jar, dir)).contains("(remove y)"));
    }

    @Test
    void failedScanKeepsThePreviousPacks() throws IOException {
        Path dir = tmp.resolve("packs");
        pack(dir, "a", "{\"id\":\"a\"}");
        PackHost h = host(null, dir);
        h.rescan();
        pack(dir, "b", "{\"id\":\"a\"}");
        assertThrows(PackException.class, h::rescan);
        assertEquals(List.of("a"), h.packs().stream().map(Pack::id).toList());
        pack(dir, "c", "{\"id\":\"c\"}");
        Files.delete(dir.resolve("b/pack.mcmeta"));
        Files.delete(dir.resolve("b"));
        assertThrows(IllegalStateException.class, () -> h.scan(p -> {
            throw new IllegalStateException("reader");
        }));
        assertEquals(1, h.packs().size());
    }

    @Test
    void hashIsFramedSortedAndSelective() throws IOException {
        Path dir = tmp.resolve("packs");
        Path p = pack(dir, "a", "{\"id\":\"a\"}");
        write(p.resolve("fakemod/ns/guns/z.json"), "{}");
        write(p.resolve("fakemod/ns/guns/a.json"), "{\"k\":1}");
        write(p.resolve("assets/ignored.png"), "png");
        PackHost h = host(null, dir);
        h.rescan();
        PackHash manual = new PackHash();
        manual.update("fakemod/ns/guns/a.json", "{\"k\":1}".getBytes(StandardCharsets.UTF_8));
        manual.update("fakemod/ns/guns/z.json", "{}".getBytes(StandardCharsets.UTF_8));
        String hash = h.packs().getFirst().sha256();
        assertEquals(manual.hex(), hash);
        write(p.resolve("assets/ignored.png"), "other");
        h.rescan();
        assertEquals(hash, h.packs().getFirst().sha256());
        write(p.resolve("fakemod/ns/guns/z.json"), "{ }");
        h.rescan();
        assertNotEquals(hash, h.packs().getFirst().sha256());
    }

    @Test
    void defsAndAssets() throws IOException {
        Path dir = tmp.resolve("packs");
        Path p = pack(dir, "a", "{\"id\":\"a\"}");
        write(p.resolve("fakemod/ns/guns/sub/x.json"), "{\"v\":1}");
        write(p.resolve("assets/ns/models/m.bin"), "bin");
        PackHost h = host(null, dir);
        h.rescan();
        Map<ResourceLocation, PackHost.Def> defs = h.defs("guns");
        JsonObject json = defs.get(ResourceLocation.fromNamespaceAndPath("ns", "sub/x")).json();
        assertEquals(1, json.get("v").getAsInt());
        assertTrue(h.defs("none").isEmpty());
        assertEquals("bin", new String(h.asset(ResourceLocation.fromNamespaceAndPath("ns", "models/m.bin")).orElseThrow()));
        assertTrue(h.asset(ResourceLocation.fromNamespaceAndPath("ns", "nope")).isEmpty());
        pack(dir, "b", "{\"id\":\"b\"}");
        write(dir.resolve("b/fakemod/ns/guns/sub/x.json"), "{}");
        h.rescan();
        assertThrows(PackException.class, () -> h.defs("guns"));
    }

    @Test
    void archivesCloseUnlessHeld() throws IOException {
        Path dir = tmp.resolve("packs");
        Files.createDirectories(dir);
        try (OutputStream out = Files.newOutputStream(dir.resolve("a.zip")); ZipOutputStream z = new ZipOutputStream(out)) {
            z.putNextEntry(new ZipEntry("pack.mcmeta"));
            z.write(mcmeta("{\"id\":\"a\"}", "34").getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        PackHost closed = host(null, dir);
        closed.rescan();
        assertThrows(java.nio.file.ClosedFileSystemException.class, () -> Files.exists(closed.packs().getFirst().root()
                .resolve("pack.mcmeta")) );
        PackSpec hold = new PackSpec("fakemod", "Fake", "fakemod_packs", "fakemod", "fakemod", java.util.Set.of("tab"),
                java.util.OptionalInt.of(34), s -> false, true, false, false, false);
        PackHost held = PackHost.detached(hold, List.of(), dir);
        held.rescan();
        assertTrue(Files.exists(held.packs().getFirst().root().resolve("pack.mcmeta")));
        held.rescan();
        assertTrue(Files.exists(held.packs().getFirst().root().resolve("pack.mcmeta")));
    }
}
