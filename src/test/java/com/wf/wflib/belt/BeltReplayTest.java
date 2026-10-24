package com.wf.wflib.belt;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * In-game belt captures ({@code /ma belt record}, {@code /ma belt dump}) replayed through {@link BeltChain}: {@code
 * -PbeltReplay=<file>} prints every erratic frame; {@code src/test/resources/belt/*.jsonl} = regressions, none erratic.
 */
class BeltReplayTest {
    private static List<BeltProbe.Frame> erratic(Path file) throws IOException {
        try (BufferedReader r = Files.newBufferedReader(file)) {
            return BeltProbe.replay(r).stream().filter(BeltProbe.Frame::erratic).toList();
        }
    }

    @Test
    void replayCapture() throws IOException {
        String file = System.getProperty("belt.replay", "");
        Assumptions.assumeFalse(file.isEmpty(), "-PbeltReplay=<capture.jsonl>");
        List<BeltProbe.Frame> bad = erratic(Path.of(file));
        System.out.println("BELT_REPLAY " + file + ": " + bad.size() + " erratic frames");
        bad.forEach(f -> System.out.println("BELT_REPLAY " + f.summary()));
    }

    @TestFactory
    Stream<DynamicTest> regressions() throws IOException, URISyntaxException {
        Path dir = Path.of(Objects.requireNonNull(getClass().getResource("/belt")).toURI());
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.toString().endsWith(".jsonl")).sorted().toList().stream()
                    .map(p -> DynamicTest.dynamicTest(p.getFileName().toString(), () -> assertEquals("",
                            erratic(p).stream().limit(8).map(BeltProbe.Frame::summary)
                                    .collect(Collectors.joining("\n")))));
        }
    }
}
