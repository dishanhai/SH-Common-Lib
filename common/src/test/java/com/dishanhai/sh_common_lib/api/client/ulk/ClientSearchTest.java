// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.ulk;

import java.util.List;
import java.util.ArrayDeque;

public final class ClientSearchTest {
    public static void main(String[] args) throws Exception {
        var sources = new SearchSources(2, new java.util.Random(1));
        sources.replaceAvailable(List.of("Iron Ingot", "钻石", "Iron Ingot", "Gold"));
        require(sources.random().size() == 2, "random source is bounded and deduplicated");
        sources.acceptChat("Iron Ingot");
        require(sources.chat().isEmpty(), "chat has no data without a session");
        Object connection = new Object();
        sources.session(connection);
        sources.acceptChat("Found iron ingot and 钻石!");
        require(sources.chat().containsAll(List.of("Iron Ingot", "钻石")), "chat records known names");
        sources.acceptChat("golden");
        require(!sources.chat().contains("Gold"), "chat respects Latin word boundaries");
        sources.acceptChat("golden gold");
        require(sources.chat().contains("Gold"), "a later whole word is still matched");
        sources.replaceQuest(List.of("钻石", "钻石", "Iron Ingot", "Gold"));
        require(sources.quest().equals(List.of("钻石", "Iron Ingot")), "quest source bounded and deduplicated");
        sources.session(connection);
        require(!sources.chat().isEmpty(), "same connection preserves session data");
        sources.session(new Object());
        require(sources.chat().isEmpty() && sources.quest().isEmpty(), "connection change clears private sources");
        var index = ClientSearch.index(List.of(
                new ClientSearch.Entry<>("iron", "Iron Ingot", List.of("minecraft:iron_ingot"), List.of(), "Minecraft", List.of("forge:ingots/iron")),
                new ClientSearch.Entry<>("gold", "Gold Ingot", List.of("minecraft:gold_ingot"), List.of(), "Minecraft", List.of("forge:ingots/gold")),
                new ClientSearch.Entry<>("diamond", "钻石", List.of("minecraft:diamond"), List.of(), "Minecraft", List.of())));
        require(index.search("iron").equals(List.of("iron")), "name search");
        require(index.search("#forge:ingots/gold").equals(List.of("gold")), "tag search");
        require(index.suggest("iro", 3).get(0).value().equals("iron"), "suggestion source");
        require(index.suggest("iro", 3).get(0).completion().equals("Iron Ingot"), "suggestion completion");
        require(index.suggest("iro", 3).get(0).matchRank() == 1, "prefix quality exposed");
        require(index.suggest("iron ingot", 3).get(0).matchRank() == 0, "exact match quality exposed");
        require(index.suggest("", 3).isEmpty(), "empty suggestion");
        require(index.suggest("zuanshi", 3).get(0).value().equals("diamond"), "pinyin suggestion");
        require(index.search("ingot", value -> value.equals("gold")).equals(List.of("gold")), "scope");
        require(SearchMatch.literal("Iron Ingot", "ing").orElseThrow().equals(new SearchMatch.Range(5, 8)), "literal match span ignores case");
        require(SearchMatch.literal("钻石", "zuanshi").isEmpty(), "phonetic match has no misleading span");
        require(SearchMatch.literal("iron", "").isEmpty(), "empty input has no match span");
        require(SearchMatch.suffix("Iro", "Iron Ingot").equals("n Ingot"), "literal prefix suffix");
        require(SearchMatch.suffix("zuanshi", "钻石").isEmpty(), "phonetic completion has no false inline suffix");
        var ranked = SearchRanking.withUsage(List.of("prefix", "exact", "prefix-used"),
                item -> item.equals("exact") ? 0 : 1,
                item -> item.equals("prefix-used") ? 99 : 0, item -> 0);
        require(ranked.equals(List.of("exact", "prefix-used", "prefix")), "usage only reorders equal quality");
        var aliasesFile = java.nio.file.Files.createTempFile("ulk-aliases", ".json");
        try {
            java.nio.file.Files.writeString(aliasesFile, "{\"minecraft:iron_ingot\":[\"铁锭\",\"fe\",\"fe\"]}");
            var aliases = SearchAliases.load(aliasesFile);
            require(SearchAliases.merge(List.of("metal"), aliases, "minecraft:iron_ingot").equals(List.of("metal", "铁锭", "fe")), "custom aliases merge and deduplicate");
            java.nio.file.Files.writeString(aliasesFile, "{\"minecraft:iron_ingot\":42}");
            try { SearchAliases.load(aliasesFile); throw new AssertionError("invalid alias JSON accepted"); }
            catch (java.io.IOException expected) { }
        } finally { java.nio.file.Files.deleteIfExists(aliasesFile); }
        var work = new ArrayDeque<Runnable>();
        var async = new AsyncClientSearch<String>(work::add);
        async.replace(List.of(new ClientSearch.Entry<>("iron", "Iron Ingot", List.of("minecraft:iron_ingot"), List.of(), "", List.of()),
                new ClientSearch.Entry<>("gold", "Gold Ingot", List.of("minecraft:gold_ingot"), List.of(), "", List.of())));
        async.request("iron", 3);
        async.request("gold", 3);
        require(work.size() == 1 && async.result().suggestions().isEmpty(), "requests coalesce and clear stale results");
        work.remove().run();
        require(async.result().query().equals("gold") && async.result().suggestions().get(0).value().equals("gold"), "latest query wins");
        async.replace(List.of());
        require(async.result().suggestions().isEmpty(), "replacement clears old results");
        work.remove().run();
        require(async.result().suggestions().isEmpty(), "replacement snapshot used");

        var correcting = new AsyncClientSearch<String>(Runnable::run, true);
        correcting.replace(List.of(
                new ClientSearch.Entry<>("iron", "Iron Ingot", List.of("minecraft:iron_ingot"), List.of(), "", List.of()),
                new ClientSearch.Entry<>("gold", "Gold Ingot", List.of("minecraft:gold_ingot"), List.of(), "", List.of())));
        correcting.request("irom", 3);
        require(correcting.result().suggestions().get(0).completion().equals("Iron Ingot"), "single typo offers a corrected prefix");
        correcting.request("iron", 3);
        require(correcting.result().suggestions().get(0).matchRank() == 1, "literal matches take priority over correction");
        correcting.request("ir", 3);
        require(correcting.result().suggestions().get(0).matchRank() == 1, "short literal matches remain available");
        correcting.request("iz", 3);
        require(correcting.result().suggestions().isEmpty(), "short typos do not create noisy corrections");
        correcting.request("@irom", 3);
        require(correcting.result().suggestions().isEmpty(), "filter syntax is never corrected as an item name");
        correcting.request("irom ingot", 3);
        require(correcting.result().suggestions().get(0).completion().equals("Iron Ingot"), "whole-name typo correction");
        require(SearchCorrection.suggest("钻百", List.of(
                new ClientSearch.Entry<>("diamond", "钻石块", List.of(), List.of(), "", List.of())), 3).isEmpty(),
                "short Unicode queries do not create noisy corrections");
        require(SearchCorrection.suggest("钻百块", List.of(
                new ClientSearch.Entry<>("diamond", "钻石块", List.of(), List.of(), "", List.of())), 3)
                .get(0).completion().equals("钻石块"), "Unicode correction uses code points");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
