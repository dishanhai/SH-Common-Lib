// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.fumo;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Shared data format for the Fumo editor and the server's reward loader. */
public final class FumoRewardDefinition {
    public static final String CONFIG_PATH = "sh_common_lib/fumo_rewards.json";
    public static final String RESOURCE_ID = "sh_common_lib:fumo_rewards/pool.json";
    public static final String DATA_PATH = "data/" + RESOURCE_ID.replace(':', '/');
    public static final int MAX_BYTES = 64 * 1024;
    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private FumoRewardDefinition() {}

    public record Entry(String item, int weight) {
        public Entry { Objects.requireNonNull(item); }
    }

    /** The index of the first invalid entry and its validation key. */
    public record Problem(int index, String key) {}

    public static Problem firstProblem(List<Entry> entries) {
        var seen = new HashSet<String>();
        long total = 0;
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            if (!ITEM_ID.matcher(entry.item()).matches() || entry.item().equals("minecraft:air"))
                return new Problem(i, "invalid_reward_item");
            if (!seen.add(entry.item())) return new Problem(i, "duplicate_reward_item");
            if (entry.weight() <= 0) return new Problem(i, "invalid_reward_weight");
            total += entry.weight();
            if (total > Integer.MAX_VALUE) return new Problem(i, "reward_weight_overflow");
        }
        return null;
    }

    /** Decodes fields without enforcing semantic validity, so drafts can retain edits. */
    public static List<Entry> read(JsonObject root) {
        var rewards = root.getAsJsonArray("rewards");
        if (rewards == null) throw new IllegalArgumentException("missing rewards array");

        var entries = new ArrayList<Entry>();
        for (var reward : rewards) {
            var object = reward.getAsJsonObject();
            var id = object.get("item");
            var weight = object.get("weight");
            if (id == null || !id.isJsonPrimitive() || !id.getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("item must be a string");
            if (weight == null || !weight.isJsonPrimitive() || !weight.getAsJsonPrimitive().isNumber())
                throw new IllegalArgumentException("weight must be an integer for " + id);
            entries.add(new Entry(id.getAsString(), weight.getAsBigDecimal().intValueExact()));
        }
        return List.copyOf(entries);
    }

    public static JsonObject json(List<Entry> entries) {
        var rewards = new JsonArray();
        for (var entry : entries) {
            var reward = new JsonObject();
            reward.addProperty("item", entry.item());
            reward.addProperty("weight", entry.weight());
            rewards.add(reward);
        }
        var root = new JsonObject();
        root.add("rewards", rewards);
        return root;
    }
}
