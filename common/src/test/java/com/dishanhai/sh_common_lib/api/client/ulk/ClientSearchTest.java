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
        var mixed = SearchSources.mix(List.of(new SearchSources.Group("quest", List.of("Iron", "Gold", "Diamond")),
                new SearchSources.Group("recent", List.of("iron", "Copper", "Tin"))), 2, 4);
        require(mixed.equals(List.of(new SearchSources.MixedItem("quest", "Iron"),
                new SearchSources.MixedItem("recent", "Copper"), new SearchSources.MixedItem("quest", "Gold"),
                new SearchSources.MixedItem("recent", "Tin"))), "mixed sources are fair, capped and deduplicated");
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
        var modNames = SearchModNames.index(List.of(new SearchModNames.Label("example", "示例模组"),
                new SearchModNames.Label("example", "示例模组"), new SearchModNames.Label("other", "其他")));
        require(modNames.get("example").equals(List.of("示例模组")), "mod labels grouped and deduplicated");
        require(SearchModNames.displayName("Example", modNames.get("example")).equals("示例模组"), "localized mod label preferred");
        var modIndex = ClientSearch.index(List.of(new ClientSearch.Entry<>("sample", "Sample", List.of("example:sample"),
                List.of(), SearchModNames.searchText("Example", modNames.get("example")), List.of())));
        require(modIndex.suggest("@示例", 4).get(0).completion().equals("@example"), "localized mod name completes namespace");
        var tags = new AsyncClientSearch<String>(Runnable::run);
        tags.replace(List.of(
                new ClientSearch.Entry<>("iron", "Iron Ingot", List.of("minecraft:iron_ingot"), List.of(), "Minecraft", List.of("forge:ingots/iron")),
                new ClientSearch.Entry<>("gold", "Gold Ingot", List.of("minecraft:gold_ingot"), List.of(), "Minecraft", List.of("forge:ingots/gold"))),
                java.util.Map.of("forge:ingots/iron", "锭 · 铁", "forge:ingots/gold", "锭 · 金"));
        tags.request("#铁锭", 5);
        require(tags.result().suggestions().get(0).completion().equals("#forge:ingots/iron"), "localized tag completes JEI identifier");
        tags.request("gold #锭", 5);
        require(tags.result().suggestions().size() == 1 && tags.result().suggestions().get(0).completion().equals("gold #forge:ingots/gold"),
                "localized tag respects preceding terms");
        require(SearchTagNames.display("forge:ingots/iron", key -> key.equals("sh_common_lib.jei.tag.ingots") ? "锭"
                        : key.equals("sh_common_lib.jei.tag.iron") ? "铁" : key)
                .equals("锭 · 铁"), "tag display translates path components");
        mixedPinyin();
        hotWords();
        serverHotWords();
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

    private static void mixedPinyin() {
        var flint = new ClientSearch.Entry<>("flint", "打火石", List.of("minecraft:flint_and_steel"),
                List.of(), "Minecraft", List.of());
        var index = ClientSearch.index(List.of(flint));
        var async = new AsyncClientSearch<String>(Runnable::run, true);
        async.replace(List.of(flint));
        for (String query : List.of("dah", "dahs", "dahuos", "dhuoshi", "dhs", "dahuoshi")) {
            require(index.search(query).equals(List.of("flint")), "mixed pinyin search: " + query);
            async.request(query, 5);
            require(async.result().suggestions().size() == 1, "mixed pinyin prediction: " + query);
            var suggestion = async.result().suggestions().getFirst();
            require(suggestion.completion().equals("打火石") && suggestion.matchRank() == 6,
                    "mixed pinyin completes original name without typo correction: " + query);
        }
        require(index.suggest("@minecraft dahs", 5).getFirst().completion().equals("@minecraft 打火石"),
                "mixed pinyin prediction keeps the mod filter");
    }

    private static void hotWords() {
        var ledger = new com.dishanhai.sh_common_lib.api.ulk.HotWordLedger();
        var one = java.util.UUID.randomUUID(); var two = java.util.UUID.randomUUID();
        require(ledger.record(one, "Iron", 10_000), "first hotword accepted");
        require(!ledger.record(one, "Gold", 11_000), "per-player report rate limited");
        require(ledger.record(two, "iron", 11_000), "different player can contribute");
        require(ledger.top(11_001, 5).size() == 1, "case-insensitive aggregation");
        require(!ledger.record(two, "bad\nword", 20_000), "control characters rejected");
        require(ledger.allowRequest(one, 10_000) && !ledger.allowRequest(one, 10_001), "requests rate limited");
        require(ledger.top(30_000_000, 5).isEmpty(), "old hotwords expire");
        int[] sent = {0, 0};
        ClientHotWords.install(new ClientHotWords.Transport() {
            public void request(Object conn) { sent[0]++; }
            public void report(Object conn, String query) { sent[1]++; }
        });
        Object first = new Object(), second = new Object();
        ClientHotWords.update(first, true, false, 100);
        ClientHotWords.report("iron", 100);
        require(sent[0] == 1 && sent[1] == 0, "receive does not enable sharing");
        ClientHotWords.accept(first, List.of("iron"));
        ClientHotWords.acceptExternal(first, List.of("gold", "iron"));
        require(ClientHotWords.words().equals(List.of("iron", "gold")), "external provider merges without duplicates");
        ClientHotWords.update(second, true, true, 200);
        ClientHotWords.accept(first, List.of("stale"));
        ClientHotWords.acceptExternal(first, List.of("stale external"));
        require(ClientHotWords.words().isEmpty(), "old connection reply ignored");
        ClientHotWords.report("iron", 200);
        ClientHotWords.report("gold", 201);
        require(sent[1] == 1, "explicit sharing is rate limited");
        ClientHotWords.update(second, false, false, 300);
        ClientHotWords.accept(second, List.of("late"));
        require(ClientHotWords.words().isEmpty(), "disabled source drops replies");
        ClientHotWords.reset();
    }

    private static void serverHotWords() throws Exception {
        var directory = java.nio.file.Files.createTempDirectory("ulk-hotwords");
        var config = directory.resolve("sh_common_lib");
        try {
            var player = java.util.UUID.randomUUID(); var server = new Object();
            com.dishanhai.sh_common_lib.internal.ulk.ServerHotWords.initialize(directory);
            require(!com.dishanhai.sh_common_lib.internal.ulk.ServerHotWords.enabled(), "server defaults to disabled");
            java.nio.file.Files.createDirectories(config);
            java.nio.file.Files.writeString(config.resolve("hotwords-server.properties"), "enabled=true\n");
            com.dishanhai.sh_common_lib.internal.ulk.ServerHotWords.initialize(directory);
            com.dishanhai.sh_common_lib.internal.ulk.ServerHotWords.report(server, player, "diamond");
            require(com.dishanhai.sh_common_lib.internal.ulk.ServerHotWords.top(server, player).orElseThrow().equals(List.of("diamond")),
                    "enabled server collects and replies");
            com.dishanhai.sh_common_lib.internal.ulk.ServerHotWords.clear(server);
            require(com.dishanhai.sh_common_lib.internal.ulk.ServerHotWords.top(server, player).orElseThrow().isEmpty(),
                    "server shutdown clears session aggregate");
        } finally {
            java.nio.file.Files.deleteIfExists(config.resolve("hotwords-server.properties"));
            java.nio.file.Files.deleteIfExists(config);
            java.nio.file.Files.deleteIfExists(directory);
        }
    }
}
