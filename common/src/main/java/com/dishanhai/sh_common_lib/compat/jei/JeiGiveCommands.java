// Author: dishanhai
package com.dishanhai.sh_common_lib.compat.jei;

import com.mojang.logging.LogUtils;
import mezz.jei.common.Internal;
import mezz.jei.common.config.GiveMode;
import mezz.jei.common.network.packets.PacketGiveItemStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** JEI inventory give requests with its server authorization and vanilla creative fallback. */
public final class JeiGiveCommands {
    private static boolean compatible = true;
    private JeiGiveCommands() {}

    public static boolean enabled() {
        if (!compatible || JeiShortcuts.runtime() == null || Minecraft.getInstance().player == null) return false;
        try { return Internal.getClientToggleState().isCheatItemsEnabled(); }
        catch (LinkageError error) { disable(error); return false; }
    }

    public static boolean give(ItemStack stack, int amount) {
        if (!enabled() || stack.isEmpty() || amount < 1 || stack.getMaxStackSize() < 1) return false;
        try {
            var connection = Internal.getServerConnection();
            if (connection.isJeiOnServer()) {
                // Real buckets remain separate, valid stacks even when the request asks for sixteen.
                while (amount > 0) {
                    int count = Math.min(amount, stack.getMaxStackSize());
                    var batch = stack.copy();
                    batch.setCount(count);
                    connection.sendPacketToServer(new PacketGiveItemStack(batch, GiveMode.INVENTORY));
                    amount -= count;
                }
                return true;
            }
            return giveCreative(stack, amount);
        } catch (LinkageError error) { disable(error); return false; }
    }

    private static boolean giveCreative(ItemStack stack, int amount) {
        var client = Minecraft.getInstance();
        var player = client.player;
        if (player == null || !player.isCreative() || client.gameMode == null) return false;
        var inventory = player.getInventory();
        // Merge before using empty slots. Update the client snapshot as each slot is assigned.
        for (int pass = 0; pass < 2 && amount > 0; pass++) {
            for (int slot = 0; slot < 36 && amount > 0; slot++) {
                var current = inventory.getItem(slot);
                if (pass == 0 ? current.isEmpty() || !sameMaterial(current, stack) : !current.isEmpty()) continue;
                int existing = current.getCount();
                int count = Math.min(amount, stack.getMaxStackSize() - existing);
                if (count <= 0) continue;
                var replacement = stack.copy();
                replacement.setCount(existing + count);
                inventory.setItem(slot, replacement);
                client.gameMode.handleCreativeModeItemAdd(replacement.copy(), slot < 9 ? slot + 36 : slot);
                amount -= count;
            }
        }
        while (amount > 0) {
            int count = Math.min(amount, stack.getMaxStackSize());
            var batch = stack.copy();
            batch.setCount(count);
            client.gameMode.handleCreativeModeItemAdd(batch, -1);
            amount -= count;
        }
        return true;
    }

    private static boolean sameMaterial(ItemStack first, ItemStack second) {
        var a = first.copy();
        var b = second.copy();
        a.setCount(1);
        b.setCount(1);
        return ItemStack.matches(a, b);
    }

    public static void feedback(String key, boolean failure, Object... arguments) {
        Minecraft.getInstance().gui.setOverlayMessage(Component.translatable("message.sh_common_lib.jei." + key, arguments)
                .withStyle(failure ? ChatFormatting.RED : ChatFormatting.GRAY), false);
    }

    private static void disable(LinkageError error) {
        compatible = false;
        LogUtils.getLogger().warn("Disabling JEI give shortcuts because the installed JEI API is incompatible", error);
    }
}
