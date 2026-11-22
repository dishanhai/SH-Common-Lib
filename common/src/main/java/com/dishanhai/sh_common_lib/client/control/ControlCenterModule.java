// Author: dishanhai
package com.dishanhai.sh_common_lib.client.control;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiExtensionRegistry;
import com.dishanhai.sh_better_ui.client.api.UiScreenBuilder;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ServiceDescriptor;
import com.dishanhai.sh_common_lib.api.ulk.settings.AssistantSettingsProvider;
import com.dishanhai.sh_common_lib.api.client.ulk.UnifiedLibUiService;
import com.dishanhai.sh_common_lib.api.ulk.UnifiedLibKernel;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.ScreenService;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UIModule;
import com.dishanhai.sh_better_ui.kernel.api.UIPermission;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_better_ui.kernel.api.VersionRange;
import com.dishanhai.sh_common_lib.api.ShCommonLib;
import com.dishanhai.sh_common_lib.api.client.announcement.AnnouncementAppearance;
import com.dishanhai.sh_common_lib.client.hud.PlayerInfoHud;
import com.dishanhai.sh_common_lib.client.hud.PlayerInfoItem;
import com.dishanhai.sh_common_lib.client.hud.HudGroups;
import com.dishanhai.sh_common_lib.client.loading.ClientLoading;
import com.dishanhai.sh_common_lib.client.jei.JeiSearchPreferences;
import com.dishanhai.sh_common_lib.internal.item.ArtisanConfig;
import com.dishanhai.sh_common_lib.client.item.ArtisanAppearancePreferences;
import com.dishanhai.sh_common_lib.client.item.PhoneScreen;
import com.dishanhai.sh_common_lib.client.voice.VoiceItemClient;
import com.dishanhai.sh_common_lib.client.voice.VoiceItemPreferences;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Supplier;

import static com.dishanhai.sh_common_lib.client.hud.PlayerInfoItem.*;

public final class ControlCenterModule implements UIModule {
    private static final UiId ID = new UiId(ShCommonLib.MOD_ID, "control_center");
    private static final KeyMapping OPEN_KEY = new KeyMapping("key.sh_common_lib.control_center",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F10, "key.categories.sh_common_lib");
    private static final KeyMapping VOICE_KEY = new KeyMapping(VoiceItemClient.KEY,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F4, "key.categories.sh_common_lib");
    private static final VoiceItemClient VOICE = new VoiceItemClient(VOICE_KEY,
            player -> player.hasPermissions(2));
    private static ControlCenterModule active;
    private static PlayerInfoHud playerInfoHud;
    private static ControlCenterScreen openPage;

    private ModuleContext module;
    private ScreenService screens;
    private UiExtensionRegistry.Registration assistantSettings;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder().id(ID).version(UiVersion.parse("1.0.0"))
                .requiredCapability(UIApis.NATIVE_SCREEN, VersionRange.ANY)
                .requiredCapability(UIApis.NATIVE_HUD, VersionRange.ANY)
                .requestPermission(UIPermission.REGISTER_RENDER_PASS)
                .build();
    }

    @Override
    public void register(ModuleContext context) {
        context.services().register(new ServiceDescriptor<>(UnifiedLibUiService.KEY, UiVersion.parse("1.0.0"),
                ServiceDescriptor.Visibility.PUBLIC, 0, false), UnifiedLibKernel::inspect);
    }

    @Override
    public void start(ModuleContext context) {
        screens = context.engine().require(UIApis.SCREENS);
        context.engine().require(UIApis.HUD).mount(context, frame -> {
            if (playerInfoHud != null) playerInfoHud.render(frame);
        });
        AnnouncementAppearance.start(context);
        PhoneScreen.bindNotes(context);
        assistantSettings = ShBetterUiApi.extensions().register("sh_common_lib:assistant_settings",
                Supplier.class, ignored -> new AssistantSettingsProvider());
        module = context;
        active = this;
    }

    @Override
    public void stop(ModuleContext context) {
        if (assistantSettings != null) assistantSettings.close();
        assistantSettings = null;
        AnnouncementAppearance.stop(context);
        PhoneScreen.bindNotes(null);
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

    public static KeyMapping voiceKey() { return VOICE_KEY; }

    public static void tick(PlayerInfoHud hud) {
        playerInfoHud = hud;
        VOICE.tick();
        if (!OPEN_KEY.consumeClick()) return;
        while (OPEN_KEY.consumeClick()) {
            // Consume repeated presses before opening the screen once.
        }
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.screen == null && client.getOverlay() == null) {
            client.setScreen(create(null, hud));
        }
    }

    public static void openFromPhone(Screen parent) {
        if (playerInfoHud != null) Minecraft.getInstance().setScreen(create(parent, playerInfoHud));
    }

    public static Screen create(Screen parent, PlayerInfoHud hud) {
        var center = active;
        if (center == null) {
            return new AlertScreen(() -> Minecraft.getInstance().setScreen(parent), label("title"), label("unavailable"));
        }
        var draft = new ControlCenterDraft(hud.settings(), ClientLoading.animation(), ClientLoading.profiling(),
                JeiSearchPreferences.current(), ArtisanConfig.profiles(), ArtisanControlSettings.editable(),
                ArtisanAppearancePreferences.targetColors(), VoiceItemPreferences.current());
        var page = new ControlCenterScreen(center.settings(hud, draft), center.module, center.screens, ID, hud, draft);
        var guard = page.returnGuard(parent);
        var screen = center.screens.create(center.module, new UiScreenBuilder(label("title")).parent(guard)
                .backButton(false).dynamicTheme()
                .onInit(screenOwner -> { page.init(screenOwner); openPage = page; })
                .onRender(page::render).onForeground(page::foreground).onRemoved(ignored -> {
                    if (openPage == page) openPage = null;
                    page.removed();
                }));
        guard.setControlCenter(screen);
        page.captureParent(screen);
        return screen;
    }

    public static boolean handleScreenKey(Screen screen, int key, int scanCode) {
        return openPage != null && openPage.handleKey(screen, key, scanCode);
    }

    private List<SettingsGroup> settings(PlayerInfoHud hud, ControlCenterDraft draft) {
        String playerInfoKey = "hud.sh_common_lib.player_info.";
        List<SettingsGroup.Entry> hudEntries = new ArrayList<>(List.of(
                new SettingsGroup.Toggle(enabled -> Component.translatable(playerInfoKey + (enabled ? "visible" : "hidden")),
                        () -> draft.hud().enabled(), draft::setHudEnabled),
                new SettingsGroup.Page(label("edit_position"), GraphicIcon.MOVE, () -> {
                    var client = Minecraft.getInstance();
                    return client.player != null && client.level != null ? null : label("requires_world");
                }, hud::openEditor)));
        hudEntries.add(new SettingsGroup.Toggle(enabled -> label(enabled ? "hud_titles_on" : "hud_titles_off"),
                () -> draft.hud().showGroupTitles(), draft::setHudTitles));
        hudEntries.add(new SettingsGroup.Toggle(enabled -> label(enabled ? "hud_separators_on" : "hud_separators_off"),
                () -> draft.hud().showGroupSeparators(), draft::setHudSeparators));
        List<PlayerInfoItem> items = List.of(PLAYER, FPS, PING, LOCAL_TIME, HEALTH, FOOD, ARMOR, EXPERIENCE, AIR, EFFECTS,
                WORLD_TIME, WEATHER, MOON_PHASE, DIMENSION, CHUNKS, ENTITIES, BIOME, POSITION, CHUNK, LIGHT,
                TPS, MSPT, MEMORY, CPU, GPU, DISK, CONNECTION_TRAFFIC, NETWORK_TRAFFIC, SYSTEM_VERSION, JAVA_VERSION,
                MINECRAFT_VERSION, hud.loaderVersionItem(), SHADER_PACK, MOD_STATUS, AE2_STATUS, AE2_CHANNELS, AE2_POWER,
                NEOECO_NETWORK, NEOECO_CRAFTING, NEOECO_COMPUTATION, NEOECO_STORAGE, NEOECO_WORKSTATION,
                ULK_SUMMARY, ULK_SOURCES, FTB_QUESTS, FTB_TEAM, FTB_CHUNKS, FTB_CHUNK_INFO);
        for (var group : HudGroups.Group.values()) {
            if (group == HudGroups.Group.CONTROLS) continue;
            var title = label("hud_group." + group.name().toLowerCase(java.util.Locale.ROOT));
            var toggle = new SettingsGroup.Toggle(enabled -> Component.translatable(
                    "screen.sh_common_lib.control_center.hud_group_toggle", title,
                    label(enabled ? "item_shown" : "item_hidden")),
                    () -> draft.hud().showsGroup(group), enabled -> draft.setHudGroup(group, enabled),
                    () -> hud.available(group));
            var children = items.stream().filter(item -> HudGroups.group(item) == group)
                    .map(item -> (SettingsGroup.Entry) hudItem(hud, draft, item)).toList();
            hudEntries.add(new SettingsGroup.Subgroup(title, toggle, children));
        }
        var playerInfo = new SettingsGroup(Component.translatable(playerInfoKey + "editor"), GraphicIcon.MONITOR,
                hudEntries, true);
        var developer = new SettingsGroup(label("developer"), GraphicIcon.DEVELOPMENT, List.of(
                new SettingsGroup.Toggle(enabled -> label(enabled ? "voice_items_on" : "voice_items_off"),
                        () -> draft.voiceItems().enabled(), draft::setVoiceItems),
                new SettingsGroup.Toggle(enabled -> label(enabled ? "voice_fuzzy_on" : "voice_fuzzy_off"),
                        () -> draft.voiceItems().fuzzy(), draft::setVoiceFuzzy, () -> draft.voiceItems().enabled()),
                new SettingsGroup.Page(com.dishanhai.sh_common_lib.client.recipe.RecipeEditorAccess.title(), GraphicIcon.DEVELOPMENT,
                        com.dishanhai.sh_common_lib.client.recipe.RecipeEditorAccess::unavailable,
                        com.dishanhai.sh_common_lib.client.recipe.RecipeEditorAccess::open),
                new SettingsGroup.Page(label("developer_settings"), GraphicIcon.SETTINGS,
                        com.dishanhai.sh_common_lib.client.recipe.RecipeEditorAccess::unavailable,
                        com.dishanhai.sh_common_lib.client.recipe.RecipeEditorAccess::openDeveloperSettings)));
        var loading = new SettingsGroup(label("loading"), GraphicIcon.TICK, List.of(
                new SettingsGroup.Toggle(enabled -> label(enabled ? "loading_animation_on" : "loading_animation_off"),
                        draft::animation, draft::setAnimation),
                new SettingsGroup.Toggle(enabled -> label(enabled ? "loading_profiling_on" : "loading_profiling_off"),
                        draft::profiling, draft::setProfiling),
                new SettingsGroup.Page(label("loading_reports"), GraphicIcon.COMPUTER,
                        () -> Files.isDirectory(ClientLoading.reportDirectory()) ? null : label("loading_reports_empty"),
                        (parent, focus) -> Util.getPlatform().openFile(ClientLoading.reportDirectory().toFile()))));
        var announcements = new SettingsGroup(Component.translatable("sh_common_lib.announcement.appearance"),
                GraphicIcon.SETTINGS, List.of(new SettingsGroup.Page(
                Component.translatable("sh_common_lib.announcement.appearance.choose"), GraphicIcon.SETTINGS,
                () -> null, AnnouncementAppearance::open)));
        var jei = new SettingsGroup.Subgroup(label("jei_extension"), List.of(
                new SettingsGroup.Heading(label("jei_rendering")),
                new SettingsGroup.Toggle(enabled -> label(enabled ? "jei_render_optimization_on" : "jei_render_optimization_off"),
                        () -> draft.jei().optimizeRendering(), draft::setJeiOptimizeRendering),
                new SettingsGroup.Heading(label("jei_search")),
                new SettingsGroup.Toggle(enabled -> label(enabled ? "jei_enabled_on" : "jei_enabled_off"), () -> draft.jei().enabled(), draft::setJeiEnabled),
                new SettingsGroup.Toggle(enabled -> label(enabled ? "jei_prediction_on" : "jei_prediction_off"), () -> draft.jei().prediction(), draft::setJeiPrediction),
                new SettingsGroup.Toggle(enabled -> label(enabled ? "jei_history_on" : "jei_history_off"), () -> draft.jei().history(), draft::setJeiHistory),
                new SettingsGroup.Toggle(enabled -> label(enabled ? "jei_hud_on" : "jei_hud_off"), () -> draft.jei().hud(), draft::setJeiHud),
                new SettingsGroup.Toggle(enabled -> label(enabled ? "jei_preview_on" : "jei_preview_off"), () -> draft.jei().preview(), draft::setJeiPreview),
                new SettingsGroup.Toggle(enabled -> label(enabled ? "jei_hints_on" : "jei_hints_off"), () -> draft.jei().hints(), draft::setJeiHints),
                new SettingsGroup.Toggle(enabled -> label(enabled ? "jei_ae2_orderable_on" : "jei_ae2_orderable_off"),
                        () -> draft.jei().ae2Orderable(), draft::setJeiAe2Orderable),
                new SettingsGroup.Toggle(enabled -> label(enabled ? "jei_glass_on" : "jei_glass_off"), () -> draft.jei().glass(), draft::setJeiGlass),
                new SettingsGroup.Toggle(enabled -> label(enabled ? "jei_random_on" : "jei_random_off"), () -> draft.jei().randomSource(), draft::setJeiRandom),
                new SettingsGroup.Toggle(enabled -> label(enabled ? "jei_quest_on" : "jei_quest_off"), () -> draft.jei().questSource(), draft::setJeiQuest),
                new SettingsGroup.Toggle(enabled -> label(enabled ? "jei_chat_on" : "jei_chat_off"), () -> draft.jei().chatSource(), draft::setJeiChat),
                new SettingsGroup.Toggle(enabled -> label(enabled ? "jei_hot_on" : "jei_hot_off"), () -> draft.jei().hotSource(), draft::setJeiHot),
                new SettingsGroup.Toggle(enabled -> label(enabled ? "jei_hot_share_on" : "jei_hot_share_off"), () -> draft.jei().hotShare(), draft::setJeiHotShare),
                new SettingsGroup.Page(label("jei_provider"), GraphicIcon.SEARCH,
                        () -> com.dishanhai.sh_common_lib.client.jei.JeiSearchProvider.installed() ? null : label("jei_provider_unavailable"),
                        ControlCenterModule::chooseJeiProvider),
                new SettingsGroup.Page(label("jei_clear_history"), GraphicIcon.RESET, () -> null, ControlCenterModule::clearJeiHistory)));
        var modExtensions = new SettingsGroup(label("mod_extensions"), GraphicIcon.SETTINGS, List.of(jei), true);
        return List.of(playerInfo, loading, modExtensions, ArtisanControlSettings.group(draft), announcements, developer);
    }

    private static void clearJeiHistory(Screen parent, Supplier<? extends GuiEventListener> focus) {
        var client = Minecraft.getInstance();
        Runnable back = () -> {
            client.setScreen(parent);
            var widget = focus.get();
            if (widget != null && parent.children().contains(widget)) parent.setFocused(widget);
        };
        client.setScreen(new ConfirmScreen(confirmed -> {
            if (!confirmed || JeiSearchPreferences.clearHistory()) back.run();
            else client.setScreen(new AlertScreen(back, label("jei_clear_failed"), label("jei_clear_failed_detail")));
        }, label("jei_clear_title"), label("jei_clear_detail"), label("jei_clear_action"), CommonComponents.GUI_CANCEL));
    }

    private static void chooseJeiProvider(Screen parent, Supplier<? extends GuiEventListener> focus) {
        com.dishanhai.sh_common_lib.client.jei.JeiSearchProvider.open(parent, () -> {
            Minecraft.getInstance().setScreen(parent);
            var widget = focus.get();
            if (widget != null && parent.children().contains(widget)) parent.setFocused(widget);
        });
    }

    private static SettingsGroup.Toggle hudItem(PlayerInfoHud hud, ControlCenterDraft draft, PlayerInfoItem item) {
        return new SettingsGroup.Toggle(enabled -> Component.translatable(
                "screen.sh_common_lib.control_center.hud_item",
                label("hud_item." + item.id()), label(enabled ? "item_shown" : "item_hidden")),
                () -> draft.hud().shows(item), enabled -> draft.setHudItem(item, enabled),
                () -> hud.available(item));
    }

    static Component label(String name) {
        return Component.translatable("screen.sh_common_lib.control_center." + name);
    }
}
