// Author: dishanhai
package com.dishanhai.sh_common_lib.client.jei;

import com.dishanhai.sh_better_ui.client.gui.layout.PopupArea;

public final class JeiSearchLayoutTest {
    public static void main(String[] args) {
        // A 40px strip used to pass the 30px minimum and shrink a 96px-high list.
        var cramped = JeiSearchLayout.candidates(448, 270, 370,
                new PopupArea.Rect(40, 8, 250, 214), 180, 96);
        require(cramped != null && cramped.width() >= 180 && cramped.height() >= 96,
                "terminal margins must not shrink a readable candidate list");
        var spacious = JeiSearchLayout.candidates(640, 360, 590,
                new PopupArea.Rect(30, 20, 220, 170), 180, 96);
        require(spacious != null && spacious.x() >= 250,
                "keep candidates outside the terminal when they fit at normal size");
        for (int terminalWidth : new int[]{176, 250, 320, 400}) {
            for (int terminalHeight : new int[]{150, 214, 256}) {
                var area = JeiSearchLayout.candidates(448, 270, 370,
                        new PopupArea.Rect(20, 4, terminalWidth, terminalHeight), 180, 96);
                require(area != null && area.width() >= 180 && area.height() >= 96,
                        "candidate readability must survive terminal resizing");
                require(area.x() >= 4 && area.y() >= 4 && area.x() + area.width() <= 366
                        && area.y() + area.height() <= 266, "stay inside the viewport before the search field");
            }
        }
        require(JeiSearchLayout.candidates(100, 80, 3, null, 80, 40) == null,
                "do not create an interactive list without usable space");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
