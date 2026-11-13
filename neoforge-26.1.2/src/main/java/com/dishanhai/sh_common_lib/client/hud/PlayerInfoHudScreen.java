package com.dishanhai.sh_common_lib.client.hud;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_common_lib.client.ClientPanels;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.locale.Language;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Supplier;

import static com.dishanhai.sh_common_lib.client.hud.PlayerInfoHud.LANG;
import static com.dishanhai.sh_common_lib.client.hud.PlayerInfoHudSettings.*;

final class PlayerInfoHudScreen extends Screen {
    private static final Component HINT = Component.translatable(LANG + "hint");
    private static final Component SHORT_HINT = Component.translatable(LANG + "hint_short");
    private static final Component SAVE_FAILED = Component.translatable(LANG + "save_failed");
    private static final int TOOLBAR_HEIGHT = 60;
    private final PlayerInfoHud hud;
    private final PlayerInfoHudLayout layout;
    private final PlayerInfoHudSettings initial;
    private final Screen parent;
    private final Supplier<? extends GuiEventListener> returnFocus;
    private boolean enabled;
    private double x;
    private double y;
    private int scalePercent;
    private boolean saveFailed;
    private Preview preview;
    private AbstractSliderButton scaleSlider;
    private List<Button> buttons;
    private int toolbarY;

    PlayerInfoHudScreen(PlayerInfoHud hud, PlayerInfoHudSettings settings, PlayerInfoHudLayout layout,
                        Screen parent, Supplier<? extends GuiEventListener> returnFocus) {
        super(Component.translatable(LANG + "editor"));
        this.hud = hud;
        this.initial = settings;
        this.layout = layout;
        this.parent = parent;
        this.returnFocus = returnFocus;
        enabled = settings.enabled();
        x = settings.x();
        y = settings.y();
        scalePercent = settings.scalePercent();
    }

    @Override
    protected void init() {
        setDragging(false);
        toolbarY = Math.max(0, height - TOOLBAR_HEIGHT);
        preview = new Preview(fitPreview());
        preview.visible = toolbarY > 8;
        int buttonWidth = Math.min(92, Math.max(1, (width - 20) / 4));
        int gap = 4;
        int buttonX = (width - buttonWidth * 4 - gap * 3) / 2;
        int sliderWidth = Math.min(220, width - 16);
        scaleSlider = sliderWidth > 8 ? ShBetterUiApi.controls().slider(
                new Bounds((width - sliderWidth) / 2, toolbarY + 15, sliderWidth, 18),
                MIN_SCALE, MAX_SCALE, SCALE_STEP, scalePercent, UiPreferences.configured(),
                value -> Component.translatable(LANG + "scale", value), value -> {
                    scalePercent = value;
                    preview.updatePlacement();
                }) : null;
        Button toggle = button(buttonX, buttonWidth, toggleLabel(), button -> {
            enabled = !enabled;
            button.setMessage(toggleLabel());
        });
        Button reset = button(buttonX + buttonWidth + gap, buttonWidth, Component.translatable(LANG + "reset"), ignored -> {
            x = PlayerInfoHudSettings.DEFAULT.x();
            y = PlayerInfoHudSettings.DEFAULT.y();
            scalePercent = PlayerInfoHudSettings.DEFAULT.scalePercent();
            rebuildWidgets();
        });
        Button cancel = button(buttonX + 2 * (buttonWidth + gap), buttonWidth, CommonComponents.GUI_CANCEL,
                ignored -> onClose());
        Button done = button(buttonX + 3 * (buttonWidth + gap), buttonWidth, CommonComponents.GUI_DONE, ignored -> {
            saveFailed = !hud.save(initial.withLayout(enabled, x, y, scalePercent));
            if (saveFailed) {
                minecraft.getNarrator().saySystemNow(SAVE_FAILED);
            } else {
                onClose();
            }
        });
        buttons = List.of(toggle, reset, cancel, done);
        if (scaleSlider != null) addWidget(scaleSlider);
        buttons.forEach(this::addWidget);
        addWidget(preview);
        for (Button button : buttons) button.setY(toolbarY + 36);
        setInitialFocus(preview.visible ? preview : done);
    }

    private PlayerInfoHudLayout.View fitPreview() {
        return layout.fit(width, height, toolbarY, scalePercent, minecraft.getWindow().getGuiScale(), hud.pageIndex());
    }

    private Button button(int x, int width, Component label, Button.OnPress action) {
        return ClientPanels.button(x, 0, width, 20, label, action);
    }

    private Component toggleLabel() {
        return Component.translatable(enabled ? LANG + "visible" : LANG + "hidden");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x44000000);
        preview.extractRenderState(graphics, mouseX, mouseY, partialTick);
        ClientPanels.panel(graphics, 2, toolbarY, Math.max(1, width - 4), TOOLBAR_HEIGHT);
        Component message = saveFailed ? SAVE_FAILED : HINT;
        if (!saveFailed && font.width(message) > width - 12) message = SHORT_HINT;
        var fitted = Language.getInstance().getVisualOrder(font.substrByWidth(message, Math.max(1, width - 12)));
        graphics.centeredText(font, fitted, width / 2, toolbarY + 3,
                saveFailed ? 0xFFFF8888 : ClientPanels.TEXT);
        if (scaleSlider != null) scaleSlider.extractRenderState(graphics, mouseX, mouseY, partialTick);
        for (Button button : buttons) button.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        // Only the toolbar controls can start an interaction within the toolbar.
        if (mouseY >= toolbarY && mouseY < toolbarY + TOOLBAR_HEIGHT
                && buttons.stream().noneMatch(widget -> widget.isMouseOver(mouseX, mouseY))
                && (scaleSlider == null || !scaleSlider.isMouseOver(mouseX, mouseY))) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        // The default dispatch only releases the widget currently under the pointer.
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && preview.dragging) {
            setDragging(false);
            return preview.mouseReleased(event);
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && scaleSlider != null && getFocused() == scaleSlider && isDragging()) {
            setDragging(false);
            return scaleSlider.mouseReleased(event);
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
        ClientPanels.restoreFocus(parent, returnFocus);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private final class Preview extends AbstractWidget {
        private PlayerInfoHudLayout.View view;
        private HudPanel.Placement placement;
        private boolean dragging;
        private double grabX;
        private double grabY;

        Preview(PlayerInfoHudLayout.View view) {
            super(view.placement().x(x), view.placement().y(y), view.placement().width(),
                    view.placement().height(), getTitle());
            this.view = view;
            placement = view.placement();
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            view.panel().draw(graphics, font, getX(), getY(), placement.scale());
            view.drawSeparators(graphics, font, getX(), getY());
            if (dragging || isFocused() || isHovered()) {
                int border = dragging ? ClientPanels.ACCENT : ClientPanels.TEXT;
                graphics.outline(getX(), getY(), getWidth(), getHeight(), border);
            }
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            dragging = true;
            grabX = event.x() - getX();
            grabY = event.y() - getY();
        }

        @Override
        protected void onDrag(MouseButtonEvent event, double deltaX, double deltaY) {
            if (dragging) moveTo(event.x() - grabX, event.y() - grabY);
        }

        @Override
        public void onRelease(MouseButtonEvent event) {
            if (dragging) moveTo(event.x() - grabX, event.y() - grabY);
            dragging = false;
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (dragging) return false;
            int step = event.hasShiftDown() ? 10 : 1;
            switch (event.key()) {
                case GLFW.GLFW_KEY_LEFT -> x = placement.moveX(getX() - step, x);
                case GLFW.GLFW_KEY_RIGHT -> x = placement.moveX(getX() + step, x);
                case GLFW.GLFW_KEY_UP -> y = placement.moveY(getY() - step, y);
                case GLFW.GLFW_KEY_DOWN -> y = placement.moveY(getY() + step, y);
                default -> { return false; }
            }
            updatePosition();
            return true;
        }

        private void moveTo(double left, double top) {
            x = placement.moveX(left, x);
            y = placement.moveY(top, y);
            updatePosition();
        }

        private void updatePosition() {
            setX(placement.x(x));
            setY(placement.y(y));
        }

        private void updatePlacement() {
            view = fitPreview();
            placement = view.placement();
            setWidth(placement.width());
            setHeight(placement.height());
            updatePosition();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
            if (view.hidden() > 0) {
                output.add(NarratedElementType.HINT, Component.translatable(LANG + "space_limited", view.hidden()));
            }
            output.add(NarratedElementType.USAGE, HINT);
        }
    }
}
