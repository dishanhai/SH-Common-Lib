// Author: dishanhai
package com.dishanhai.sh_common_lib.client.jei;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import com.dishanhai.sh_common_lib.api.client.ulk.SearchHistory;
import com.dishanhai.sh_common_lib.api.client.ulk.SearchAliases;

/** Settings are loaded on client initialization and updated only after a successful save. */
public final class JeiSearchPreferences {
    private static final Logger LOGGER = LoggerFactory.getLogger("sh_common_lib:jei_search");
    private static Path file;
    private static JeiSearchSettings current = JeiSearchSettings.DEFAULT;
    private static SearchHistory history;
    private static boolean historyLoaded;
    private static Map<String, List<String>> aliases = Map.of();

    private JeiSearchPreferences() {}

    public static void initialize(Path directory) {
        file = directory.resolve("jei-search.properties");
        history = null;
        historyLoaded = false;
        try { aliases = SearchAliases.load(directory.resolve("jei-search-aliases.json")); }
        catch (IOException failure) {
            LOGGER.warn("Could not load JEI search aliases", failure);
            aliases = Map.of();
        }
        try {
            current = JeiSearchSettings.read(file);
        } catch (IOException failure) {
            LOGGER.warn("Could not read JEI search settings", failure);
            current = JeiSearchSettings.DEFAULT;
        }
    }

    public static JeiSearchSettings current() { return current; }
    public static Path directory() { return file == null ? null : file.getParent(); }
    public static List<String> aliases(String id, List<String> existing) {
        return SearchAliases.merge(existing, aliases, id);
    }

    public static SearchHistory history() {
        if (historyLoaded || file == null) return history;
        historyLoaded = true;
        try {
            history = SearchHistory.load(directory().resolve("jei-search-history.properties"), 40, 256);
        } catch (IOException failure) {
            LOGGER.warn("Could not load JEI search history", failure);
        }
        return history;
    }

    /** Keep the in-memory history intact if clearing the on-disk copy fails. */
    public static boolean clearHistory() {
        var entries = history();
        if (entries == null) return false;
        try {
            entries.clearAndSave();
            return true;
        } catch (IOException failure) {
            LOGGER.warn("Could not clear JEI search history", failure);
            return false;
        }
    }

    public static boolean save(JeiSearchSettings replacement) {
        Objects.requireNonNull(replacement);
        if (file == null) return false;
        try {
            replacement.write(file);
            current = replacement;
            return true;
        } catch (IOException failure) {
            LOGGER.warn("Could not save JEI search settings", failure);
            return false;
        }
    }
}
