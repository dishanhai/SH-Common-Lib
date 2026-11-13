// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.ulk;

import java.nio.file.Files;
import java.util.List;

public final class SearchHistoryTest {
    public static void main(String[] args) throws Exception {
        var dir = Files.createTempDirectory("ulk-search-history-");
        var file = dir.resolve("history.properties");
        try {
            var history = SearchHistory.load(file, 2, 32);
            history.record(" iron ");
            history.record("gold");
            history.record("iron");
            require(history.toggleFavorite("iron"), "favorite added");
            history.save();
            var restored = SearchHistory.load(file, 2, 32);
            require(restored.entries().equals(List.of("iron", "gold")), "deduplicate and persist");
            require(restored.favorites().equals(List.of("iron")) && restored.count("iron") == 2, "favorite and counts persist");
            restored.record("gold");
            restored.record("gold");
            restored.record("iron");
            require(restored.frequent().equals(List.of("iron", "gold")), "ties keep recent order");
            restored.record("gold");
            require(restored.frequent().equals(List.of("gold", "iron")), "frequency orders queries");
            require(restored.remove("gold") && restored.count("gold") == 0, "remove history and count");
            require(restored.undoRemove() && restored.count("gold") == 4 && restored.entries().get(0).equals("gold"), "undo restores count and position");
            require(!restored.undoRemove(), "removed history restored once");
            require(restored.removeFavorite("iron") && restored.undoRemove() && restored.isFavorite("iron"), "favorite deletion can be undone");
            require(restored.remove("iron"), "remove before new search");
            restored.record("iron");
            require(!restored.undoRemove() && restored.count("iron") == 1, "new search invalidates stale removal");
            require(!restored.toggleFavorite("iron") && restored.favorites().isEmpty(), "favorite removed");
            history.record("copper");
            require(history.entries().equals(List.of("copper", "iron")), "limit history");
            history.clear();
            history.save();
            var cleared = SearchHistory.load(file, 2, 32);
            require(cleared.entries().isEmpty() && cleared.favorites().equals(List.of("iron")), "clear preserves favorites");
            history.record("钻石");
            history.save();
            require(SearchHistory.load(file, 2, 32).entries().equals(List.of("钻石")), "Unicode persists");
            Files.writeString(file, "0=legacy\n");
            var legacy = SearchHistory.load(file, 2, 32);
            require(legacy.entries().equals(List.of("legacy")) && legacy.count("legacy") == 1, "old history remains readable");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
