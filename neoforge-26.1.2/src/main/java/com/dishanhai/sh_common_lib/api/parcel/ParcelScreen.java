package com.dishanhai.sh_common_lib.api.parcel;

import com.dishanhai.sh_common_lib.client.ClientPanels;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

final class ParcelScreen extends AbstractContainerScreen<ParcelMenu> {
    ParcelScreen(ParcelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, ParcelMenu.WIDTH, ParcelMenu.HEIGHT);
        inventoryLabelY = 85;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        ClientPanels.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            ClientPanels.slot(graphics, x, y);
            if (menu.isLocked(slot)) graphics.outline(x, y, 18, 18, 0xFFD7A763);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, 0xFFF7F7FA, true);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFF7F7FA, true);
    }
}
