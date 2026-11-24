// Author: dishanhai
package com.dishanhai.sh_common_lib.client.control;

/** Checks the page controls, settings rows and footer across supported window sizes. */
final class ControlCenterLayoutTest {
    static void run() {
        check(ControlCenterLayout.settingsColumns(true, 243) == 1, "narrow settings must use one column");
        check(ControlCenterLayout.settingsColumns(true, 244) == 2, "two readable columns should fit");
        check(ControlCenterLayout.settingsColumns(false, 400) == 1, "single-column groups must keep their layout");
        for (int width = 180; width <= 1800; width += 7) {
            for (int height = 160; height <= 1000; height += 5) {
                var layout = ControlCenterLayout.of(width, height);
                if (!layout.fits()) continue;
                check(layout.pagePreviousX() >= layout.contentX(), "page controls cross the panel edge");
                check(layout.pageNextX() + ControlCenterLayout.PAGE_BUTTON_WIDTH <= layout.contentX() + layout.contentWidth(),
                        "page controls cross the content edge");
                check(layout.pageY() + layout.pageHeight() <= layout.tabsY(), "page controls overlap the tabs");
                check(layout.tabsWidth() <= layout.contentWidth(), "tabs cross the content edge");
                check(layout.tabsY() + ControlCenterLayout.ROW <= layout.bodyTop(true), "overview overlaps the tabs");
                check(layout.groupsY() + ControlCenterLayout.ROW <= layout.bodyTop(false), "settings overlap the group tabs");
                check(layout.bodyHeight(true) > layout.bodyHeight(false), "overview did not reclaim the group row");
                check(layout.bodyBottom() <= layout.footerY(), "content overlaps the action buttons");
                int actionWidth = layout.footerActionWidth();
                check(layout.footerResetX() >= layout.contentX() + actionWidth + ControlCenterLayout.GAP,
                        "restore overlaps back");
                check(layout.footerApplyX() >= layout.footerResetX() + actionWidth + ControlCenterLayout.GAP,
                        "apply overlaps restore");
                check(layout.footerApplyX() + actionWidth == layout.contentX() + layout.contentWidth(),
                        "apply is not aligned to the content edge");
                int lineHeight = 9;
                int overviewBottom = layout.bodyTop(true) + 4 + layout.overviewPageSize(lineHeight) * lineHeight;
                int statusY = layout.compact() ? layout.bodyBottom() - lineHeight - 2 : layout.footerStatusY(lineHeight);
                check(overviewBottom <= Math.min(layout.bodyBottom(), statusY), "overview overlaps the status text");
                if (!layout.compact()) {
                    check(statusY >= layout.bodyBottom() && statusY + lineHeight < layout.footerY(),
                            "status overlaps the settings or action buttons");
                }
                int firstRow = layout.bodyTop(false) + 4 + lineHeight + 4;
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
