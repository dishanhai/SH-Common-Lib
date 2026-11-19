// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.fumo;

import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;

/** An immutable weighted pool compiled from a single configuration source. */
final class FumoRewardPool<T> {
    private final List<Entry<T>> entries;
    private final int totalWeight;

    private FumoRewardPool(List<Entry<T>> entries, int totalWeight) {
        this.entries = List.copyOf(entries);
        this.totalWeight = totalWeight;
    }

    static <T> FumoRewardPool<T> uniform(List<T> items) {
        return new FumoRewardPool<>(items.stream().map(item -> new Entry<>(item, 1)).toList(), items.size());
    }

    static <T> FumoRewardPool<T> read(InputStream input, Function<String, T> resolveItem) throws IOException {
        byte[] bytes = input.readNBytes(FumoRewardDefinition.MAX_BYTES + 1);
        if (bytes.length > FumoRewardDefinition.MAX_BYTES) throw new IllegalArgumentException("reward pool exceeds 64 KiB");

        var root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        var rewards = FumoRewardDefinition.read(root);
        var problem = FumoRewardDefinition.firstProblem(rewards);
        if (problem != null) {
            throw new IllegalArgumentException(problem.key() + " for " + rewards.get(problem.index()).item());
        }

        List<Entry<T>> entries = new ArrayList<>();
        var seen = new HashSet<T>();
        int totalWeight = 0;
        for (var reward : rewards) {
            T item = resolveItem.apply(reward.item());
            if (item == null) throw new IllegalArgumentException("unknown or invalid item " + reward.item());
            if (!seen.add(item)) throw new IllegalArgumentException("duplicate item " + reward.item());
            totalWeight += reward.weight();
            entries.add(new Entry<>(item, reward.weight()));
        }
        return new FumoRewardPool<>(entries, totalWeight);
    }

    T choose(IntUnaryOperator nextInt) {
        if (totalWeight == 0) return null;

        int roll = Objects.checkIndex(nextInt.applyAsInt(totalWeight), totalWeight);
        for (var entry : entries) {
            roll -= entry.weight();
            if (roll < 0) return entry.item();
        }
        throw new IllegalStateException("reward pool weights do not match the total");
    }

    private record Entry<T>(T item, int weight) {}
}
