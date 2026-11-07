package com.wf.wflib.labs;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** Commit + dirty flag of a checkout; null fields when git is absent or the path is no checkout. */
final class GitInfo {
    private GitInfo() {
    }

    static JsonObject of(Path dir) {
        JsonObject o = new JsonObject();
        String commit = git(dir, "rev-parse", "HEAD");
        o.addProperty("path", dir.toString());
        o.addProperty("commit", commit);
        String status = commit == null ? null : git(dir, "status", "--porcelain");
        if (status == null) {
            o.add("dirty", null);
        } else {
            o.addProperty("dirty", !status.isEmpty());
        }
        return o;
    }

    /** Nearest ancestor of {@code from} holding {@code .git}; null if none. */
    static Path root(Path from) {
        for (Path p = from.toAbsolutePath().normalize(); p != null; p = p.getParent()) {
            if (Files.exists(p.resolve(".git"))) {
                return p;
            }
        }
        return null;
    }

    private static String git(Path dir, String... args) {
        String[] cmd = new String[args.length + 3];
        cmd[0] = "git";
        cmd[1] = "-C";
        cmd[2] = dir.toString();
        System.arraycopy(args, 0, cmd, 3, args.length);
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            byte[] out = p.getInputStream().readAllBytes();
            if (!p.waitFor(20, TimeUnit.SECONDS) || p.exitValue() != 0) {
                return null;
            }
            return new String(out, java.nio.charset.StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
