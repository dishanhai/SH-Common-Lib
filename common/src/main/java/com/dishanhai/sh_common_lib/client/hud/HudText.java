// Author: dishanhai
package com.dishanhai.sh_common_lib.client.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.Optional;

final class HudText {
    private HudText() {}

    static Component truncate(Font font, Component text, int width) {
        var marker = CommonComponents.ELLIPSIS;
        int markerWidth = font.width(marker);
        // Keep the original if there is no room even for the marker. Row fitting can then omit it.
        if (width < markerWidth || font.width(text) <= width) return text;
        var result = Component.empty();
        font.substrByWidth(text, width - markerWidth).visit((style, part) -> {
            result.append(Component.literal(part).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return result.append(marker);
    }
}
