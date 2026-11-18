// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.acceleration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class TickBudgetTest {
    public static void main(String[] args) {
        var visits = new ArrayList<Integer>();
        var tasks = List.of(0, 1, 2);
        int cursor = TickBudget.drain(tasks, 0, 2, () -> 0, 1, task -> { visits.add(task); return true; });
        if (!visits.equals(List.of(0, 1)) || cursor != 2) throw new AssertionError("first budget slice");
        visits.clear();
        cursor = TickBudget.drain(tasks, cursor, 2, () -> 0, 1, task -> { visits.add(task); return true; });
        if (!visits.equals(List.of(2, 0)) || cursor != 1) throw new AssertionError("deferred work must rotate");
        visits.clear();
        var clock = new AtomicLong();
        TickBudget.drain(tasks, 0, 100, clock::getAndIncrement, 2, task -> { visits.add(task); return true; });
        if (!visits.equals(List.of(0, 1))) throw new AssertionError("time deadline must bound calls");
        visits.clear();
        TickBudget.drain(tasks, 0, 100, () -> 0, 1, task -> { visits.add(task); return false; });
        if (visits.size() != 3) throw new AssertionError("no runnable work must stop after one cycle");
    }
}
