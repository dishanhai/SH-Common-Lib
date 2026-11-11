package com.dishanhai.sh_common_lib.client.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
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

final class PlayerInfoHudScreen extends Screen {
    private static final Component HINT = Component.translatable(LANG + "hint");
    private static final Component SAVE_FAILED = Component.translatable(LANG + "save_failed");
    private static final int TOOLBAR_HEIGHT = 40;
    private final PlayerInfoHud hud;
    private final PlayerInfoHud.Panel panel;
    private final Screen parent;
    private final Supplier<? extends GuiEventListener> returnFocus;
    private boolean enabled;
    private double x;
    private double y;
    private boolean saveFailed;
    private Preview preview;
    private List<Button> buttons;
    private int toolbarY;

    PlayerInfoHudScreen(PlayerInfoHud hud, PlayerInfoHudSettings settings, PlayerInfoHud.Panel panel,
                        Screen parent, Supplier<? extends GuiEventListener> returnFocus) {
        super(Component.translatable(LANG + "editor"));
        this.hud = hud;
        this.panel = panel;
        this.parent = parent;
        this.returnFocus = returnFocus;
        enabled = settings.enabled();
        x = settings.x();
        y = settings.y();
    }

    @Override
    protected void init() {
        setDragging(false);
        preview = new Preview(panel.fit(width, height));
        int buttonWidth = Math.min(92, Math.max(1, (width - 20) / 4));
        int gap = 4;
        int buttonX = (width - buttonWidth * 4 - gap * 3) / 2;
        Button toggle = Button.builder(toggleLabel(), button -> {
            enabled = !enabled;
            button.setMessage(toggleLabel());
        }).bounds(buttonX, 0, buttonWidth, 20).build();
        Button reset = Button.builder(Component.translatable(LANG + "reset"), button -> {
            x = PlayerInfoHudSettings.DEFAULT.x();
            y = PlayerInfoHudSettings.DEFAULT.y();
            preview.updatePosition();
        }).bounds(buttonX + buttonWidth + gap, 0, buttonWidth, 20).build();
        Button cancel = Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(buttonX + 2 * (buttonWidth + gap), 0, buttonWidth, 20).build();
        Button done = Button.builder(CommonComponents.GUI_DONE, button -> {
            saveFailed = !hud.save(new PlayerInfoHudSettings(enabled, x, y));
            if (saveFailed) {
                minecraft.getNarrator().sayNow(SAVE_FAILED);
            } else {
                onClose();
            }
        }).bounds(buttonX + 3 * (buttonWidth + gap), 0, buttonWidth, 20).build();
        buttons = List.of(toggle, reset, cancel, done);
        buttons.forEach(this::addWidget);
        addWidget(preview);
        updateToolbar();
        setInitialFocus(preview);
    }

    private Component toggleLabel() {
        return Component.translatable(enabled ? LANG + "visible" : LANG + "hidden");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x44000000);
        preview.render(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, toolbarY, width, toolbarY + TOOLBAR_HEIGHT, 0xF0101010);
        Component message = saveFailed ? SAVE_FAILED : HINT;
        graphics.drawCenteredString(font, message, width / 2, toolbarY + 3, saveFailed ? 0xFF8888 : 0xFFFFFF);
        for (Button button : buttons) button.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // The default dispatch only releases the widget currently under the pointer.
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && preview.dragging) {
            setDragging(false);
            return preview.mouseReleased(mouseX, mouseY, button);
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

    private void updateToolbar() {
        int bottom = height - TOOLBAR_HEIGHT;
        toolbarY = preview.getY() + preview.getHeight() > bottom && preview.getY() >= TOOLBAR_HEIGHT ? 0 : bottom;
        for (Button button : buttons) button.setY(toolbarY + 16);
    }

    private final class Preview extends AbstractWidget {
        private final PlayerInfoHud.Placement placement;
        private boolean dragging;
        private double grabX;
        private double grabY;

        Preview(PlayerInfoHud.Placement placement) {
            super(placement.x(x), placement.y(y), placement.width(), placement.height(), getTitle());
            this.placement = placement;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            panel.draw(graphics, font, getX(), getY(), placement.scale());
            int border = dragging ? 0xFFFFCC66 : (isFocused() || isHovered() ? 0xFFFFFFFF : 0xFF9AA8B6);
            graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), border);
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
            updateToolbar();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
            output.add(NarratedElementType.USAGE, HINT);
        }
    }
}
