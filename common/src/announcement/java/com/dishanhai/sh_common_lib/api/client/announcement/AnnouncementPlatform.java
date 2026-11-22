// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.announcement;

import com.dishanhai.sh_common_lib.api.ulk.markdown.MarkdownDocument.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

final class AnnouncementPlatform {
    private AnnouncementPlatform() {}

    static void schedule(Minecraft client, Runnable task) {
        client.tell(task);
    }

    static Style apply(Style style, String font) {
        return style.withFont(ResourceLocation.tryParse(font));
    }

    static boolean matches(Style style, String font) {
        return font.equals(style.getFont().toString());
    }

    static ClickEvent link(Link link) {
        return switch (link) {
            case WebLink web -> new ClickEvent(ClickEvent.Action.OPEN_URL, web.uri().toString());
            case AnchorLink anchor -> new ClickEvent(ClickEvent.Action.CHANGE_PAGE, anchor.id());
        };
    }
}
