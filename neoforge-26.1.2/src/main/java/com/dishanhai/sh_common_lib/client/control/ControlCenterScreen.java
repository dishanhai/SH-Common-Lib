// Author: dishanhai
package com.dishanhai.sh_common_lib.client.control;

import com.dishanhai.sh_common_lib.client.ClientPanels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.dishanhai.sh_common_lib.client.control.ControlCenterModule.label;

final class ControlCenterScreen extends Screen {
    private static final int ROW = 20;
    private static final int GAP = 4;
    private static final List<String> FEATURES = List.of("crate", "jei");

    private final Screen parent;
    private final List<SettingsGroup> groups;
    private final Map<SettingsGroup.Page, Button> pageButtons = new IdentityHashMap<>();
    private SettingsGroup selectedGroup;
    private boolean overview;
    private Component status;
    private int panelX;
    private int panelWidth;
    private int bodyY;
    private int statusY;

    ControlCenterScreen(Screen parent, List<SettingsGroup> groups) {
        super(label("title"));
        this.parent = parent;
        this.groups = List.copyOf(groups);
        selectedGroup = this.groups.getFirst();
    }

    @Override
    protected void init() {
        pageButtons.clear();
        panelWidth = Math.min(360, width - 16);
        panelX = (width - panelWidth) / 2;
        var close = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(panelX, height - ROW - 8, Math.max(1, panelWidth), ROW).build());
        if (panelWidth < 72 || height < 104) {
            setInitialFocus(close);
            return;
        }

        int half = (panelWidth - GAP) / 2;
        addRenderableWidget(Button.builder(label("settings"), button -> show(false))
                .bounds(panelX, 24, half, ROW).build());
        var overviewButton = addRenderableWidget(Button.builder(label("overview"), button -> show(true))
                .bounds(panelX + half + GAP, 24, panelWidth - half - GAP, ROW).build());
        overviewButton.setTooltip(Tooltip.create(label("overview.hint")));
        bodyY = 24 + ROW + GAP;
        if (overview) {
            setInitialFocus(overviewButton);
            return;
        }

        var selector = addRenderableWidget(CycleButton.builder(SettingsGroup::title, selectedGroup)
                .withValues(groups).displayOnlyValue()
                .create(panelX, bodyY, panelWidth, ROW, label("settings"), (button, group) -> {
                    selectedGroup = group;
                    status = null;
                    rebuildWidgets();
                }));
        setInitialFocus(selector);
        int columns = selectedGroup.twoColumns() && panelWidth >= 164 ? 2 : 1;
        int columnWidth = (panelWidth - (columns - 1) * GAP) / columns;
        int firstRow = bodyY + ROW + GAP;
        var entries = selectedGroup.entries();
        int rowCount = (entries.size() + columns - 1) / columns;
        statusY = firstRow + rowCount * (ROW + GAP);
        if (statusY + font.lineHeight > close.getY()) {
            statusY = firstRow;
            return;
        }
        for (int index = 0; index < entries.size(); index++) {
            int x = panelX + index % columns * (columnWidth + GAP);
            int y = firstRow + index / columns * (ROW + GAP);
            switch (entries.get(index)) {
                case SettingsGroup.Toggle toggle -> addRenderableWidget(Button.builder(
                        toggle.label().apply(toggle.read().getAsBoolean()), button -> save(toggle, button))
                        .bounds(x, y, columnWidth, ROW).build());
                case SettingsGroup.Page page -> {
                    var button = addRenderableWidget(Button.builder(page.label(),
                            control -> page.open().accept(this, () -> pageButtons.get(page)))
                            .bounds(x, y, columnWidth, ROW).build());
                    pageButtons.put(page, button);
                    var unavailable = page.unavailable().get();
                    button.active = unavailable == null;
                    if (unavailable != null) button.setTooltip(Tooltip.create(unavailable));
                }
            }
        }
    }

    private void show(boolean overview) {
        this.overview = overview;
        status = null;
        rebuildWidgets();
    }

    private void save(SettingsGroup.Toggle toggle, Button button) {
        boolean saved = toggle.save().test(!toggle.read().getAsBoolean());
        status = label(saved ? "saved" : "save_failed");
        button.setMessage(toggle.label().apply(toggle.read().getAsBoolean()));
        if (!saved) minecraft.getNarrator().saySystemNow(status);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.centeredText(font, title, width / 2, 10, ClientPanels.TEXT);
        if (panelWidth >= 72 && height >= 104) {
            int contentBottom = height - ROW - 12;
            graphics.enableScissor(panelX, bodyY, panelX + panelWidth, contentBottom);
            try {
                if (overview) {
                    int y = bodyY;
                    for (String feature : FEATURES) {
                        graphics.text(font, label(feature), panelX, y, ClientPanels.TEXT);
                        y += font.lineHeight + GAP;
                        var lines = font.split(label(feature + ".details"), panelWidth);
                        for (var line : lines) {
                            graphics.text(font, line, panelX, y, ClientPanels.secondaryText());
                            y += font.lineHeight;
                        }
                        y += GAP * 2;
                    }
                } else if (statusY == bodyY + ROW + GAP) {
                    graphics.textWithWordWrap(font, label("too_small"), panelX, statusY, panelWidth, ClientPanels.TEXT);
                } else if (status != null) {
                    graphics.textWithWordWrap(font, status, panelX, statusY, panelWidth, ClientPanels.TEXT);
                }
            } finally {
                graphics.disableScissor();
            }
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    static void openDeveloperSettings(Screen parent, Supplier<? extends GuiEventListener> returnFocus) {
        var client = Minecraft.getInstance();
        client.setScreen(new Screen(label("developer_settings")) {
            @Override
            protected void init() {
                setInitialFocus(addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> onClose())
                        .bounds((width - 120) / 2, height - ROW - 8, 120, ROW).build()));
            }

            @Override
            public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
                graphics.centeredText(font, title, width / 2, 10, ClientPanels.TEXT);
                graphics.textWithWordWrap(font, label("developer_empty"), 12, Math.max(24, height / 2 - 8),
                        Math.max(1, width - 24), ClientPanels.secondaryText());
                super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            }

            @Override
            public void onClose() {
                client.setScreen(parent);
                var focus = returnFocus.get();
                if (focus != null && parent.children().contains(focus)) parent.setFocused(focus);
            }
        });
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
