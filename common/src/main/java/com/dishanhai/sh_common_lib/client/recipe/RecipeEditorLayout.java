// Author: dishanhai
package com.dishanhai.sh_common_lib.client.recipe;

/** Shared, renderer-independent geometry for the editor's compact and desktop workspaces. */
public final class RecipeEditorLayout {
    private RecipeEditorLayout() {}

    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public Rect inset(int padding) {
            return new Rect(x + padding, y + padding, width - padding * 2, height - padding * 2);
        }
        public boolean contains(Rect other) {
            return other.x >= x && other.y >= y && other.right() <= right() && other.bottom() <= bottom();
        }
    }

    public record Layout(Rect toolbar, Rect navigation, Rect content, Rect status,
                         Rect editor, Rect preview, boolean sidebar, boolean split) {
        public Rect navigationControls() {
            return sidebar ? new Rect(navigation.x, navigation.y + 18, navigation.width, 84) : navigation;
        }

        public Rect body(Rect panel) {
            int header = split || sidebar ? 20 : 0;
            int pad = header == 0 ? 0 : 6;
            return new Rect(panel.x + pad, panel.y + header, panel.width - pad * 2, panel.height - header - pad);
        }

        public Rect result(int view) {
            return view == 2 ? content : preview;
        }
    }

    /** The game's supported minimum logical viewport is 320 x 180. */
    public static Layout compute(int width, int height) {
        int windowWidth = Math.min(940, width), windowHeight = Math.min(400, height);
        int x = (width - windowWidth) / 2 + 12, y = (height - windowHeight) / 2;
        boolean roomy = height >= 240;
        var toolbar = new Rect(x, y + 8, windowWidth - 24, roomy ? 32 : 24);
        var status = new Rect(x, y + windowHeight - 22, windowWidth - 24, 18);
        boolean sidebar = width >= 840 && roomy;
        int navTop = toolbar.bottom() + 8;
        int top = sidebar ? navTop + 4 : navTop + 32;
        int heightAvailable = status.y - 4 - top;
        var navigation = sidebar
                ? new Rect(x, top, 119, heightAvailable)
                : new Rect(x, navTop, Math.min(356, windowWidth - 24), 22);
        int left = sidebar ? navigation.right() + 14 : x;
        var content = new Rect(left, top, toolbar.right() - left, heightAvailable);
        boolean split = width >= 520 && roomy;
        if (!split) return new Layout(toolbar, navigation, content, status, content, content, sidebar, false);
        int editorWidth = (content.width - 16) * 55 / 100;
        var editor = new Rect(content.x, top, editorWidth, heightAvailable);
        var preview = new Rect(editor.right() + 16, top, content.width - editorWidth - 16, heightAvailable);
        return new Layout(toolbar, navigation, content, status, editor, preview, sidebar, true);
    }

    public static Rect slotArea(Rect canvas, boolean labelled) {
        int offset = labelled ? 40 : 24;
        return new Rect(canvas.x, canvas.y + offset, canvas.width, Math.min(98, canvas.height - offset));
    }

    public record FieldRow(Rect label, Rect field) {}

    /** Short windows use inline labels; regular windows leave a full line above each field. */
    public static FieldRow propertyRow(Rect pane, int row, int column, int columns, boolean compact) {
        int gap = 4;
        int columnWidth = (pane.width - gap * (columns - 1)) / columns;
        int x = pane.x + column * (columnWidth + gap);
        // Let the final column consume division remainder without shrinking preceding controls.
        int width = column == columns - 1 ? pane.right() - x : columnWidth;
        int y = pane.y + row * (compact ? 26 : 36);
        if (compact) {
            int labelWidth = Math.min(64, width / 3);
            return new FieldRow(new Rect(x, y + 6, labelWidth, 9),
                    new Rect(x + labelWidth + gap, y, width - labelWidth - gap, 20));
        }
        return new FieldRow(new Rect(x, y, width, 9), new Rect(x, y + 12, width, 20));
    }

    public record Slots(Rect inputs, Rect output, Rect arrow, int cell, int columns, int rows) {}

    /** Fits and centers ingredient roles as one unit, never placing the result outside its pane. */
    public static Slots slots(Rect area, int count, boolean hasOutput) {
        if (count < 1 || count > 9) throw new IllegalArgumentException("Ingredient count must be 1..9");
        int columns = count > 3 ? 3 : count;
        int rows = (count + columns - 1) / columns;
        int gap = 2;
        int arrowGap = hasOutput ? 24 : 0;
        int widthCell = (area.width - gap * (columns - 1) - arrowGap) / (columns + (hasOutput ? 1 : 0));
        int heightCell = (area.height - gap * (rows - 1)) / rows;
        int cell = Math.min(30, Math.min(widthCell, heightCell));
        if (cell < 18) throw new IllegalArgumentException("Slot area cannot fit minimum 18px targets: " + area);
        int gridWidth = columns * cell + (columns - 1) * gap;
        int gridHeight = rows * cell + (rows - 1) * gap;
        int totalWidth = gridWidth + arrowGap + (hasOutput ? cell : 0);
        int x = area.x + (area.width - totalWidth) / 2;
        int y = area.y + (area.height - gridHeight) / 2;
        var inputs = new Rect(x, y, gridWidth, gridHeight);
        var output = new Rect(inputs.right() + arrowGap, y + (gridHeight - cell) / 2, hasOutput ? cell : 0, hasOutput ? cell : 0);
        var arrow = new Rect(inputs.right() + 4, y + (gridHeight - 12) / 2, hasOutput ? 16 : 0, hasOutput ? 12 : 0);
        return new Slots(inputs, output, arrow, cell, columns, rows);
    }
}
