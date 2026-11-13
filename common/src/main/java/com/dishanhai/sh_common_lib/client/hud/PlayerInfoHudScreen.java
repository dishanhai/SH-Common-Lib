package com.dishanhai.sh_common_lib.client.hud;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiHudPanel;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.text.TextFit;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Supplier;

import static com.dishanhai.sh_common_lib.client.hud.PlayerInfoHud.LANG;
import static com.dishanhai.sh_common_lib.client.hud.PlayerInfoHudSettings.*;

final class PlayerInfoHudScreen extends Screen {
    private static final UiId TEXT_SAMPLE = new UiId("sh_common_lib", "player_info_hud_preview");
    private static final Component HINT = Component.translatable(LANG + "hint");
    private static final Component SHORT_HINT = Component.translatable(LANG + "hint_short");
    private static final Component SAVE_FAILED = Component.translatable(LANG + "save_failed");
    private static final TextFit.Scale HINT_SCALE = new TextFit.Scale(1, 1, 1);
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
    private UiSlider scaleSlider;
    private List<UiButton> buttons;
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
        scaleSlider = sliderWidth > 8 ? new UiSlider(new Bounds((width - sliderWidth) / 2, 0, sliderWidth, 18),
                MIN_SCALE, MAX_SCALE, SCALE_STEP, scalePercent, UiPreferences.configured(),
                percent -> Component.translatable(LANG + "scale", percent), percent -> {
                    scalePercent = percent;
                    preview.updatePlacement();
                }) : null;
        UiButton toggle = button(buttonX, buttonWidth, toggleLabel(), button -> {
            enabled = !enabled;
            button.setMessage(toggleLabel());
        });
        UiButton reset = button(buttonX + buttonWidth + gap, buttonWidth, Component.translatable(LANG + "reset"), ignored -> {
            x = PlayerInfoHudSettings.DEFAULT.x();
            y = PlayerInfoHudSettings.DEFAULT.y();
            scalePercent = PlayerInfoHudSettings.DEFAULT.scalePercent();
            if (scaleSlider != null) scaleSlider.setValue(scalePercent);
            preview.updatePlacement();
        });
        UiButton cancel = button(buttonX + 2 * (buttonWidth + gap), buttonWidth, CommonComponents.GUI_CANCEL,
                ignored -> onClose());
        UiButton done = button(buttonX + 3 * (buttonWidth + gap), buttonWidth, CommonComponents.GUI_DONE, ignored -> {
            saveFailed = !hud.save(initial.withLayout(enabled, x, y, scalePercent));
            if (saveFailed) {
                minecraft.getNarrator().sayNow(SAVE_FAILED);
            } else {
                onClose();
            }
        });
        buttons = List.of(toggle, reset, cancel, done);
        if (scaleSlider != null) addWidget(scaleSlider);
        buttons.forEach(this::addWidget);
        addWidget(preview);
        if (scaleSlider != null) scaleSlider.setY(toolbarY + 15);
        for (UiButton button : buttons) button.setY(toolbarY + 36);
        setInitialFocus(preview.visible ? preview : done);
    }

    private PlayerInfoHudLayout.View fitPreview() {
        return layout.fit(width, height, toolbarY, scalePercent, minecraft.getWindow().getGuiScale(), hud.pageIndex());
    }

    private UiButton button(int x, int width, Component label, UiButton.OnPress action) {
        var button = new UiButton(x, 0, width, 20, label, action, UiPreferences.configured(), false, UiStyle.LIQUID_GLASS);
        button.setSampleBackground(false);
        return button;
    }

    private Component toggleLabel() {
        return Component.translatable(enabled ? LANG + "visible" : LANG + "hidden");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x44000000);
        preview.render(graphics, mouseX, mouseY, partialTick);
        var preferences = UiPreferences.configured();
        LiquidGlassTheme.plate(graphics, 2, toolbarY, Math.max(1, width - 4), TOOLBAR_HEIGHT,
                DarkModeTheme.borderColor(), DarkModeTheme.surfaceColor(), preferences);
        Component message = saveFailed ? SAVE_FAILED : HINT;
        if (!saveFailed && font.width(message) > width - 12) message = SHORT_HINT;
        var fitted = ShBetterUiApi.layout().fitText(font, message, Math.max(0, width - 12), font.lineHeight, HINT_SCALE);
        graphics.drawCenteredString(font, fitted.glyphs(), width / 2, toolbarY + 3,
                saveFailed ? 0xFFFF8888 : LiquidGlassTheme.textColor());
        if (scaleSlider != null) scaleSlider.render(graphics, mouseX, mouseY, partialTick);
        for (UiButton button : buttons) button.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Only the toolbar controls can start an interaction within the toolbar.
        if (mouseY >= toolbarY && mouseY < toolbarY + TOOLBAR_HEIGHT
                && buttons.stream().noneMatch(widget -> widget.isMouseOver(mouseX, mouseY))
                && (scaleSlider == null || !scaleSlider.isMouseOver(mouseX, mouseY))) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // The default dispatch only releases the widget currently under the pointer.
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && preview.dragging) {
            setDragging(false);
            return preview.mouseReleased(mouseX, mouseY, button);
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && scaleSlider != null && getFocused() == scaleSlider && isDragging()) {
            setDragging(false);
            return scaleSlider.mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
        if (parent != null && minecraft.screen == parent) {
            var focus = returnFocus.get();
            if (focus != null && parent.children().contains(focus)) parent.setFocused(focus);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private final class Preview extends AbstractWidget {
        private PlayerInfoHudLayout.View view;
        private UiHudPanel.Placement placement;
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
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            view.panel().draw(graphics, font, UiPreferences.configured(), getX(), getY(), placement.scale(), TEXT_SAMPLE);
            view.drawSeparators(graphics, font, getX(), getY(), UiPreferences.configured());
            if (dragging || isFocused() || isHovered()) {
                int border = dragging ? LiquidGlassTheme.accentColor() : LiquidGlassTheme.textColor();
                graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), border);
            }
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            dragging = true;
            grabX = mouseX - getX();
            grabY = mouseY - getY();
        }

        @Override
        protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
            if (dragging) moveTo(mouseX - grabX, mouseY - grabY);
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            if (dragging) moveTo(mouseX - grabX, mouseY - grabY);
            dragging = false;
        }

        @Override
        public boolean keyPressed(int key, int scanCode, int modifiers) {
            if (dragging) return false;
            int step = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? 10 : 1;
            switch (key) {
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
