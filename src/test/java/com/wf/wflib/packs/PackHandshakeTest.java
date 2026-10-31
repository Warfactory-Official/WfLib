package com.wf.wflib.packs;

import com.wf.wflib.packs.api.PackEntry;
import com.wf.wflib.packs.api.PackHost;
import com.wf.wflib.packs.api.PackSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackHandshakeTest {
    static final PackSpec SPEC = PackSpec.builder("fakemod", "Fake").build();
    static final PackEntry A = new PackEntry("a", "11", ""), B = new PackEntry("b", "22", "");

    private static String mismatch(List<PackEntry> server, List<PackEntry> client) {
        return PackHost.mismatch(SPEC, server, client);
    }

    @Test
    void sameSetsPassInAnyOrder() {
        assertNull(mismatch(List.of(A, B), List.of(B, A)));
        assertNull(mismatch(List.of(), List.of()));
    }

    @Test
    void duplicateClientIdIsAMismatch() {
        PackEntry c = new PackEntry("c", "1", "");
        String m = mismatch(List.of(A), List.of(A, A, c, c));
        assertNotNull(m);
        assertTrue(m.contains("Different contents: a"), m);
        assertTrue(m.contains("Not on the server: c\n"), m);
    }

    @Test
    void namesEveryDifference() {
        String m = mismatch(List.of(A, B), List.of(new PackEntry("b", "99", ""), new PackEntry("c", "33", "")));
        assertNotNull(m);
        assertTrue(m.startsWith("Fake packs differ from the server."), m);
        assertTrue(m.contains("Missing: a"), m);
        assertTrue(m.contains("Different contents: b"), m);
        assertTrue(m.contains("Not on the server: c"), m);
        assertTrue(m.contains("fakemod/packs"), m);
        assertFalse(m.contains("mod jars"), m);
    }

    @Test
    void jarPackDifferenceNamesTheMod() {
        PackEntry jar = new PackEntry("jar", "11", "addon");
        String m = mismatch(List.of(jar), List.of(new PackEntry("jar", "12", "addon")));
        assertNotNull(m);
        assertTrue(m.contains("From mod jars: jar (mod addon)."), m);
        assertFalse(m.contains("fakemod/packs"), m);
        m = mismatch(List.of(jar, A), List.of());
        assertTrue(m.contains("Missing: jar, a") && m.contains("From mod jars: jar (mod addon)."), m);
        assertTrue(m.contains("fakemod/packs"), m);
    }

    @Test
    void installDirFollowsTheSpec() {
        PackSpec other = PackSpec.builder("fakemod", "Other").gameDirName("elsewhere").build();
        String m = PackHost.mismatch(other, List.of(A), List.of());
        assertTrue(m.startsWith("Other packs differ") && m.contains("<game dir>/elsewhere/packs."), m);
    }
}
