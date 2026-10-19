package com.wf.wflib.pda.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FastfetchTest {
    @Test
    void escapeSequencesBlankLinesAndTabsAreCleanedAndLongLinesCut() {
        List<String> out = Fastfetch.clean(List.of("\u001B[1;32muser\u001B[0m@host", "   ", "OS:\tArch", "x".repeat(300)));
        assertEquals(3, out.size());
        assertEquals("user@host", out.get(0));
        assertEquals("OS: Arch", out.get(1));
        assertEquals(160, out.get(2).length());
    }

    @Test
    void outputIsCappedAtSixtyLines() {
        List<String> raw = new java.util.ArrayList<>();
        for (int i = 0; i < 200; i++) {
            raw.add("line " + i);
        }
        assertEquals(60, Fastfetch.clean(raw).size());
    }

    @Test
    void sizesAndUptimesReadWell() {
        assertEquals("512 KiB", Fastfetch.size(512 * 1024));
        assertEquals("64 MiB", Fastfetch.size(64L * 1024 * 1024));
        assertEquals("15.5 GiB", Fastfetch.size((long) (15.5 * 1024 * 1024 * 1024)));
        assertEquals("2h 5m", Fastfetch.uptime((2 * 60 + 5) * 60_000L));
        assertEquals("3m 7s", Fastfetch.uptime((3 * 60 + 7) * 1000L));
    }
}
