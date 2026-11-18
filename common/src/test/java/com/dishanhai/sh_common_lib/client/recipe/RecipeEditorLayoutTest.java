// Author: dishanhai
package com.dishanhai.sh_common_lib.client.recipe;

import com.dishanhai.sh_common_lib.client.recipe.RecipeEditorLayout.Rect;

public final class RecipeEditorLayoutTest {
    public static void main(String[] args) {
        int[][] viewports = {{320,180}, {320,200}, {320,219}, {320,220}, {420,240}, {519,240}, {520,240},
                {560,239}, {560,240}, {560,320}, {760,180}, {760,219}, {760,220}, {839,240}, {840,240},
                {960,540}, {1280,720}, {1680,960}};
        for (int[] size : viewports) {
            var layout = RecipeEditorLayout.compute(size[0], size[1]);
            var viewport = new Rect(0, 0, size[0], size[1]);
            require(viewport.contains(layout.toolbar()), "toolbar in viewport");
            require(viewport.contains(layout.navigation()), "navigation in viewport");
            require(viewport.contains(layout.content()), "content in viewport");
            require(viewport.contains(layout.status()), "status in viewport");
            require(layout.toolbar().bottom() < layout.navigation().y(), "toolbar/navigation separation");
            require(!intersects(layout.navigation(), layout.content()), "navigation/content separation");
            require(layout.content().bottom() < layout.status().y(), "content/status separation");
            require(layout.sidebar() == (size[0] >= 840 && size[1] >= 240), "sidebar breakpoint");
            require(layout.split() == (size[0] >= 520 && size[1] >= 240), "live preview breakpoint");
            require(layout.toolbar().width() <= 916 && layout.content().height() <= 322, "bounded desktop workspace");
            var controls = layout.navigationControls();
            require(layout.navigation().contains(controls), "navigation controls inside surface");
            if (layout.sidebar()) {
                require(controls.height() >= 23 * 3, "vertical navigation targets");
                require(controls.bottom() + 36 + 22 <= layout.navigation().bottom(), "sidebar document actions fit");
            } else require(controls.width() >= 27 * 3 && controls.height() >= 20, "horizontal navigation targets");
            require(layout.content().contains(layout.editor()), "editor in content");
            require(layout.content().contains(layout.preview()), "preview in content");
            require(layout.result(2).equals(layout.content()), "export page uses full width");
            if (layout.split()) {
                require(layout.editor().right() < layout.preview().x(), "editor/preview separation");
                require(layout.preview().width() >= 215, "readable live preview");
            } else {
                require(layout.editor().equals(layout.content()), "single pane uses available space");
            }
            var properties = layout.body(layout.editor());
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
            var pane = layout.body(layout.editor());
            var slotArea = RecipeEditorLayout.slotArea(pane, size[1] >= 220);
            require(pane.contains(slotArea) && slotArea.height() <= 98, "grid stays beside controls on large displays");
            require(pane.y() + 44 + 20 <= pane.bottom(), "machine inputs and outputs fit");
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
