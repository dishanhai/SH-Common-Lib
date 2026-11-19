// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.trash;

import com.dishanhai.sh_common_lib.api.ulk.trash.TrashCan.Extension;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

final class TrashCanMenu extends AbstractContainerMenu {
    static final int WIDTH = 194;
    static final int HEIGHT = 152;
    static final Extension[] EXTENSIONS = Extension.values();
    private final TrashCanBlockEntity entity;

    static SimpleMenuProvider provider(TrashCanBlockEntity entity) {
        return new SimpleMenuProvider((id, inventory, player) -> new TrashCanMenu(id, inventory, entity),
                Component.translatable("block.sh_common_lib.trash_can"));
    }

    TrashCanMenu(int id, Inventory inventory) {
        this(id, inventory, null);
    }

    private TrashCanMenu(int id, Inventory inventory, TrashCanBlockEntity entity) {
        super(TrashCanPlatform.MENU.get(), id);
        this.entity = entity;
        var storage = entity == null ? new SimpleContainer(EXTENSIONS.length) : entity.storage;
        for (var extension : EXTENSIONS) {
            addSlot(new Slot(storage, extension.ordinal(), 17 + extension.ordinal() * 48, 25) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return TrashCanPlatform.available(extension) && TrashCanModuleItem.matches(stack, extension);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        for (int index = 9; index < Inventory.INVENTORY_SIZE; index++) {
            addSlot(new Slot(inventory, index, 17 + index % 9 * 18, 69 + (index / 9 - 1) * 18));
        }
        for (int index = 0; index < 9; index++) {
            addSlot(new Slot(inventory, index, 17 + index * 18, 127));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return entity == null || (!entity.isRemoved() && player.mayBuild() && !player.isSpectator()
                && player.level() == entity.getLevel()
                && player.distanceToSqr(Vec3.atCenterOf(entity.getBlockPos())) <= 64);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || !stillValid(player)) return ItemStack.EMPTY;
        var slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        var stack = slot.getItem();
        var original = stack.copy();
        int count = EXTENSIONS.length;
        boolean moved = index < count ? moveItemStackTo(stack, count, slots.size(), true)
                : moveItemStackTo(stack, 0, count, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }
}
