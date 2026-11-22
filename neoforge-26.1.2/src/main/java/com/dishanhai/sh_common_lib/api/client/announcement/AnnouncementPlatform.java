// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.announcement;

import com.dishanhai.sh_common_lib.api.ulk.markdown.MarkdownDocument.*;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.Optional;

final class AnnouncementPlatform {
    static final Identifier ANCHOR = Identifier.fromNamespaceAndPath("sh_common_lib", "announcement_anchor");

    private AnnouncementPlatform() {}

    static void schedule(Minecraft client, Runnable task) {
        client.schedule(client.wrapRunnable(task));
    }

    static Style apply(Style style, String font) {
        return style.withFont(new FontDescription.Resource(Identifier.parse(font)));
    }

    static boolean matches(Style style, String font) {
        return style.getFont() instanceof FontDescription.Resource resource && font.equals(resource.id().toString());
    }

    static ClickEvent link(Link link) {
        return switch (link) {
            case WebLink web -> new ClickEvent.OpenUrl(web.uri());
            case AnchorLink anchor -> new ClickEvent.Custom(ANCHOR, Optional.of(StringTag.valueOf(anchor.id())));
        };
    }
}
