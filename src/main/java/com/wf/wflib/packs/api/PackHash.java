package com.wf.wflib.packs.api;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Framing {@code name \0 length(8, big-endian) bytes}; names '/'-separated, sorted: native '\' > '0' => OS-dependent hash. */
final class PackHash {
    private final MessageDigest digest;

    PackHash() {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    void update(String name, byte[] bytes) {
        digest.update(name.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(ByteBuffer.allocate(8).putLong(bytes.length).flip());
        digest.update(bytes);
    }

    String hex() {
        return HexFormat.of().formatHex(digest.digest());
    }

    static String walk(Path root, Predicate<String> hashed) throws IOException {
        TreeMap<String, Path> files = new TreeMap<>();
        try (Stream<Path> s = Files.walk(root)) {
            s.filter(Files::isRegularFile).forEach(f -> {
                String name = root.relativize(f).toString().replace('\\', '/');
                if (hashed.test(name)) {
                    files.put(name, f);
                }
            });
        }
        PackHash h = new PackHash();
        for (var e : files.entrySet()) {
            h.update(e.getKey(), Files.readAllBytes(e.getValue()));
        }
        return h.hex();
    }
}
