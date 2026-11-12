package com.dishanhai.sh_common_lib.forge;

import com.mojang.blaze3d.platform.InputConstants;
import com.dishanhai.sh_common_lib.api.ShCommonLib;
import com.dishanhai.sh_common_lib.client.BlockInspectionShortcuts;
import com.dishanhai.sh_common_lib.client.control.ControlCenterModule;
import com.dishanhai.sh_common_lib.client.hud.PlayerInfoHud;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;

final class ForgePlayerInfoHud {
    private ForgePlayerInfoHud() {}

    static void register(IEventBus modBus) {
        PlayerInfoHud hud = new PlayerInfoHud(FMLPaths.CONFIGDIR.get(), ForgeTickNetwork::request,
                () -> Minecraft.getInstance().options.renderDebug);
        KeyMapping copyData = inspectionKey(BlockInspectionShortcuts.COPY_DATA, GLFW.GLFW_KEY_I);
        KeyMapping copyState = inspectionKey(BlockInspectionShortcuts.COPY_STATE, GLFW.GLFW_KEY_K);
        ControlCenterModule.bindHud(hud);
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            event.register(hud.editKey());
            event.register(ControlCenterModule.openKey());
            event.register(copyData);
            event.register(copyState);
        });
        modBus.addListener((FMLClientSetupEvent event) -> ModList.get().getModContainerById(ShCommonLib.MOD_ID)
                .ifPresent(container -> container.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                        () -> new ConfigScreenHandler.ConfigScreenFactory(parent -> ControlCenterModule.create(parent, hud)))));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                hud.tick();
                ControlCenterModule.tick(hud);
                BlockInspectionShortcuts.tick(copyData, copyState);
            }
        });
    }

    private static KeyMapping inspectionKey(String name, int key) {
        return new KeyMapping(name, KeyConflictContext.IN_GAME, KeyModifier.CONTROL,
                InputConstants.Type.KEYSYM, key, "key.categories.sh_common_lib");
    }
}
