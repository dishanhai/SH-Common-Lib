package com.dishanhai.sh_common_lib.api.ulk.storage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Author: dishanhai. Dependency-free contract test, runnable with java -ea. */
public final class CheckedFileStoreTest {
    public static void main(String[] args) throws Exception {
        Class<?> api;
        try {
            api = Class.forName("com.dishanhai.sh_common_lib.api.ulk.storage.CheckedFileStore");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("CheckedFileStore must provide verified backup and replacement", missing);
        }
        Path dir = Files.createTempDirectory("ulk-checked-file-test-");
        try {
            Path target = dir.resolve("world.dat");
            Path backups = dir.resolve("backups");
            byte[] original = "old world".getBytes(StandardCharsets.UTF_8);
            byte[] replacement = "new world".getBytes(StandardCharsets.UTF_8);
            Files.write(target, original);
            Object result = api.getMethod("write", Path.class, byte[].class, Path.class, String.class)
                    .invoke(null, target, replacement, backups, "test");
            check((boolean) result.getClass().getMethod("ok").invoke(result), "replacement must succeed");
            Path backup = (Path) result.getClass().getMethod("backupPath").invoke(result);
            check(java.util.Arrays.equals(original, Files.readAllBytes(backup)), "backup preserves old bytes");
            check(java.util.Arrays.equals(replacement, Files.readAllBytes(target)), "target contains new bytes");
            check((boolean) result.getClass().getMethod("tmpVanished").invoke(result), "no stale temporary file");
            check((boolean) api.getMethod("restore", Path.class, Path.class).invoke(null, backup, target), "restore succeeds");
            check(java.util.Arrays.equals(original, Files.readAllBytes(target)), "restored bytes match");
            Object failed = api.getMethod("write", Path.class, byte[].class, Path.class, String.class)
                    .invoke(null, target, replacement, target, "bad-backup-directory");
            check(!(boolean) failed.getClass().getMethod("ok").invoke(failed), "failed backup refuses write");
            check(java.util.Arrays.equals(original, Files.readAllBytes(target)), "failed write leaves original intact");
            Object first = api.getMethod("write", Path.class, byte[].class, Path.class, String.class)
                    .invoke(null, dir.resolve("new.dat"), replacement, backups, "new");
            check((boolean) first.getClass().getMethod("ok").invoke(first), "new file can be created");
            check(first.getClass().getMethod("backupPath").invoke(first) == null, "new file has no fake backup");
            check(!(boolean) api.getMethod("restore", Path.class, Path.class)
                    .invoke(null, dir.resolve("missing.dat"), target), "missing backup is refused");
            System.out.println("CheckedFileStore: 11 checks passed");
        } finally {
            try (var paths = Files.walk(dir)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
