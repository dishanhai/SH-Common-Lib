// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.announcement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

import java.nio.file.Path;

/** Client bootstrap for the announcement folder owned by SH-Common-Lib. */
public final class DefaultAnnouncement {
    private final Path directory;
    private boolean checked;

    public DefaultAnnouncement(Path configDirectory) {
        directory = configDirectory.resolve("sh_common_lib").resolve("changelog");
    }

    public void onScreenInit(Screen screen) {
        if (checked || !(screen instanceof TitleScreen)) return;
        var client = Minecraft.getInstance();
        client.tell(() -> {
            if (checked || client.screen != screen) return;
            checked = true;
            new AnnouncementCatalog(directory).installDefaultPreset();
            new FullscreenAnnouncements(directory).openUnread(screen);
        });
    }
}
