// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.trash;

import com.dishanhai.sh_common_lib.client.ClientPanels;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

final class TrashCanScreen extends AbstractContainerScreen<TrashCanMenu> {
    TrashCanScreen(TrashCanMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, TrashCanMenu.WIDTH, TrashCanMenu.HEIGHT);
        titleLabelX = inventoryLabelX = 16;
        inventoryLabelY = 57;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(ClientPanels.button(leftPos + imageWidth - 51, topPos + 3, 45, 18,
                Component.translatable("gui.done"), ignored -> onClose()));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ClientPanels.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        for (var slot : menu.slots) {
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            if (slot.index < TrashCanMenu.EXTENSIONS.length) {
                ClientPanels.surface(graphics, new Bounds(x, y, 18, 18), GlassVariant.REGULAR, 0xD0303B4B);
            } else {
                ClientPanels.slot(graphics, x, y);
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, 0xFFF7F7FA, true);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFF7F7FA, true);
        for (var extension : TrashCanMenu.EXTENSIONS) {
            var slot = menu.slots.get(extension.ordinal());
            var label = Component.translatable(extension.labelKey());
            int color = TrashCanPlatform.available(extension) ? 0xFFF7F7FA : 0xFFB5C0CD;
            graphics.centeredText(font, label, slot.x + 8, 45, color);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (hoveredSlot == null || hoveredSlot.hasItem() || hoveredSlot.index >= TrashCanMenu.EXTENSIONS.length) return;
        var extension = TrashCanMenu.EXTENSIONS[hoveredSlot.index];
        var tooltip = Component.translatable(TrashCanPlatform.available(extension)
                ? extension.labelKey() + ".hint" : "screen.sh_common_lib.trash_can.unavailable");
        graphics.setTooltipForNextFrame(font, tooltip, mouseX, mouseY);
    }
}
