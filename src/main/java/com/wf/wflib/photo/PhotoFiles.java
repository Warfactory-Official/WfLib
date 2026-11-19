package com.wf.wflib.photo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/** {@code <world>/wflib_photos/<uuid>.jpg}. File names come only from a {@link UUID}, so no path ever leaves the folder. */
public final class PhotoFiles {
    static final String DIR = "wflib_photos";

    private PhotoFiles() {
    }

    static Path root(MinecraftServer server) {
        return server.getWorldPath(new LevelResource(DIR)).toAbsolutePath().normalize();
    }

    static Path path(Path root, UUID id) {
        return root.resolve(id + ".jpg");
    }

    /** Blocking; call off the server thread. */
    static void write(Path root, UUID id, byte[] bytes) throws IOException {
        Files.createDirectories(root);
        Path target = path(root, id);
        Path temp = root.resolve(id + ".tmp");
        Files.write(temp, bytes);
        Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /** Blocking; null when missing or oversized. The bytes are re-checked as a JPEG before they are served. */
    @Nullable
    static byte[] read(Path root, UUID id) {
        Path file = path(root, id);
        try {
            if (!Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                    || Files.size(file) > PhotoLimits.MAX_BYTES) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(file);
            return Jpeg.size(bytes) == null ? null : bytes;
        } catch (IOException e) {
            return null;
        }
    }

    static void delete(Path root, UUID id) {
        try {
            Files.deleteIfExists(path(root, id));
        } catch (IOException ignored) {
            // shortcut: an undeletable file stays orphaned on disk; it is no longer indexed, so it is never served
        }
    }
}
