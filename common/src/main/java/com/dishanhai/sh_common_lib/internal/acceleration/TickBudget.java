// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.acceleration;

import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** Bounded round-robin dispatch; an executing callback runs to completion. */
final class TickBudget {
    private TickBudget() {}

    static <T> int drain(List<T> work, int previous, int maximumCalls, LongSupplier clock, long deadline,
                         Predicate<T> execute) {
        if (work.isEmpty()) return 0;
        int cursor = Math.floorMod(previous, work.size());
        int idle = 0;
        for (int calls = 0; calls < maximumCalls && clock.getAsLong() < deadline && idle < work.size(); calls++) {
            T item = work.get(cursor);
            cursor = (cursor + 1) % work.size();
            if (execute.test(item)) idle = 0; else idle++;
        }
        return cursor;
    }
}
