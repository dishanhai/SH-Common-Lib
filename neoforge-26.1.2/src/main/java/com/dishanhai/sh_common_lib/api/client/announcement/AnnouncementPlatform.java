// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.announcement;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

final class AnnouncementPlatform {
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
}
