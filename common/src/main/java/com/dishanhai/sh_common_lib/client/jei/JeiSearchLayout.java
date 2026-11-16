// Author: dishanhai
package com.dishanhai.sh_common_lib.client.jei;

import com.dishanhai.sh_better_ui.client.gui.layout.PopupArea;

final class JeiSearchLayout {
    private static final int MARGIN = 4;

    private JeiSearchLayout() {}

    static PopupArea.Rect candidates(int screenWidth, int screenHeight, int searchX,
                                   PopupArea.Rect host, int desiredWidth, int desiredHeight) {
        var area = PopupArea.fittingLeftOf(screenWidth, screenHeight, searchX, host, MARGIN, desiredWidth, desiredHeight);
        return fits(area) ? area : null;
    }

    private static boolean fits(PopupArea.Rect area) {
        return area.width() >= 64 && area.height() >= 30;
    }
}
