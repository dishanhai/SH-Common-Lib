// Author: dishanhai
package com.dishanhai.sh_common_lib.client.recipe;

import com.dishanhai.sh_common_lib.client.recipe.RecipeEditorLayout.Rect;

public final class RecipeEditorLayoutTest {
    public static void main(String[] args) {
        int[][] viewports = {{320,180}, {320,200}, {320,219}, {320,220}, {420,240}, {560,320}, {759,320}, {760,180}, {760,219}, {760,220}, {760,320}, {960,540}, {1280,720}};
        for (int[] size : viewports) {
            var layout = RecipeEditorLayout.compute(size[0], size[1]);
            var viewport = new Rect(0, 0, size[0], size[1]);
            require(viewport.contains(layout.toolbar()), "toolbar in viewport");
            require(viewport.contains(layout.navigation()), "navigation in viewport");
            require(viewport.contains(layout.content()), "content in viewport");
            require(viewport.contains(layout.status()), "status in viewport");
            require(layout.toolbar().bottom() < layout.navigation().y(), "toolbar/navigation separation");
            require(layout.navigation().bottom() < layout.content().y(), "navigation/content separation");
            require(layout.content().bottom() < layout.status().y(), "content/status separation");
            require(layout.wide() == (size[0] >= 760), "breakpoint");
            for (int view = 0; view < 3; view++) require(layout.content().contains(layout.pane(view)), "pane in content");
            if (layout.wide()) {
                require(layout.canvas().right() < layout.properties().x(), "canvas/inspector separation");
                require(layout.properties().right() < layout.result().x(), "inspector/source separation");
                require(layout.result().width() >= 280, "source readable minimum width");
            } else {
                require(layout.canvas().equals(layout.properties()) && layout.canvas().equals(layout.result()), "compact pages share content area");
            }
            var properties = layout.properties();
            boolean compactFields = size[1] < 220;
            RecipeEditorLayout.FieldRow previous = null;
            for (int row = 0; row < 3; row++) {
                int columns = row == 2 ? 3 : 1;
                RecipeEditorLayout.FieldRow previousColumn = null;
                for (int column = 0; column < columns; column++) {
                    var fieldRow = RecipeEditorLayout.propertyRow(properties, row, column, columns, compactFields);
                    require(properties.contains(fieldRow.label()), "property label in pane");
                    require(properties.contains(fieldRow.field()), "property field in pane");
                    require(fieldRow.field().height() == 20 && fieldRow.field().width() >= 38, "usable property field");
                    require(!intersects(fieldRow.label(), fieldRow.field()), "label does not intersect its field");
                    if (previous != null) {
                        require(Math.max(previous.label().bottom(), previous.field().bottom())
                                < Math.min(fieldRow.label().y(), fieldRow.field().y()), "property rows do not overlap");
                    }
                    if (previousColumn != null) {
                        require(Math.max(previousColumn.label().right(), previousColumn.field().right())
                                < Math.min(fieldRow.label().x(), fieldRow.field().x()), "property columns do not overlap");
                    }
                    previousColumn = fieldRow;
                }
                previous = previousColumn;
            }
            var pane = layout.canvas();
            // A 22px type selector plus 2px separation leaves a usable 58px grid at 320x180.
            var slotArea = new Rect(pane.x(), pane.y() + 24, pane.width(), pane.height() - 24);
            for (int count : new int[] {1, 2, 3, 9}) for (boolean output : new boolean[] {false, true}) {
                var slots = RecipeEditorLayout.slots(slotArea, count, output);
                require(slotArea.contains(slots.inputs()), "input grid in canvas");
                require(slots.cell() >= 18 && slots.cell() <= 30, "usable slot targets");
                if (output) {
                    require(slotArea.contains(slots.output()), "output in canvas");
                    require(slotArea.contains(slots.arrow()), "arrow in canvas");
                    require(slots.inputs().right() < slots.arrow().x(), "arrow after inputs");
                    require(slots.arrow().right() < slots.output().x(), "arrow before output");
                } else require(slots.output().width() == 0 && slots.arrow().width() == 0, "no phantom output for trim");
            }
        }
        System.out.println("RecipeEditorLayoutTest passed: compact/wide panes, property labels/fields, and all ingredient roles");
    }
    private static boolean intersects(Rect first, Rect second) {
        return first.x() < second.right() && second.x() < first.right()
                && first.y() < second.bottom() && second.y() < first.bottom();
    }
    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
