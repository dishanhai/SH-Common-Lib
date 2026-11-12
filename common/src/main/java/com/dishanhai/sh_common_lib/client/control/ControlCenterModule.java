// Author: dishanhai
package com.dishanhai.sh_common_lib.client.control;

import com.dishanhai.sh_better_ui.client.api.UiScreenBuilder;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.ScreenService;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UIModule;
import com.dishanhai.sh_better_ui.kernel.api.UIPermission;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_better_ui.kernel.api.VersionRange;
import com.dishanhai.sh_common_lib.api.ShCommonLib;
import com.dishanhai.sh_common_lib.client.hud.PlayerInfoHud;
import com.dishanhai.sh_common_lib.client.hud.PlayerInfoItem;
import com.dishanhai.sh_common_lib.client.loading.ClientLoading;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.List;
import java.util.Arrays;
import java.util.function.Supplier;

import static com.dishanhai.sh_common_lib.client.hud.PlayerInfoItem.*;

public final class ControlCenterModule implements UIModule {
    private static final UiId ID = new UiId(ShCommonLib.MOD_ID, "control_center");
    private static final KeyMapping OPEN_KEY = new KeyMapping("key.sh_common_lib.control_center",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F10, "key.categories.sh_common_lib");
    private static ControlCenterModule active;
    private static PlayerInfoHud playerInfoHud;

    private ModuleContext module;
    private ScreenService screens;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder().id(ID).version(UiVersion.parse("1.0.0"))
                .requiredCapability(UIApis.NATIVE_SCREEN, VersionRange.ANY)
                .requiredCapability(UIApis.NATIVE_HUD, VersionRange.ANY)
                .requestPermission(UIPermission.REGISTER_RENDER_PASS)
                .build();
    }

    @Override
    public void register(ModuleContext context) {}

    @Override
    public void start(ModuleContext context) {
        screens = context.engine().require(UIApis.SCREENS);
        context.engine().require(UIApis.HUD).mount(context, frame -> {
            if (playerInfoHud != null) playerInfoHud.render(frame);
        });
        module = context;
        active = this;
    }

    @Override
    public void stop(ModuleContext context) {
        if (active == this) active = null;
        module = null;
        screens = null;
    }

    public static KeyMapping openKey() {
        return OPEN_KEY;
    }

    public static void bindHud(PlayerInfoHud hud) {
        playerInfoHud = hud;
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
        var center = active;
        if (center == null) {
            return new AlertScreen(() -> Minecraft.getInstance().setScreen(parent), label("title"), label("unavailable"));
        }
        var page = new ControlCenterScreen(center.settings(hud), center.module, center.screens, ID);
        return center.screens.create(center.module, new UiScreenBuilder(label("title")).parent(parent)
                .dynamicTheme().onInit(page::init).onRender(page::render).onRemoved(screen -> page.removed()));
    }

    private List<SettingsGroup> settings(PlayerInfoHud hud) {
        String playerInfoKey = "hud.sh_common_lib.player_info.";
        var playerInfo = new SettingsGroup(Component.translatable(playerInfoKey + "editor"), GraphicIcon.MONITOR, List.of(
                new SettingsGroup.Toggle(enabled -> Component.translatable(playerInfoKey + (enabled ? "visible" : "hidden")),
                        hud::isEnabled, hud::setEnabled),
                new SettingsGroup.Page(label("edit_position"), GraphicIcon.MOVE, () -> {
                    var client = Minecraft.getInstance();
                    return client.player != null && client.level != null ? null : label("requires_world");
                }, hud::openEditor)));
        var developer = new SettingsGroup(label("developer"), GraphicIcon.DEVELOPMENT, List.of(
                new SettingsGroup.Page(label("developer_settings"), GraphicIcon.SETTINGS,
                        () -> null, this::openDeveloperSettings)));
        var loading = new SettingsGroup(label("loading"), GraphicIcon.TICK, List.of(
                new SettingsGroup.Toggle(enabled -> label(enabled ? "loading_animation_on" : "loading_animation_off"),
                        ClientLoading::animation, ClientLoading::setAnimation),
                new SettingsGroup.Toggle(enabled -> label(enabled ? "loading_profiling_on" : "loading_profiling_off"),
                        ClientLoading::profiling, ClientLoading::setProfiling),
                new SettingsGroup.Page(label("loading_reports"), GraphicIcon.COMPUTER,
                        () -> Files.isDirectory(ClientLoading.reportDirectory()) ? null : label("loading_reports_empty"),
                        (parent, focus) -> Util.getPlatform().openFile(ClientLoading.reportDirectory().toFile()))));
        return List.of(playerInfo,
                hudItems(hud, "hud_player", GraphicIcon.PLAYER, PLAYER, FPS, PING, LOCAL_TIME),
                hudItems(hud, "hud_world", GraphicIcon.MAP, WORLD_TIME, DIMENSION, CHUNKS, ENTITIES, BIOME),
                hudItems(hud, "hud_location", GraphicIcon.PIN, POSITION, CHUNK, LIGHT),
                hudItems(hud, "hud_server", GraphicIcon.TICK, TPS, MSPT),
                hudItems(hud, "hud_computer", GraphicIcon.COMPUTER, MEMORY, CPU, GPU, DISK),
                loading,
                developer);
    }

    private static SettingsGroup hudItems(PlayerInfoHud hud, String group, GraphicIcon icon, PlayerInfoItem... items) {
        List<SettingsGroup.Entry> entries = Arrays.stream(items).map(item ->
                (SettingsGroup.Entry) new SettingsGroup.Toggle(enabled -> Component.translatable(
                        "screen.sh_common_lib.control_center.hud_item",
                        label("hud_item." + item.id()), label(enabled ? "item_shown" : "item_hidden")),
                        () -> hud.shows(item), enabled -> hud.setVisible(item, enabled))).toList();
        return new SettingsGroup(label(group), icon, entries, true);
    }

    private void openDeveloperSettings(Screen parent, Supplier<? extends GuiEventListener> returnFocus) {
        var builder = new UiScreenBuilder(label("developer_settings")).parent(parent).returnFocus(returnFocus)
                .dynamicTheme().onForeground((screen, frame) -> {
                    var viewport = frame.viewport();
                    if (viewport.width() <= 16) return;
                    var font = Minecraft.getInstance().font;
                    var lines = font.split(label("developer_empty"), viewport.width() - 16);
                    int y = viewport.y() + Math.max(0, (viewport.height() - lines.size() * font.lineHeight) / 2);
                    int center = viewport.x() + viewport.width() / 2;
                    for (var line : lines) {
                        frame.graphics().drawString(font, line, center - font.width(line) / 2, y,
                                DarkModeTheme.secondaryTextColor(), false);
                        y += font.lineHeight;
                    }
                });
        screens.create(module, builder).open();
    }

    static Component label(String name) {
        return Component.translatable("screen.sh_common_lib.control_center." + name);
    }
}
