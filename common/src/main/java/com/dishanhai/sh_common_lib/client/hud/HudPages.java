// Author: dishanhai
package com.dishanhai.sh_common_lib.client.hud;

import com.dishanhai.sh_better_ui.kernel.api.text.TextFit;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** Partitions rows using the same priority and fitting rules as the single-page HUD. */
final class HudPages {
    private HudPages() {}

    static <T> Result<T> partition(List<T> rows, ToIntFunction<T> priority, Predicate<List<T>> fits, T footer) {
        var remaining = new ArrayList<>(rows);
        var pages = new ArrayList<List<T>>();
        Predicate<List<T>> withFooter = candidate -> {
            if (!candidate.isEmpty() && candidate.get(candidate.size() - 1) == footer) return fits.test(candidate);
            var measured = new ArrayList<>(candidate);
            measured.add(footer);
            return fits.test(measured);
        };
        while (!remaining.isEmpty()) {
            var selected = TextFit.selectRows(remaining, priority, withFooter, footer).visible();
            if (selected.isEmpty()) break;
            pages.add(selected);
            var used = new IdentityHashMap<T, Integer>();
            selected.forEach(row -> used.merge(row, 1, Integer::sum));
            remaining.removeIf(row -> {
                int count = used.getOrDefault(row, 0);
                if (count == 0) return false;
                used.put(row, count - 1);
                return true;
            });
        }
        return new Result<>(List.copyOf(pages), remaining.size());
    }

    record Result<T>(List<List<T>> pages, int omitted) {}
}
