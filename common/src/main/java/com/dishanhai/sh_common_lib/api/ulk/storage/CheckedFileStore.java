package com.dishanhai.sh_common_lib.api.ulk.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Author: dishanhai. Verified single-file backup/replacement for offline tools.
 * Callers must exclusively own the file while writing. This is not a transaction
 * across multiple files; callers retain the verified backup for recovery.
 */
public final class CheckedFileStore {
    private CheckedFileStore() {}

    public record Backup(Path path, String originalSha256, String backupSha256, long bytes) {}

    public record WriteResult(boolean ok, boolean tmpCreated, boolean tmpVanished,
                              boolean atomicMoveUsed, Path backupPath, String originalSha256,
                              String backupSha256, String contentSha256, String targetSha256After,
                              String error) {}

    public static String sha256(byte[] content) {
        return HexFormat.of().formatHex(digest().digest(Objects.requireNonNull(content)));
    }

    public static String sha256(Path file) throws IOException {
        requireRegularFile(file);
        MessageDigest hash = digest();
        try (InputStream input = Files.newInputStream(file)) {
            byte[] block = new byte[8192];
            for (int read; (read = input.read(block)) != -1;) hash.update(block, 0, read);
        }
        return HexFormat.of().formatHex(hash.digest());
    }

    public static Backup backup(Path target, Path backupRoot, String stamp) throws IOException {
        requireRegularFile(target);
        Files.createDirectories(backupRoot);
        String before = sha256(target);
        String label = stamp == null ? "backup" : stamp.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (label.isEmpty()) label = "backup";
        if (label.length() > 80) label = label.substring(0, 80);
        Path copy = Files.createTempFile(backupRoot, label + "-", ".bak");
        try {
            Files.copy(target, copy, StandardCopyOption.REPLACE_EXISTING);
            String copied = sha256(copy);
            if (!before.equals(copied) || !before.equals(sha256(target))) {
                throw new IOException("file changed during backup");
            }
            return new Backup(copy, before, copied, Files.size(copy));
        } catch (IOException | RuntimeException failure) {
            try { Files.deleteIfExists(copy); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public static WriteResult write(Path target, byte[] content, Path backupRoot, String stamp) {
        Path tmp = null;
        Backup backup = null;
        boolean tmpCreated = false;
        boolean atomic = false;
        String contentHash = "";
        String after = "";
        String error = "";
        boolean ok = false;
        try {
            Objects.requireNonNull(content, "content");
            target = target.toAbsolutePath().normalize();
            contentHash = sha256(content);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                backup = backup(target, backupRoot, stamp);
            }
            Path parent = target.getParent();
            Files.createDirectories(parent);
            tmp = Files.createTempFile(parent, ".ulk-write-", ".tmp");
            tmpCreated = true;
            Files.write(tmp, content);
            if (!contentHash.equals(sha256(tmp))) throw new IOException("temporary file verification failed");
            if (backup != null && !backup.originalSha256().equals(sha256(target))) {
                throw new IOException("target changed since backup");
            }
            if (backup == null && Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("target appeared during write");
            }
            atomic = replace(tmp, target);
            after = sha256(target);
            if (!contentHash.equals(after)) throw new IOException("written file verification failed");
            ok = true;
        } catch (IOException | RuntimeException failure) {
            error = failure.getClass().getSimpleName() + ": " + failure.getMessage();
        } finally {
            if (tmp != null) {
                try { Files.deleteIfExists(tmp); }
                catch (IOException cleanup) { ok = false; error += "; cleanup: " + cleanup.getMessage(); }
            }
        }
        return new WriteResult(ok, tmpCreated, tmp == null || !Files.exists(tmp), atomic,
                backup == null ? null : backup.path(), backup == null ? "" : backup.originalSha256(),
                backup == null ? "" : backup.backupSha256(), contentHash, after, error);
    }

    /** Restores verified bytes through a sibling temporary file; preserves the backup itself. */
    public static boolean restore(Path backupFile, Path target) {
        Path tmp = null;
        try {
            String expected = sha256(backupFile);
            target = target.toAbsolutePath().normalize();
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) requireRegularFile(target);
            Files.createDirectories(target.getParent());
            tmp = Files.createTempFile(target.getParent(), ".ulk-restore-", ".tmp");
            Files.copy(backupFile, tmp, StandardCopyOption.REPLACE_EXISTING);
            if (!expected.equals(sha256(tmp))) return false;
            replace(tmp, target);
            return expected.equals(sha256(target));
        } catch (IOException | RuntimeException failure) {
            return false;
        } finally {
            if (tmp != null) try { Files.deleteIfExists(tmp); } catch (IOException ignored) { }
        }
    }

    private static boolean replace(Path tmp, Path target) throws IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            return false;
        }
    }

    private static void requireRegularFile(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("not a regular file: " + path);
        }
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
