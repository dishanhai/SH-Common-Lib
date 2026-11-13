// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.announcement;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.kernel.api.ConfigCodec;
import com.dishanhai.sh_better_ui.kernel.api.ConfigKey;
import com.dishanhai.sh_better_ui.kernel.api.ConfigLayer;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.UIConfigCenter;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant;
import com.dishanhai.sh_common_lib.client.ClientPanels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.CommonComponents;
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

    public static boolean available() { return configs != null; }


    private static Preset preset() { return configs == null ? PRESET.defaultValue() : configs.get(PRESET); }

    static boolean solid() { return preset() == Preset.SOLID; }

    static void drawPanel(GuiGraphicsExtractor frame, Bounds area) {
        switch (preset()) {
            case LIQUID -> ClientPanels.surface(frame, area, GlassVariant.REGULAR, 0xB018202C);
            case CLEAR -> ClientPanels.surface(frame, area, GlassVariant.CLEAR, 0x6418202C);
            case SOLID -> ClientPanels.surface(frame, area, null, 0xFF18202C);
        }
    }

    public static void open(Screen parent, Supplier<? extends GuiEventListener> returnFocus) {
        if (!available()) return;
        var page = new AppearancePage(configs);
        new AnnouncementScreen(label("appearance"), parent, returnFocus, page::init, page::render).open();
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

        private void init(AnnouncementScreen screen) {
            buttons.clear();
            var area = content(viewport(screen));
            int index = 0;
            for (var choice : Preset.values()) {
                var row = row(area, index++);
                var button = screen.button(row.x(), row.y(), row.width(), row.height(), choice.label(), () -> select(choice));
                button.setTooltip(Tooltip.create(choice.label()));
                button.setSampleBackground(false);
                buttons.put(choice, button);
            }
            var back = row(area, Preset.values().length + 1);
            screen.button(back.x(), back.y(), back.width(), back.height(), CommonComponents.GUI_BACK, screen::onClose);
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
                settings.flush().whenComplete((ignored, failure) -> AnnouncementPlatform.schedule(Minecraft.getInstance(), () -> {
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

        private void render(AnnouncementScreen screen, GuiGraphicsExtractor frame) {
            var area = content(viewport(screen));
            drawPanel(frame, area);
            var statusArea = row(area, buttons.size());
            var font = screen.getFont();
            var text = net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(label(status), statusArea.width()));
            frame.centeredText(font, text, statusArea.x() + statusArea.width() / 2,
                    statusArea.y() + (statusArea.height() - font.lineHeight) / 2, ClientPanels.TEXT);
        }

        private static Bounds row(Bounds area, int index) {
            int height = Math.min(ROW, Math.max(1, (area.height() - 2 * PADDING - 4 * GAP) / 5));
            return new Bounds(area.x() + PADDING, area.y() + PADDING + index * (height + GAP),
                    Math.max(1, area.width() - 2 * PADDING), height);
        }

        private static Bounds viewport(AnnouncementScreen screen) {
            return new Bounds(0, 0, screen.width, screen.height);
        }

        private static Bounds content(Bounds viewport) {
            int width = Math.max(1, Math.min(280, viewport.width() - 2 * PADDING));
            int height = Math.max(1, Math.min(142, viewport.height() - 2 * PADDING));
            return new Bounds(viewport.x() + (viewport.width() - width) / 2,
                    viewport.y() + (viewport.height() - height) / 2, width, height);
        }
    }
}
