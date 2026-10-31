package com.wf.wflib.packs.api;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;

/**
 * One dependent's pack format. On-disk contract: jar dir {@code jarDir} in a mod file; {@code <gamedir>/<gameDirName>/packs};
 * {@code pack.mcmeta} section {@code manifestKey} (keys {@code id}, {@code requires}, + {@code keys}); defs under
 * {@code <manifestKey>/<ns>/<type>/<path>.json}.
 *
 * @param displayName   user-facing name in disconnect and error text
 * @param keys          allowed manifest section keys besides {@code id} and {@code requires}
 * @param packFormat    required {@code pack.pack_format}; empty = unchecked
 * @param hashed        pack-root-relative {@code /} path -> in the handshake hash
 * @param holdArchives  zip file systems stay open until the next successful scan; else closed after it and a zip pack's
 *                      files are readable only inside the {@link PackHost#scan} reader
 * @param createGameDir create {@code <gamedir>/<gameDirName>/packs} on scan
 */
public record PackSpec(String modId, String displayName, String jarDir, String gameDirName, String manifestKey,
                       Set<String> keys, OptionalInt packFormat, Predicate<String> hashed, boolean holdArchives,
                       boolean createGameDir, boolean clientResources, boolean serverData) {
    public PackSpec {
        Objects.requireNonNull(modId);
        Objects.requireNonNull(displayName);
        Objects.requireNonNull(jarDir);
        Objects.requireNonNull(gameDirName);
        Objects.requireNonNull(manifestKey);
        Objects.requireNonNull(hashed);
        keys = Set.copyOf(keys);
        if (keys.contains("id") || keys.contains("requires")) {
            throw new IllegalArgumentException("id and requires are always allowed");
        }
    }

    public static Builder builder(String modId, String displayName) {
        return new Builder(modId, displayName);
    }

    /** Defaults: jarDir {@code <modId>_packs}, gameDirName and manifestKey = modId, nothing hashed, no offering. */
    public static final class Builder {
        private final String modId;
        private final String displayName;
        private String jarDir;
        private String gameDirName;
        private String manifestKey;
        private Set<String> keys = Set.of();
        private OptionalInt packFormat = OptionalInt.empty();
        private Predicate<String> hashed = path -> false;
        private boolean holdArchives;
        private boolean createGameDir;
        private boolean clientResources;
        private boolean serverData;

        private Builder(String modId, String displayName) {
            this.modId = modId;
            this.displayName = displayName;
            this.jarDir = modId + "_packs";
            this.gameDirName = modId;
            this.manifestKey = modId;
        }

        public Builder jarDir(String v) {
            jarDir = v;
            return this;
        }

        public Builder gameDirName(String v) {
            gameDirName = v;
            return this;
        }

        public Builder manifestKey(String v) {
            manifestKey = v;
            return this;
        }

        public Builder keys(String... v) {
            keys = Set.of(v);
            return this;
        }

        public Builder packFormat(int v) {
            packFormat = OptionalInt.of(v);
            return this;
        }

        public Builder hashed(Predicate<String> v) {
            hashed = v;
            return this;
        }

        public Builder holdArchives() {
            holdArchives = true;
            return this;
        }

        public Builder createGameDir() {
            createGameDir = true;
            return this;
        }

        /** Offer every pack as an always-on client resource pack. */
        public Builder clientResources() {
            clientResources = true;
            return this;
        }

        /** Offer every pack as an always-on server data pack. */
        public Builder serverData() {
            serverData = true;
            return this;
        }

        public PackSpec build() {
            return new PackSpec(modId, displayName, jarDir, gameDirName, manifestKey, keys, packFormat, hashed,
                    holdArchives, createGameDir, clientResources, serverData);
        }
    }
}
