// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.compat;

import appeng.api.features.GridLinkables;
import appeng.api.features.IGridLinkableHandler;
import appeng.api.networking.IGrid;
import appeng.core.definitions.AEItems;
import appeng.items.tools.powered.WirelessTerminalItem;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.function.Consumer;

/** Optional AE2 integration for items that store a native wireless access point link. */
public final class Ae2WirelessLinks {
    private Ae2WirelessLinks() {}

    /** Registers an item for the access point's input slot on both logical sides. */
    public static void register(ItemLike item, Consumer<ItemStack> clearLegacy) {
        GridLinkables.register(item, new IGridLinkableHandler() {
            @Override
            public boolean canLink(ItemStack stack) {
                return stack.is(item.asItem());
            }

            @Override
            public void link(ItemStack stack, GlobalPos point) {
                WirelessTerminalItem.LINKABLE_HANDLER.link(stack, point);
                clearLegacy.accept(stack);
            }

            @Override
            public void unlink(ItemStack stack) {
                Ae2WirelessLinks.unlink(stack);
                clearLegacy.accept(stack);
            }
        });
    }

    public static void unlink(ItemStack stack) {
        WirelessTerminalItem.LINKABLE_HANDLER.unlink(stack);
    }

    public static GlobalPos linkedPoint(ItemStack stack) {
        return AEItems.WIRELESS_TERMINAL.asItem().getLinkedPosition(stack);
    }

    /** Resolves an online access point in an already loaded chunk without a player range check. */
    public static IGrid grid(ServerLevel origin, GlobalPos point) {
        var level = origin.getServer().getLevel(point.dimension());
        return level == null ? null : Ae2Inventories.wirelessGrid(level, point.pos());
    }
}
