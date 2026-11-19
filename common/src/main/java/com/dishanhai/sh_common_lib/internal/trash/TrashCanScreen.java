// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.trash;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.glass.GlassSurface;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

final class TrashCanScreen extends AbstractContainerScreen<TrashCanMenu> {
    TrashCanScreen(TrashCanMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = TrashCanMenu.WIDTH;
        imageHeight = TrashCanMenu.HEIGHT;
        titleLabelX = inventoryLabelX = 16;
        inventoryLabelY = 57;
    }

    static void registerBlur() {
        ShBetterUiApi.blur().allowScreen(screen -> screen instanceof TrashCanScreen);
    }

    @Override
    protected void init() {
        super.init();
        var close = ShBetterUiApi.controls().button(new Bounds(leftPos + imageWidth - 51, topPos + 3, 45, 18),
                Component.translatable("gui.done"), ignored -> onClose(), UiPreferences.configured(), false,
                UiStyle.LIQUID_GLASS);
        close.setSampleBackground(false);
        addRenderableWidget(close);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        GlassSurface.smokePanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        for (var slot : menu.slots) {
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            float hover = isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY) ? 1 : 0;
            GlassSurface.smokeControl(graphics, x, y, 18, 18, hover);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFF7F7FA, true);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFF7F7FA, true);
        for (var extension : TrashCanMenu.EXTENSIONS) {
            var slot = menu.slots.get(extension.ordinal());
            var label = Component.translatable(extension.labelKey());
            int color = TrashCanPlatform.available(extension) ? 0xFFF7F7FA : 0xFFB5C0CD;
            graphics.drawCenteredString(font, label, slot.x + 8, 45, color);
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        if (hoveredSlot == null || hoveredSlot.hasItem() || hoveredSlot.index >= TrashCanMenu.EXTENSIONS.length) return;
        var extension = TrashCanMenu.EXTENSIONS[hoveredSlot.index];
        var tooltip = Component.translatable(TrashCanPlatform.available(extension)
                ? extension.labelKey() + ".hint" : "screen.sh_common_lib.trash_can.unavailable");
        graphics.renderTooltip(font, tooltip, mouseX, mouseY);
    }
}
