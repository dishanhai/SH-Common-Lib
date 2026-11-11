package com.dishanhai.sh_common_lib.api.parcel;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import com.dishanhai.sh_common_lib.api.client.ItemContentPreview;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

final class ParcelClientHooks {
    private static final long DOUBLE_CLICK_MS = 250;

    private Pending pending;

    static void registerModel() {
        ItemProperties.register(ParcelStorage.item(), ResourceLocation.fromNamespaceAndPath(ShCommonLib.MOD_ID, "sealed"),
                (stack, level, entity, seed) -> Parcels.isSealed(stack) ? 1 : 0);
    }

    static void registerPreview() {
        ItemContentPreview.register(Parcels::isParcel, (stack, limit) -> {
            String hint = Parcels.isSealed(stack) ? "sealed_hint" : "draft_hint";
            return new ItemContentPreview.Preview(
                    Component.translatable("tooltip.sh_common_lib.sealed_crate.contents").withStyle(ChatFormatting.GOLD),
                    List.of(Component.translatable("tooltip.sh_common_lib.sealed_crate." + hint).withStyle(ChatFormatting.GRAY)),
                    Parcels.contents(stack).stream().map(ItemContentPreview.Entry::of).toList());
        });
    }

    boolean mousePressed(Screen screen, int button, double mouseX, double mouseY,
                         Consumer<OpenParcelRequest> send) {
        Pending previous = pending;
        pending = null;
        if (button != GLFW.GLFW_MOUSE_BUTTON_MIDDLE || !(screen instanceof AbstractContainerScreen<?> inventory)
                || !(screen instanceof InventoryScreen
                     || screen instanceof CreativeModeInventoryScreen creative && creative.isInventoryOpen())
                || !inventory.getMenu().getCarried().isEmpty()) return false;

        var player = Minecraft.getInstance().player;
        Slot slot = inventory.getSlotUnderMouse();
        if (player == null || player.isSpectator() || slot == null || slot.container != player.getInventory()) return false;
        double localX = mouseX - inventory.getGuiLeft() - slot.x;
        double localY = mouseY - inventory.getGuiTop() - slot.y;
        if (localX < 0 || localX >= 16 || localY < 0 || localY >= 16) return false;

        // Creative inventory wrappers use a menu slot index, not a player inventory index.
        int index = slot.getContainerSlot();
        if (inventory instanceof CreativeModeInventoryScreen) {
            if (index < 0 || index >= player.inventoryMenu.slots.size()) return false;
            index = player.inventoryMenu.getSlot(index).getContainerSlot();
        }
        if (!OpenParcelRequest.isEligibleSlot(index)) return false;

        ItemStack stack = slot.getItem();
        if (stack.getCount() != 1 || !Parcels.isParcel(stack)) return false;
        UUID sealId = ParcelStorage.sealId(stack);
        if (sealId == null) return false;

        OpenParcelRequest request = new OpenParcelRequest(index, sealId);
        long now = Util.getMillis();
        if (previous != null && previous.screen == screen && previous.request.equals(request)
                && now - previous.time <= DOUBLE_CLICK_MS) {
            send.accept(request);
        } else {
            pending = new Pending(screen, request, now);
        }
        return true;
    }

    void reset() {
        pending = null;
    }

    private record Pending(Screen screen, OpenParcelRequest request, long time) {}
}
