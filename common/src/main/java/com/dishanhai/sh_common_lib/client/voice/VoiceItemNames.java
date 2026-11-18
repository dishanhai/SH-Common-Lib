// Author: dishanhai
package com.dishanhai.sh_common_lib.client.voice;

import com.dishanhai.sh_common_lib.api.client.ulk.SearchCorrection;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class VoiceItemNames {
    private static final Gson GSON = new Gson();
    private static final List<String> LANGUAGES = List.of("lang/en_us.json", "lang/zh_cn.json");
    private static final Map<String, Item> ALIASES = Map.of(
            "wood", Items.OAK_LOG, "木头", Items.OAK_LOG, "木頭", Items.OAK_LOG,
            "water", Items.WATER_BUCKET, "水", Items.WATER_BUCKET);
    private static Index index;

    private VoiceItemNames() {}

    static Item resolve(String spoken, ResourceManager resources, boolean fuzzy) {
        String name = normalize(spoken);
        if (name.isEmpty()) return Items.AIR;
        Item alias = ALIASES.get(name);
        if (alias != null) return alias;

        var language = Language.getInstance();
        if (index == null || index.resources() != resources || index.language() != language) {
            index = buildIndex(resources, language);
        }
        Item byId = index.ids().get(name);
        if (byId != null) return byId;
        Item byName = index.names().get(name);
        if (byName != null) return byName;
        return fuzzy ? SearchCorrection.uniqueNameMatch(name, index.names()).orElse(Items.AIR) : Items.AIR;
    }

    private static Index buildIndex(ResourceManager resources, Language language) {
        var languages = loadLanguages(resources).values();
        Map<String, Item> ids = new HashMap<>();
        Map<String, Item> names = new HashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            ids.put(BuiltInRegistries.ITEM.getKey(item).toString(), item);
            String key = item.getDescriptionId();
            for (var translations : languages) addName(names, translations.get(key), item);
        }
        names.putAll(ALIASES);
        return new Index(resources, language, ids, names);
    }

    private static void addName(Map<String, Item> names, String text, Item item) {
        String name = normalize(text);
        if (!name.isEmpty()) {
            names.merge(name, item, (previous, next) -> previous == next ? previous : Items.AIR);
        }
    }

    private static Map<String, Map<String, String>> loadLanguages(ResourceManager resources) {
        Map<String, Map<String, String>> languages = new HashMap<>();
        resources.listResourceStacks("lang", id -> LANGUAGES.contains(id.getPath())).forEach((id, stack) -> {
            var translated = languages.computeIfAbsent(id.getPath(), ignored -> new HashMap<>());
            for (var resource : stack) {
                try (var reader = resource.openAsReader()) {
                    var entries = GSON.fromJson(reader, JsonObject.class);
                    if (entries == null) continue;
                    entries.entrySet().forEach(entry -> {
                        var value = entry.getValue();
                        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                            translated.put(entry.getKey(), value.getAsString());
                        }
                    });
                } catch (IOException | JsonParseException ignored) {
                    // The remaining resource packs can still provide translations.
                }
            }
        });
        return languages;
    }

    static String normalize(String text) {
        return text == null ? "" : Normalizer.normalize(text, Normalizer.Form.NFKC).strip()
                .replaceAll("[.!?,。！？、，]+$", "").strip().toLowerCase(Locale.ROOT);
    }

    private record Index(ResourceManager resources, Language language, Map<String, Item> ids, Map<String, Item> names) {}
}
