package com.wf.wflib.packs.api;

import com.google.gson.JsonObject;

import java.nio.file.Path;
import java.util.List;

/**
 * Installed pack. {@code root}: dir, or a zip's root (valid after the scan only if the host holds archives).
 * {@code mod}: providing mod id for {@link Origin#JAR}, else "". {@code sha256}: hex over {@link PackSpec#hashed()}
 * files, sorted by root-relative {@code /} path, each {@code path \0 length bytes}. {@code manifest}: the host's
 * section of {@code pack.mcmeta}.
 */
public record Pack(String id, String description, List<String> requires, Path source, Path root, Origin origin,
                   String mod, String sha256, JsonObject manifest) {

    /** JAR: {@code <jarDir>/<dir>} in a mod file; ARCHIVE: {@code packs/<name>.zip}; DIRECTORY: {@code packs/<dir>}. */
    public enum Origin {
        JAR, ARCHIVE, DIRECTORY
    }
}
