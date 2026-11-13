// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.ulk;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/** Local, bounded query history; callers decide when a search is committed and saved. */
public final class SearchHistory {
    public enum Source { RECENT, FAVORITES, FREQUENT }
    private final Path file;
    private final int capacity;
    private final int maxLength;
    private final List<String> entries = new ArrayList<>();
    private final List<String> favorites = new ArrayList<>();
    private final Map<String, Integer> counts = new HashMap<>();
    private String removed;
    private int removedIndex;
    private int removedCount;
    private boolean removedFavorite;
    private long revision;

    private SearchHistory(Path file, int capacity, int maxLength) {
        this.file = Objects.requireNonNull(file).toAbsolutePath();
        if (capacity < 1 || maxLength < 1) throw new IllegalArgumentException("Invalid history limits");
        this.capacity = capacity;
        this.maxLength = maxLength;
    }

    public static SearchHistory load(Path file, int capacity, int maxLength) throws IOException {
        var result = new SearchHistory(file, capacity, maxLength);
        if (!Files.exists(result.file)) return result;
        var properties = new Properties();
        try (InputStream input = Files.newInputStream(result.file)) {
            properties.load(input);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid search history", invalid);
        }
        for (int i = capacity - 1; i >= 0; i--) {
            String query = properties.getProperty(Integer.toString(i));
            if (query != null) {
                String text = result.valid(query);
                if (text != null && !result.entries.contains(text)) {
                    result.entries.add(0, text);
                    result.counts.put(text, positiveInt(properties.getProperty("count." + i)));
                }
            }
        }
        for (int i = 0; i < capacity; i++) {
            String text = result.valid(properties.getProperty("favorite." + i));
            if (text != null && !result.favorites.contains(text)) result.favorites.add(text);
        }
        return result;
    }

    public List<String> entries() { return List.copyOf(entries); }
    public List<String> favorites() { return List.copyOf(favorites); }
    public List<String> browse(Source source) {
        return switch (Objects.requireNonNull(source)) {
            case RECENT -> entries();
            case FAVORITES -> favorites();
            case FREQUENT -> frequent();
        };
    }
    /** Most frequently recorded queries, preserving recency order when counts are equal. */
    public List<String> frequent() {
        return entries.stream().sorted((a, b) -> Integer.compare(counts.getOrDefault(b, 0), counts.getOrDefault(a, 0))).toList();
    }
    public int count(String query) { return counts.getOrDefault(query, 0); }
    public long revision() { return revision; }
    public int recency(String query) {
        int index = entries.indexOf(query);
        return index < 0 ? Integer.MAX_VALUE : index;
    }
    public boolean isFavorite(String query) { return favorites.contains(query); }

    public boolean toggleFavorite(String query) {
        String text = valid(query);
        if (text == null) return false;
        if (removedFavorite && text.equals(removed)) removed = null;
        if (favorites.remove(text)) { revision++; return false; }
        favorites.add(0, text);
        if (favorites.size() > capacity) favorites.remove(favorites.size() - 1);
        revision++;
        return true;
    }

    public boolean remove(String query) {
        int index = entries.indexOf(query);
        if (index < 0) return false;
        removed = query;
        removedIndex = index;
        removedCount = counts.getOrDefault(query, 1);
        removedFavorite = false;
        entries.remove(index);
        counts.remove(query);
        revision++;
        return true;
    }

    public boolean undoRemove() {
        if (removed == null) return false;
        if (removedFavorite) {
            favorites.remove(removed);
            favorites.add(Math.min(removedIndex, favorites.size()), removed);
            if (favorites.size() > capacity) favorites.remove(favorites.size() - 1);
            removed = null;
            revision++;
            return true;
        }
        entries.remove(removed);
        entries.add(Math.min(removedIndex, entries.size()), removed);
        counts.put(removed, removedCount);
        if (entries.size() > capacity) counts.remove(entries.remove(entries.size() - 1));
        removed = null;
        revision++;
        return true;
    }

    public boolean removeFavorite(String query) {
        int index = favorites.indexOf(query);
        if (index < 0) return false;
        removed = query;
        removedIndex = index;
        removedFavorite = true;
        favorites.remove(index);
        revision++;
        return true;
    }

    public void record(String query) {
        String text = valid(Objects.requireNonNull(query));
        if (text == null) return;
        if (!removedFavorite && text.equals(removed)) removed = null;
        entries.remove(text);
        entries.add(0, text);
        counts.compute(text, (key, count) -> count == null ? 1 : count == Integer.MAX_VALUE ? count : count + 1);
        if (entries.size() > capacity) counts.remove(entries.remove(entries.size() - 1));
        revision++;
    }

    public void clear() { entries.clear(); counts.clear(); removed = null; revision++; }

    /** Clear recent usage without losing the in-memory state when persistence fails. */
    public void clearAndSave() throws IOException {
        var previous = List.copyOf(entries);
        var previousCounts = Map.copyOf(counts);
        String previousRemoved = removed;
        int previousRemovedIndex = removedIndex;
        int previousRemovedCount = removedCount;
        boolean previousRemovedFavorite = removedFavorite;
        long previousRevision = revision;
        clear();
        try { save(); }
        catch (IOException error) {
            entries.addAll(previous);
            counts.putAll(previousCounts);
            removed = previousRemoved;
            removedIndex = previousRemovedIndex;
            removedCount = previousRemovedCount;
            removedFavorite = previousRemovedFavorite;
            revision = previousRevision;
            throw error;
        }
    }

    private String valid(String query) {
        if (query == null) return null;
        String text = query.strip();
        return text.isEmpty() || text.length() > maxLength ? null : text;
    }

    private static int positiveInt(String text) {
        try { return Math.max(1, Integer.parseInt(text)); }
        catch (NumberFormatException error) { return 1; }
    }

    public void save() throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "search-history-", ".tmp");
        try {
            var properties = new Properties();
            for (int i = 0; i < entries.size(); i++) {
                String entry = entries.get(i);
                properties.setProperty(Integer.toString(i), entry);
                properties.setProperty("count." + i, Integer.toString(counts.getOrDefault(entry, 1)));
            }
            for (int i = 0; i < favorites.size(); i++) properties.setProperty("favorite." + i, favorites.get(i));
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, null);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
