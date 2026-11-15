// Author: dishanhai
package com.dishanhai.sh_common_lib.client.jei;

import com.dishanhai.sh_better_ui.client.gui.layout.PopupArea;

final class JeiSearchLayout {
    private static final int MARGIN = 4;

    private JeiSearchLayout() {}

    static PopupArea.Rect candidates(int screenWidth, int screenHeight, int searchX,
                                   PopupArea.Rect host, int desiredWidth, int desiredHeight) {
        var area = PopupArea.leftOf(screenWidth, screenHeight, searchX, host, MARGIN, desiredWidth, desiredHeight);
        if (fits(area)) return area;

        // A wide or tall terminal can leave too little space beside and below it.
        var overlay = PopupArea.leftOf(screenWidth, screenHeight, searchX, null, MARGIN);
        return fits(overlay) ? overlay : null;
    }

    private static boolean fits(PopupArea.Rect area) {
        return area.width() >= 64 && area.height() >= 30;
    }
}
