// Author: dishanhai
package com.dishanhai.sh_common_lib.client.hud;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.function.Function;

/** Measures each row once while the priority selector tries different combinations. */
final class HudRowFit<T> implements Predicate<List<T>> {
    private final int baseWidth, baseHeight, emptyWidth, rowHeight, availableWidth, availableHeight;
    private final float minimum;
    private final ToIntFunction<T> textWidth;
    private final IdentityHashMap<T, Integer> widths = new IdentityHashMap<>();
    private final Function<List<T>, List<T>> expand;

    HudRowFit(int baseWidth, int baseHeight, int emptyWidth, int rowHeight,
              int availableWidth, int availableHeight, float minimum, ToIntFunction<T> textWidth) {
        this(baseWidth, baseHeight, emptyWidth, rowHeight, availableWidth, availableHeight, minimum, textWidth, Function.identity());
    }

    HudRowFit(int baseWidth, int baseHeight, int emptyWidth, int rowHeight,
              int availableWidth, int availableHeight, float minimum, ToIntFunction<T> textWidth,
              Function<List<T>, List<T>> expand) {
        this.baseWidth = baseWidth;
        this.baseHeight = baseHeight;
        this.emptyWidth = emptyWidth;
        this.rowHeight = rowHeight;
        this.availableWidth = availableWidth;
        this.availableHeight = availableHeight;
        this.minimum = minimum;
        this.textWidth = textWidth;
        this.expand = expand;
    }

    @Override public boolean test(List<T> rows) {
        List<T> expanded = expand.apply(rows);
        int width = baseWidth;
        for (T row : expanded) {
            width = Math.max(width, emptyWidth + widths.computeIfAbsent(row, textWidth::applyAsInt));
        }
        int height = baseHeight + rowHeight * expanded.size();
        float scale = Math.min(minimum, Math.min((float) availableWidth / width, (float) availableHeight / height));
        return scale >= minimum;
    }
}
