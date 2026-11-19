// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.trash;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

final class TrashCanClient {
    static void register(IEventBus bus) {
        bus.addListener((RegisterMenuScreensEvent event) ->
                event.register(TrashCanPlatform.MENU.get(), TrashCanScreen::new));
    }
}
