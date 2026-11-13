// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.announcement;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.api.UiScreenBuilder;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.display.UiLayout.Horizontal;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.kernel.api.ConfigCodec;
import com.dishanhai.sh_better_ui.kernel.api.ConfigKey;
import com.dishanhai.sh_better_ui.kernel.api.ConfigLayer;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UIConfigCenter;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static com.dishanhai.sh_common_lib.api.client.announcement.FullscreenAnnouncements.label;

/** Client appearance shared by announcement readers and release history. */
public final class AnnouncementAppearance {
    private enum Preset {
        LIQUID("liquid"), CLEAR("clear"), SOLID("solid");

        private final String key;

        Preset(String key) { this.key = key; }

        Component label() { return FullscreenAnnouncements.label("appearance." + key); }
    }

    private static final ConfigKey<Preset> PRESET = new ConfigKey<>(new UiId("sh_common_lib", "announcement_appearance"),
            Preset.class, Preset.LIQUID, Set.of(ConfigLayer.USER_GLOBAL), ConfigKey.Mutability.RUNTIME,
            ConfigKey.Persistence.USER_GLOBAL, 1, "sh_common_lib:announcement_appearance", value -> true,
            UnaryOperator.identity(), Map.of());
    private static ModuleContext module;
    private static UIConfigCenter configs;

    private AnnouncementAppearance() {}

    /** Called by the client control center's kernel module. */
    public static void start(ModuleContext context) {
        var settings = context.engine().configs();
        settings.define(PRESET);
        settings.persist(PRESET, ConfigCodec.enumeration(Preset.class));
        configs = settings;
        module = context;
    }

    public static void stop(ModuleContext context) {
        if (module != context) return;
        module = null;
        configs = null;
    }

    static boolean available() { return configs != null; }

    static UiScreen create(UiScreenBuilder builder) {
        builder.themeBackground().dynamicTheme(theme -> theme.override(ThemeTokens.LIQUID_GLASS, true));
        return module == null ? builder.build() : module.engine().require(UIApis.SCREENS).create(module, builder);
    }

    private static Preset preset() { return configs == null ? PRESET.defaultValue() : configs.get(PRESET); }

    static boolean solid() { return preset() == Preset.SOLID; }

    static void drawPanel(UiFrame frame, Bounds area) {
        var materials = ShBetterUiApi.materials();
        switch (preset()) {
            case LIQUID -> materials.liquidLayeredPopover(frame.graphics(), area, frame.preferences(), 1f);
            case CLEAR -> materials.liquidToolbar(frame.graphics(), area, frame.preferences());
            case SOLID -> materials.roundedRect(frame.graphics(), area.x(), area.y(), area.width(), area.height(),
                    10, 1, LiquidGlassTheme.accentColor(), DarkModeTheme.surfaceColor() | 0xFF000000);
        }
    }

    public static void open(Screen parent, Supplier<? extends GuiEventListener> returnFocus) {
        if (!available()) return;
        var page = new AppearancePage(configs);
        create(ShBetterUiApi.screen(label("appearance")).parent(parent).returnFocus(returnFocus)
                .onInit(page::init).onRender(page::render)).open();
    }

    private static final class AppearancePage {
        private static final int ROW = 22;
        private static final int GAP = 4;
        private static final int PADDING = 8;
        private final UIConfigCenter settings;
        private final Map<Preset, UiButton> buttons = new EnumMap<>(Preset.class);
        private String status = "appearance.preview";
        private boolean saving;

        AppearancePage(UIConfigCenter settings) { this.settings = settings; }

        private void init(UiScreen screen) {
            buttons.clear();
            var area = content(screen.viewport());
            int index = 0;
            for (var choice : Preset.values()) {
                var row = row(area, index++);
                var button = screen.button(row.x(), row.y(), row.width(), row.height(), choice.label(), () -> select(choice));
                button.setTooltip(Tooltip.create(choice.label()));
                button.setSampleBackground(false);
                buttons.put(choice, button);
            }
            refresh();
            screen.setFocused(buttons.get(preset()));
        }

        private void select(Preset choice) {
            if (saving) return;
            try {
                settings.beginTransaction().set(settings.userGlobal(), PRESET, choice).commit();
                saving = true;
                status = "appearance.saving";
                refresh();
                settings.flush().whenComplete((ignored, failure) -> Minecraft.getInstance().tell(() -> {
                    saving = false;
                    status = failure == null ? "appearance.saved" : "write_failed";
                    refresh();
                }));
            } catch (RuntimeException failure) {
                saving = false;
                status = "write_failed";
                refresh();
            }
        }

        private void refresh() {
            var selected = preset();
            buttons.forEach((choice, button) -> {
                button.setSelected(choice == selected);
                button.setLeadingIcon(choice == selected ? GraphicIcon.SUCCESS : null);
                button.active = !saving;
            });
        }

        private void render(UiScreen screen, UiFrame frame) {
            var area = content(frame.viewport());
            drawPanel(frame, area);
            ShBetterUiApi.graphics().drawText(frame.graphics(), Minecraft.getInstance().font, label(status), row(area, buttons.size()),
                    Horizontal.CENTER, LiquidGlassTheme.textColor(), false, frame.preferences());
        }

        private static Bounds row(Bounds area, int index) {
            int height = Math.min(ROW, Math.max(1, (area.height() - 2 * PADDING - 3 * GAP) / 4));
            return new Bounds(area.x() + PADDING, area.y() + PADDING + index * (height + GAP),
                    Math.max(1, area.width() - 2 * PADDING), height);
        }

        private static Bounds content(Bounds viewport) {
            int width = Math.max(1, Math.min(280, viewport.width() - 2 * PADDING));
            int height = Math.max(1, Math.min(118, viewport.height() - 2 * PADDING));
            return new Bounds(viewport.x() + (viewport.width() - width) / 2,
                    viewport.y() + (viewport.height() - height) / 2, width, height);
        }
    }
}
