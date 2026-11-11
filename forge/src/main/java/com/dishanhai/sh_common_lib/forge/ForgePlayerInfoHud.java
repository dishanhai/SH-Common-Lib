package com.dishanhai.sh_common_lib.forge;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import com.dishanhai.sh_common_lib.client.control.ControlCenterModule;
import com.dishanhai.sh_common_lib.client.hud.PlayerInfoHud;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;

final class ForgePlayerInfoHud {
    private ForgePlayerInfoHud() {}

    static void register(IEventBus modBus) {
        PlayerInfoHud hud = new PlayerInfoHud(FMLPaths.CONFIGDIR.get());
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            event.register(hud.editKey());
            event.register(ControlCenterModule.openKey());
        });
        modBus.addListener((FMLClientSetupEvent event) -> ModList.get().getModContainerById(ShCommonLib.MOD_ID)
                .ifPresent(container -> container.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                        () -> new ConfigScreenHandler.ConfigScreenFactory(parent -> ControlCenterModule.create(parent, hud)))));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                hud.tick();
                ControlCenterModule.tick(hud);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((RenderGuiEvent.Post event) -> {
            if (!Minecraft.getInstance().options.renderDebug) hud.render(event.getGuiGraphics());
        });
    }
}
