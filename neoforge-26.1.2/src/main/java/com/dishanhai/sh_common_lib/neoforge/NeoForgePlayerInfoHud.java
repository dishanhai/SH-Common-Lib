package com.dishanhai.sh_common_lib.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import com.dishanhai.sh_common_lib.api.ShCommonLib;
import com.dishanhai.sh_common_lib.client.BlockInspectionShortcuts;
import com.dishanhai.sh_common_lib.client.control.ControlCenterModule;
import com.dishanhai.sh_common_lib.client.hud.PlayerInfoHud;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

final class NeoForgePlayerInfoHud {
    private NeoForgePlayerInfoHud() {}

    static void register(IEventBus modBus) {
        PlayerInfoHud hud = new PlayerInfoHud(FMLPaths.CONFIGDIR.get(), NeoForgeTickNetwork::request,
                () -> Minecraft.getInstance().getDebugOverlay().showDebugScreen());
        KeyMapping copyData = inspectionKey(BlockInspectionShortcuts.COPY_DATA, GLFW.GLFW_KEY_I);
        KeyMapping copyState = inspectionKey(BlockInspectionShortcuts.COPY_STATE, GLFW.GLFW_KEY_K);
        modBus.addListener((RegisterGuiLayersEvent event) -> event.registerAboveAll(
                Identifier.fromNamespaceAndPath(ShCommonLib.MOD_ID, "player_info"),
                (graphics, delta) -> hud.render(graphics)));
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            event.register(hud.editKey());
            event.register(ControlCenterModule.openKey());
            event.register(copyData);
            event.register(copyState);
        });
        modBus.addListener((FMLClientSetupEvent event) -> ModList.get().getModContainerById(ShCommonLib.MOD_ID)
                .ifPresent(container -> container.registerExtensionPoint(IConfigScreenFactory.class,
                        (ignored, parent) -> ControlCenterModule.create(parent, hud))));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            hud.tick();
            ControlCenterModule.tick(hud);
            BlockInspectionShortcuts.tick(copyData, copyState);
        });
    }

    private static KeyMapping inspectionKey(String name, int key) {
        return new KeyMapping(name, KeyConflictContext.IN_GAME, KeyModifier.CONTROL,
                InputConstants.Type.KEYSYM, key, NeoForgeKeyMappings.CATEGORY);
    }
}
