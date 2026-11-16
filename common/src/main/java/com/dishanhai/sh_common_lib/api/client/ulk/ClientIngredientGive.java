// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.ulk;

import java.util.Objects;

/** Optional client-thread ingredient give requests; the backend must retain server permission checks. */
public final class ClientIngredientGive {
    public enum Mode {
        ONE, STACK, INFINITY_CELL;

        public int amount(boolean fluid, int maxStackSize) {
            if (maxStackSize < 1) throw new IllegalArgumentException("maxStackSize must be positive");
            if (this != STACK) return 1;
            return fluid ? 16 : maxStackSize;
        }
    }

    public interface Backend {
        boolean enabled();
        boolean infinityCellsAvailable();
        /** Returns whether a request or a failure explanation was handled; never implies server success. */
        boolean give(Object ingredient, Mode mode);
    }

    private static Backend backend;
    private ClientIngredientGive() {}

    public static void install(Backend replacement) { backend = Objects.requireNonNull(replacement); }
    public static boolean enabled() { return backend != null && backend.enabled(); }
    public static boolean infinityCellsAvailable() { return enabled() && backend.infinityCellsAvailable(); }
    public static boolean request(Object ingredient, Mode mode) {
        return ingredient != null && mode != null && enabled() && backend.give(ingredient, mode);
    }
}
