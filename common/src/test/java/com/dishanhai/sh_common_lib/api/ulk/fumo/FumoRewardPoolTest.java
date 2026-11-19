// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.fumo;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class FumoRewardPoolTest {
    public static void main(String[] args) throws IOException {
        var pool = read("""
                {"rewards":[{"item":"minecraft:apple","weight":3},{"item":"example:gem","weight":2}]}
                """);
        for (int roll = 0; roll < 5; roll++) {
            int ticket = roll;
            String item = pool.choose(bound -> {
                check(bound == 5, "total configured weight");
                return ticket;
            });
            check(item.equals(roll < 3 ? "minecraft:apple" : "example:gem"), "weighted roll " + roll);
        }

        var empty = read("{\"rewards\":[]}");
        check(empty.choose(bound -> { throw new AssertionError("empty pool rolled"); }) == null, "empty pool");
        var defaults = FumoRewardPool.uniform(List.of("first", "second"));
        check(defaults.choose(bound -> bound - 1).equals("second"), "uniform pool");

        reject("{}", "missing pool");
        reject("{\"rewards\":{}}", "non-array pool");
        reject(poolWith("minecraft:apple", "0"), "zero weight");
        reject(poolWith("minecraft:apple", "-2"), "negative weight");
        reject(poolWith("minecraft:apple", "1.5"), "fractional weight");
        reject(poolWith("minecraft:apple", "\"2\""), "string weight");
        reject(poolWith("apple", "2"), "unnamespaced item");
        reject(poolWith("minecraft:air", "2"), "unknown or excluded item");
        reject("""
                {"rewards":[{"item":"minecraft:apple","weight":1},{"item":"minecraft:apple","weight":2}]}
                """, "duplicate item");
        reject("""
                {"rewards":[{"item":"minecraft:apple","weight":2147483647},{"item":"example:gem","weight":1}]}
                """, "total weight overflow");
        reject(" ".repeat(65537), "oversized pool");
    }

    private static String poolWith(String item, String weight) {
        return "{\"rewards\":[{\"item\":\"" + item + "\",\"weight\":" + weight + "}]}";
    }

    private static FumoRewardPool<String> read(String json) throws IOException {
        return FumoRewardPool.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                id -> List.of("minecraft:apple", "example:gem").contains(id) ? id : null);
    }

    private static void reject(String json, String description) throws IOException {
        try {
            read(json);
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError("accepted " + description);
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
