// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge;

import com.dishanhai.sh_common_lib.client.loading.ClientLoading;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

final class NeoForgeLoading {
    private NeoForgeLoading() {}

    static void register() {
        ClientLoading.initialize(FMLPaths.CONFIGDIR.get(), ModList.get().isLoaded("jei"));
        var bus = NeoForge.EVENT_BUS;
        bus.addListener((ScreenEvent.Init.Post event) -> ClientLoading.screenInitialized(event.getScreen()));
        bus.addListener((ScreenEvent.Render.Post event) -> ClientLoading.renderWorld(event.getScreen(), event.getGuiGraphics()));
        bus.addListener((RenderGuiEvent.Post event) -> ClientLoading.renderHud(event.getGuiGraphics()));
        bus.addListener((ClientTickEvent.Post event) -> ClientLoading.tick());
    }
}
