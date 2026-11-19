// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.trash;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

final class TrashCanClient {
    static void register(IEventBus bus) {
        bus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            MenuScreens.register(TrashCanPlatform.MENU.get(), TrashCanScreen::new);
            TrashCanScreen.registerBlur();
        }));
    }
}
