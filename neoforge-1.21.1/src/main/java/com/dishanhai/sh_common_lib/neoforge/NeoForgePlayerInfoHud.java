package com.dishanhai.sh_common_lib.neoforge;

import com.mojang.blaze3d.platform.InputConstants;
import com.dishanhai.sh_common_lib.api.ShCommonLib;
import com.dishanhai.sh_common_lib.client.BlockInspectionShortcuts;
import com.dishanhai.sh_common_lib.client.control.ControlCenterModule;
import com.dishanhai.sh_common_lib.client.hud.PlayerInfoHud;
import com.dishanhai.sh_common_lib.client.hud.HudRuntimeInfo;
import com.dishanhai.sh_common_lib.client.hud.PlayerInfoItem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

final class NeoForgePlayerInfoHud {
    private NeoForgePlayerInfoHud() {}

    static void register(IEventBus modBus) {
        PlayerInfoHud hud = new PlayerInfoHud(FMLPaths.CONFIGDIR.get(), NeoForgeTickNetwork::request, NeoForgeTickNetwork::requestWeather,
                () -> Minecraft.getInstance().gui.getDebugOverlay().showDebugScreen(),
                new HudRuntimeInfo(PlayerInfoItem.NEOFORGE_VERSION, NeoForgePlayerInfoHud::runtimeMods, id -> ModList.get().isLoaded(id)));
        KeyMapping copyData = inspectionKey(BlockInspectionShortcuts.COPY_DATA, GLFW.GLFW_KEY_I);
        KeyMapping copyState = inspectionKey(BlockInspectionShortcuts.COPY_STATE, GLFW.GLFW_KEY_K);
        KeyMapping previousPage = pageKey("key.sh_common_lib.hud_previous_page", GLFW.GLFW_KEY_LEFT);
        KeyMapping nextPage = pageKey("key.sh_common_lib.hud_next_page", GLFW.GLFW_KEY_RIGHT);
        hud.bindPageKeys(previousPage, nextPage);
        hud.bindAe2Request(NeoForgeTickNetwork::requestAe2);
        hud.bindInventoryRequest(NeoForgeTickNetwork::requestInventoryHud);
        hud.bindEcoMachineRequest(NeoForgeTickNetwork::requestEcoMachines);
        ControlCenterModule.bindHud(hud);
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            event.register(hud.editKey());
            event.register(ControlCenterModule.openKey());
            event.register(copyData);
            event.register(copyState);
            event.register(previousPage);
            event.register(nextPage);
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

    private static HudRuntimeInfo.Mods runtimeMods() {
        var modList = ModList.get();
        var mods = modList.getMods();
        String minecraftVersion = mods.stream().filter(mod -> mod.getModId().equals("minecraft"))
                .map(mod -> mod.getVersion().toString()).findFirst().orElse(null);
        String loaderVersion = mods.stream().filter(mod -> mod.getModId().equals("neoforge"))
                .map(mod -> mod.getVersion().toString()).findFirst().orElse(null);
        long loaded = mods.stream().filter(mod -> modList.getModContainerById(mod.getModId()).isPresent()).count();
        return new HudRuntimeInfo.Mods(minecraftVersion, loaderVersion, loaded, mods.size());
    }

    private static KeyMapping inspectionKey(String name, int key) {
        return new KeyMapping(name, KeyConflictContext.IN_GAME, KeyModifier.CONTROL,
                InputConstants.Type.KEYSYM, key, "key.categories.sh_common_lib");
    }

    private static KeyMapping pageKey(String name, int key) {
        return new KeyMapping(name, KeyConflictContext.IN_GAME, KeyModifier.ALT,
                InputConstants.Type.KEYSYM, key, "key.categories.sh_common_lib");
    }
}
