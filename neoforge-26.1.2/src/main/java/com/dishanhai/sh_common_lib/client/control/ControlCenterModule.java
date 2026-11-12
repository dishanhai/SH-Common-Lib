// Author: dishanhai
package com.dishanhai.sh_common_lib.client.control;

import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.UIModule;
import com.dishanhai.sh_better_ui.kernel.api.UIPlatform;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_common_lib.api.ShCommonLib;
import com.dishanhai.sh_common_lib.client.hud.PlayerInfoHud;
import com.dishanhai.sh_common_lib.client.hud.PlayerInfoItem;
import com.dishanhai.sh_common_lib.client.loading.ClientLoading;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.util.Util;
import com.dishanhai.sh_common_lib.neoforge.NeoForgeKeyMappings;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.Arrays;

import static com.dishanhai.sh_common_lib.client.hud.PlayerInfoItem.*;

public final class ControlCenterModule implements UIModule {
    private static final UiId ID = new UiId(ShCommonLib.MOD_ID, "control_center");
    private static final KeyMapping OPEN_KEY = new KeyMapping("key.sh_common_lib.control_center",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F10, NeoForgeKeyMappings.CATEGORY);
    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder().id(ID).version(UiVersion.parse("1.0.0"))
                .platforms(Set.of(ModuleDescriptor.Platform.NEOFORGE_26_1_2))
                .build();
    }

    @Override
    public void register(ModuleContext context) {}

    @Override
    public void start(ModuleContext context) {
        context.engine().require(UIPlatform.KEY);
    }

    @Override
    public void stop(ModuleContext context) {}

    public static KeyMapping openKey() {
        return OPEN_KEY;
    }

    public static void tick(PlayerInfoHud hud) {
        if (!OPEN_KEY.consumeClick()) return;
        while (OPEN_KEY.consumeClick()) {
            // Consume repeated presses before opening the screen once.
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.screen == null && client.getOverlay() == null) {
            client.setScreen(create(null, hud));
        }
    }

    public static Screen create(Screen parent, PlayerInfoHud hud) {
        return new ControlCenterScreen(parent, settings(hud));
    }

    private static List<SettingsGroup> settings(PlayerInfoHud hud) {
        String playerInfoKey = "hud.sh_common_lib.player_info.";
        var playerInfo = new SettingsGroup(Component.translatable(playerInfoKey + "editor"), List.of(
                new SettingsGroup.Toggle(enabled -> Component.translatable(playerInfoKey + (enabled ? "visible" : "hidden")),
                        hud::isEnabled, hud::setEnabled),
                new SettingsGroup.Page(label("edit_position"), () -> {
                    var client = Minecraft.getInstance();
                    return client.player != null && client.level != null ? null : label("requires_world");
                }, hud::openEditor)));
        var developer = new SettingsGroup(label("developer"), List.of(
                new SettingsGroup.Page(label("developer_settings"),
                        () -> null, ControlCenterScreen::openDeveloperSettings)));
        var loading = new SettingsGroup(label("loading"), List.of(
                new SettingsGroup.Toggle(enabled -> label(enabled ? "loading_animation_on" : "loading_animation_off"),
                        ClientLoading::animation, ClientLoading::setAnimation),
                new SettingsGroup.Toggle(enabled -> label(enabled ? "loading_profiling_on" : "loading_profiling_off"),
                        ClientLoading::profiling, ClientLoading::setProfiling),
                new SettingsGroup.Page(label("loading_reports"),
                        () -> Files.isDirectory(ClientLoading.reportDirectory()) ? null : label("loading_reports_empty"),
                        (parent, focus) -> Util.getPlatform().openFile(ClientLoading.reportDirectory().toFile()))));
        return List.of(playerInfo,
                hudItems(hud, "hud_player", PLAYER, FPS, PING, LOCAL_TIME),
                hudItems(hud, "hud_world", WORLD_TIME, DIMENSION, CHUNKS, ENTITIES, BIOME),
                hudItems(hud, "hud_location", POSITION, CHUNK, LIGHT),
                hudItems(hud, "hud_server", TPS, MSPT),
                hudItems(hud, "hud_computer", MEMORY, CPU, GPU, DISK),
                loading,
                developer);
    }

    private static SettingsGroup hudItems(PlayerInfoHud hud, String group, PlayerInfoItem... items) {
        List<SettingsGroup.Entry> entries = Arrays.stream(items).map(item ->
                (SettingsGroup.Entry) new SettingsGroup.Toggle(enabled -> Component.translatable(
                        "screen.sh_common_lib.control_center.hud_item",
                        label("hud_item." + item.id()), label(enabled ? "item_shown" : "item_hidden")),
                        () -> hud.shows(item), enabled -> hud.setVisible(item, enabled))).toList();
        return new SettingsGroup(label(group), entries, true);
    }

    static Component label(String name) {
        return Component.translatable("screen.sh_common_lib.control_center." + name);
    }
}
