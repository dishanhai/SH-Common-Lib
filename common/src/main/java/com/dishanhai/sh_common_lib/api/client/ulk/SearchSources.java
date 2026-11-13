// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.ulk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/** Session-scoped, bounded candidate sources. The host supplies visible names and quest data. */
public final class SearchSources {
    private final int capacity;
    private final Random random;
    private record Name(String value, String normalized) {}
    private List<String> available = List.of();
    private Map<Character, List<Name>> namesByFirst = Map.of();
    private List<String> randomItems = List.of();
    private List<String> questItems = List.of();
    private final ArrayList<String> chatItems = new ArrayList<>();
    private Object session;

    public SearchSources(int capacity, Random random) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
        this.random = Objects.requireNonNull(random);
    }

    /** Changes of connection or world clear data derived from its messages and tasks. */
    public void session(Object next) {
        if (session != next) { session = next; chatItems.clear(); questItems = List.of(); }
    }

    public void replaceAvailable(List<String> names) {
        available = clean(names, Integer.MAX_VALUE);
        var index = new HashMap<Character, List<Name>>();
        for (String name : available) {
            if (name.codePointCount(0, name.length()) < 2) continue;
            String normalized = name.toLowerCase(Locale.ROOT);
            index.computeIfAbsent(normalized.charAt(0), ignored -> new ArrayList<>()).add(new Name(name, normalized));
        }
        namesByFirst = index;
        var shuffled = new ArrayList<>(available);
        Collections.shuffle(shuffled, random);
        randomItems = List.copyOf(shuffled.subList(0, Math.min(capacity, shuffled.size())));
    }

    public void replaceQuest(List<String> names) { questItems = clean(names, capacity); }
    public List<String> random() { return randomItems; }
    public List<String> quest() { return questItems; }
    public List<String> chat() { return List.copyOf(chatItems); }
    public void clearChat() { chatItems.clear(); }

    /** Record only known item names in a player message; message contents are never stored. */
    public void acceptChat(String message) {
        if (session == null || message == null || message.isBlank() || message.length() > 512) return;
        String text = message.toLowerCase(Locale.ROOT);
        int added = 0;
        var checked = new HashSet<Character>();
        for (int i = 0; i < text.length(); i++) {
            if (!checked.add(text.charAt(i))) continue;
            for (Name name : namesByFirst.getOrDefault(text.charAt(i), List.of())) {
                int start = text.indexOf(name.normalized());
                while (start >= 0 && !boundary(text, name.normalized(), start))
                    start = text.indexOf(name.normalized(), start + 1);
                if (start < 0) continue;
                chatItems.remove(name.value());
                chatItems.add(0, name.value());
                if (chatItems.size() > capacity) chatItems.remove(chatItems.size() - 1);
                if (++added >= 16) return;
            }
        }
    }

    private static boolean boundary(String text, String word, int start) {
        int end = start + word.length();
        return !(start > 0 && asciiWord(word.charAt(0)) && asciiWord(text.charAt(start - 1)))
                && !(end < text.length() && asciiWord(word.charAt(word.length() - 1)) && asciiWord(text.charAt(end)));
    }
    private static boolean asciiWord(char c) { return c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_'; }

    private static List<String> clean(List<String> names, int limit) {
        var unique = new LinkedHashSet<String>();
        for (String name : names) {
            if (name == null) continue;
            String value = name.strip();
            if (!value.isEmpty() && value.length() <= 256) unique.add(value);
            if (unique.size() >= limit) break;
        }
        return List.copyOf(unique);
    }
}
