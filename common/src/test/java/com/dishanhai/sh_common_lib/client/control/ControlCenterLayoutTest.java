// Author: dishanhai
package com.dishanhai.sh_common_lib.client.control;

/** Checks the page controls, settings rows and footer across supported window sizes. */
final class ControlCenterLayoutTest {
    static void run() {
        for (int width = 180; width <= 1800; width += 7) {
            for (int height = 160; height <= 1000; height += 5) {
                var layout = ControlCenterLayout.of(width, height);
                if (!layout.fits()) continue;
                check(layout.pagePreviousX() >= layout.contentX(), "page controls cross the panel edge");
                check(layout.pageNextX() + ControlCenterLayout.PAGE_BUTTON_WIDTH <= layout.contentX() + layout.contentWidth(),
                        "page controls cross the content edge");
                check(layout.pageY() + layout.pageHeight() <= layout.tabsY(), "page controls overlap the tabs");
                check(layout.bodyBottom() <= layout.footerY(), "content overlaps the action buttons");
                int firstRow = layout.bodyTop() + 4 + 9 + 4;
                for (int columns = 1; columns <= 2; columns++) {
                    int capacity = ControlCenterLayout.capacity(layout.bodyBottom() - firstRow,
                            ControlCenterLayout.ROW, ControlCenterLayout.GAP, columns);
                    int rows = capacity / columns;
                    int lastBottom = firstRow + (rows - 1) * (ControlCenterLayout.ROW + ControlCenterLayout.GAP)
                            + ControlCenterLayout.ROW;
                    check(lastBottom <= layout.bodyBottom(), "a settings row crosses the content edge");
                }
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private ControlCenterLayoutTest() {}
}
