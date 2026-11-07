package com.wf.wflib.labs;

import com.wf.wflib.labs.api.LabTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LabWriterTest {
    private static final LabTable TABLE = LabTable.of("t").dimStr("a").dimNum("b", "m").measure("c", "J").build();

    @Test
    void rowsAreJsonArraysInColumnOrder(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("t.ndjson.gz");
        try (LabWriter w = new LabWriter(TABLE, file)) {
            w.row("x\"y", 1.5, 3);
            w.row(null, Double.NaN, true);
            assertEquals(2, w.rows());
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(
                Files.newInputStream(file))))) {
            assertEquals(List.of("[\"x\\\"y\",1.5,3]", "[null,null,1]"), r.lines().toList());
        }
    }

    @Test
    void wrongArityAndKindAreRejected(@TempDir Path dir) throws Exception {
        try (LabWriter w = new LabWriter(TABLE, dir.resolve("t.ndjson.gz"))) {
            assertThrows(IllegalArgumentException.class, () -> w.row("a", 1.0));
            assertThrows(IllegalArgumentException.class, () -> w.row(1, 1.0, 1.0));
            assertThrows(IllegalArgumentException.class, () -> w.row("a", "b", 1.0));
        }
    }
}
