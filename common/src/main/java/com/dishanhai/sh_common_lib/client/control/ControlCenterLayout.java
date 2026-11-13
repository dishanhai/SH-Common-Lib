// Author: dishanhai
package com.dishanhai.sh_common_lib.client.control;

/** Shared panel geometry for the control center across Minecraft versions. */
record ControlCenterLayout(int left, int top, int width, int height) {
    static final int ROW = 22;
    static final int GAP = 8;
    static final int PAGE_BUTTON_WIDTH = 22;
    static final int PAGE_LABEL_WIDTH = 34;
    private static final int PADDING = 14;

    static ControlCenterLayout of(int screenWidth, int screenHeight) {
        int width = Math.max(0, Math.min(624, screenWidth - 12));
        int height = Math.max(0, Math.min(330, screenHeight - 12));
        return new ControlCenterLayout((screenWidth - width) / 2, (screenHeight - height) / 2, width, height);
    }

    boolean fits() { return width >= 168 && height >= 148; }
    private boolean compact() { return height < 210; }
    int contentX() { return left + PADDING; }
    int contentWidth() { return Math.max(0, width - 2 * PADDING); }
    int titleY() { return top + (compact() ? 8 : 15); }
    int pageY() { return top + (compact() ? 4 : 8); }
    int pageHeight() { return compact() ? 18 : ROW; }
    int pageNextX() { return contentX() + contentWidth() - PAGE_BUTTON_WIDTH; }
    int pagePreviousX() { return pageNextX() - PAGE_LABEL_WIDTH - PAGE_BUTTON_WIDTH; }
    int tabsY() { return top + (compact() ? 24 : 35); }
    int groupsY() { return top + (compact() ? 50 : 65); }
    int bodyTop() { return top + (compact() ? 76 : 94); }
    int footerY() { return top + height - (compact() ? 6 : PADDING) - ROW; }
    int bodyBottom() { return footerY() - (compact() ? 4 : 16); }
    int bodyHeight() { return Math.max(0, bodyBottom() - bodyTop()); }

    static int capacity(int availableHeight, int rowHeight, int gap, int columns) {
        int rows = Math.max(1, (Math.max(0, availableHeight) + gap) / (rowHeight + gap));
        return rows * columns;
    }

    static int pages(int size, int capacity) {
        return Math.max(1, (size + capacity - 1) / capacity);
    }
}
