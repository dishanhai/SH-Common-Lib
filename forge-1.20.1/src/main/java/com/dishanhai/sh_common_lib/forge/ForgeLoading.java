// Author: dishanhai
package com.dishanhai.sh_common_lib.forge;

import com.dishanhai.sh_common_lib.client.loading.ClientLoading;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.ModList;

final class ForgeLoading {
    private ForgeLoading() {}

    static void register() {
        ClientLoading.initialize(FMLPaths.CONFIGDIR.get(), ModList.get().isLoaded("jei"));
        var bus = MinecraftForge.EVENT_BUS;
        bus.addListener((ScreenEvent.Init.Post event) -> ClientLoading.screenInitialized(event.getScreen()));
        bus.addListener((ScreenEvent.Render.Post event) -> ClientLoading.renderWorld(event.getScreen(), event.getGuiGraphics()));
        bus.addListener((RenderGuiEvent.Post event) -> ClientLoading.renderHud(event.getGuiGraphics()));
        bus.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) ClientLoading.tick();
        });
    }
}
