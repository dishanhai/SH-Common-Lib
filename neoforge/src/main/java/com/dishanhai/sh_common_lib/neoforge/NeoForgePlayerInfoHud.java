package com.dishanhai.sh_common_lib.neoforge;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import com.dishanhai.sh_common_lib.client.control.ControlCenterModule;
import com.dishanhai.sh_common_lib.client.hud.PlayerInfoHud;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

final class NeoForgePlayerInfoHud {
    private NeoForgePlayerInfoHud() {}

    static void register(IEventBus modBus) {
        PlayerInfoHud hud = new PlayerInfoHud(FMLPaths.CONFIGDIR.get());
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            event.register(hud.editKey());
            event.register(ControlCenterModule.openKey());
        });
        modBus.addListener((FMLClientSetupEvent event) -> ModList.get().getModContainerById(ShCommonLib.MOD_ID)
                .ifPresent(container -> container.registerExtensionPoint(IConfigScreenFactory.class,
                        (ignored, parent) -> ControlCenterModule.create(parent, hud))));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            hud.tick();
            ControlCenterModule.tick(hud);
        });
        NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Post event) -> {
            if (!Minecraft.getInstance().gui.getDebugOverlay().showDebugScreen()) hud.render(event.getGuiGraphics());
        });
    }
}
