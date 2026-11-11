package com.dishanhai.sh_common_lib.api.parcel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.glass.GlassSurface;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

final class ParcelScreen extends AbstractContainerScreen<ParcelMenu> {
    ParcelScreen(ParcelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = ParcelMenu.WIDTH;
        imageHeight = ParcelMenu.HEIGHT;
        inventoryLabelY = 85;
    }

    static void registerBlur() {
        ShBetterUiApi.blur().allowScreen(screen -> screen instanceof ParcelScreen);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        GlassSurface.smokePanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            GlassSurface.smokeControl(graphics, x, y, 18, 18, 0);
            if (menu.isLocked(slot)) graphics.renderOutline(x, y, 18, 18, 0xFFD7A763);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFF7F7FA, true);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFF7F7FA, true);
    }
}
