// Author: dishanhai
package com.dishanhai.sh_common_lib.client.jei;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Persistent provider choice. A failed external update never enables the SHCLIB overlay. */
final class JeiSearchProviderState {
    enum Choice { SHCLIB, JEIGUESS }
    interface External {
        boolean enabled() throws Exception;
        void setEnabled(boolean enabled) throws Exception;
    }
    private final Path file;
    private final boolean installed;
    private final External external;
    private Choice choice;
    private boolean verified;

    JeiSearchProviderState(Path file, boolean installed, External external) throws IOException {
        this.file = file;
        this.installed = installed;
        this.external = external;
        var values = new Properties();
        try (var in = Files.newInputStream(file)) { values.load(in); }
        catch (NoSuchFileException missing) { return; }
        catch (IllegalArgumentException malformed) { throw new IOException("Invalid provider settings", malformed); }
        try { choice = Choice.valueOf(values.getProperty("provider", "")); }
        catch (IllegalArgumentException invalid) { choice = null; }
    }

    boolean installed() { return installed; }
    boolean needsChoice() { return installed && choice == null; }
    boolean useShclib() { return !installed || choice == Choice.SHCLIB && verified; }
    Choice choice() { return choice; }

    boolean reconcile() {
        if (!installed || choice == null) return false;
        try {
            boolean desired = choice == Choice.JEIGUESS;
            if (external.enabled() != desired) external.setEnabled(desired);
            verified = external.enabled() == desired;
        } catch (Exception | LinkageError failure) { verified = false; }
        return verified;
    }

    boolean choose(Choice next) {
        if (!installed || next == null) return false;
        Boolean previous = null;
        try {
            previous = external.enabled();
            boolean desired = next == Choice.JEIGUESS;
            if (previous != desired) external.setEnabled(desired);
            if (external.enabled() != desired) throw new IOException("External setting did not change");
            write(next);
            choice = next;
            verified = true;
            return true;
        } catch (Exception | LinkageError failure) {
            verified = false;
            if (previous != null) {
                try { if (external.enabled() != previous) external.setEnabled(previous); }
                catch (Exception | LinkageError ignored) { /* Keep the overlay disabled when rollback fails. */ }
            }
            return false;
        }
    }

    private void write(Choice value) throws IOException {
        Path absolute = file.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Path temporary = Files.createTempFile(absolute.getParent(), "jei-provider-", ".tmp");
        try {
            var properties = new Properties();
            properties.setProperty("provider", value.name());
            try (var out = Files.newOutputStream(temporary)) { properties.store(out, null); }
            try { Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }
}
