// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.announcement;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_common_lib.client.ClientPanels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Keeps the announcement state with its screen while native widgets are rebuilt. */
final class AnnouncementScreen extends Screen {
    private final Screen parent;
    private final Supplier<? extends GuiEventListener> returnFocus;
    private final Consumer<AnnouncementScreen> layout;
    private final Painter painter;

    AnnouncementScreen(Component title, Screen parent, Supplier<? extends GuiEventListener> returnFocus,
                       Consumer<AnnouncementScreen> layout, Painter painter) {
        super(title);
        this.parent = parent;
        this.returnFocus = returnFocus;
        this.layout = layout;
        this.painter = painter;
    }

    void open() {
        Minecraft.getInstance().setScreen(this);
    }

    @Override
    protected void init() {
        layout.accept(this);
    }

    void rebuild(Runnable restoreFocus) {
        // Finish dispatch to the old widget before rebuilding its focus path.
        AnnouncementPlatform.schedule(minecraft, () -> {
            if (minecraft.screen != this) return;
            rebuildWidgets();
            restoreFocus.run();
        });
    }

    <T extends AbstractWidget> T add(T widget) {
        return addRenderableWidget(widget);
    }

    UiButton button(int x, int y, int width, int height, Component label, Runnable action) {
        return add(ClientPanels.button(x, y, width, height, label, ignored -> action.run()));
    }

    void openLink(URI uri) {
        if (minecraft.screen != this || !minecraft.options.chatLinks().get()) return;
        if (!minecraft.options.chatLinksPrompt().get()) {
            Util.getPlatform().openUri(uri);
            return;
        }
        minecraft.setScreen(new ConfirmLinkScreen(confirmed -> {
            if (confirmed) Util.getPlatform().openUri(uri);
            minecraft.setScreen(this);
            children().stream().filter(AnnouncementText.class::isInstance).findFirst().ifPresent(this::setFocused);
        }, uri.toString(), false));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        painter.draw(this, graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (getFocused() instanceof AnnouncementText text && text.mouseReleased(event)) {
            setDragging(false);
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
        if (minecraft.screen == parent) ClientPanels.restoreFocus(parent, returnFocus);
    }

    @FunctionalInterface
    interface Painter {
        void draw(AnnouncementScreen screen, GuiGraphicsExtractor graphics);
    }
}
