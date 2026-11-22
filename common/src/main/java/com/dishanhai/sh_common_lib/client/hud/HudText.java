// Author: dishanhai
package com.dishanhai.sh_common_lib.client.hud;

import com.dishanhai.sh_better_ui.client.gui.text.UiHudRichText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.Optional;

final class HudText {
    private HudText() {}

    static Component truncate(Font font, Component text, int width) {
        if (font.width(text) <= width) return text;
        var marker = CommonComponents.ELLIPSIS;
        int markerWidth = font.width(marker);
        if (width < markerWidth) return Component.empty();
        var result = Component.empty();
        font.substrByWidth(text, width - markerWidth).visit((style, part) -> {
            result.append(Component.literal(part).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return result.append(marker);
    }

    static Component pageNotice(Font font, int width, int page, int total, int hidden, Component keys) {
        String lang = PlayerInfoHud.LANG;
        var counter = UiHudRichText.prepare(Component.translatable(lang + "page_counter", page, total));
        var full = UiHudRichText.prepare(Component.translatable(lang + (hidden > 0 ? "page_indicator_hidden" : "page_indicator"),
                page, total, keys, hidden));
        if (font.width(full) <= width) return full;
        var detail = hidden > 0 ? Component.translatable(lang + "space_limited_short", hidden) : keys;
        var compact = UiHudRichText.prepare(Component.translatable(lang + "page_indicator_compact",
                counter.copy().withStyle(ChatFormatting.BOLD), detail));
        if (font.width(compact) <= width) return compact;
        return truncate(font, counter, width);
    }

    static Component scaleLabel(int requested, float scale) {
        int actual = Math.round(scale * 100);
        return actual < requested
                ? Component.translatable(PlayerInfoHud.LANG + "scale_preview", requested, actual)
                : Component.translatable(PlayerInfoHud.LANG + "scale", requested);
    }
}
