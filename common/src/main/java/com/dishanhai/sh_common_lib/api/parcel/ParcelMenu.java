package com.dishanhai.sh_common_lib.api.parcel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.stream.IntStream;

final class ParcelMenu extends AbstractContainerMenu {
    static final int WIDTH = 176;
    static final int HEIGHT = 180;
    private static final int CRATE_SLOTS = Parcels.MAX_STACKS;

    private final int heldSlot;
    private final ItemStack parcel;

    static int heldSlot(Inventory inventory, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? inventory.selected : Inventory.SLOT_OFFHAND;
    }

    static SimpleMenuProvider provider(int heldSlot, ItemStack parcel) {
        return new SimpleMenuProvider((id, inventory, player) -> new ParcelMenu(id, inventory, heldSlot, parcel),
                Component.translatable("menu.sh_common_lib.sealed_crate"));
    }

    static ParcelMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf buffer) {
        return new ParcelMenu(id, inventory, buffer.readVarInt(), null);
    }

    private ParcelMenu(int id, Inventory inventory, int heldSlot, ItemStack parcel) {
        super(ParcelStorage.menuType(), id);
        this.heldSlot = heldSlot;
        this.parcel = parcel;

        SimpleContainer storage = new SimpleContainer(CRATE_SLOTS);
        if (parcel != null) {
            List<ItemStack> contents = ParcelStorage.read(parcel);
            for (int index = 0; index < Math.min(contents.size(), CRATE_SLOTS); index++) {
                storage.setItem(index, contents.get(index));
            }
            storage.addListener(ignored -> {
                if (stillValid(inventory.player)) {
                    ParcelStorage.write(parcel, IntStream.range(0, CRATE_SLOTS).mapToObj(storage::getItem).toList());
                    inventory.setChanged();
                }
            });
        }

        for (int index = 0; index < CRATE_SLOTS; index++) {
            addSlot(new Slot(storage, index, 62 + index % 3 * 18, 23 + index / 3 * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return Parcels.canPack(stack);
                }
            });
        }
        for (int index = 9; index < Inventory.INVENTORY_SIZE; index++) {
            addSlot(new Slot(inventory, index, 8 + index % 9 * 18, 98 + (index / 9 - 1) * 18));
        }
        for (int index = 0; index < 9; index++) {
            addSlot(index == heldSlot
                    ? new HeldSlot(inventory, index, 8 + index * 18, 156)
                    : new Slot(inventory, index, 8 + index * 18, 156));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return parcel == null || (!player.isSpectator() && player.getInventory().getItem(heldSlot) == parcel
                && parcel.getCount() == 1 && !Parcels.isSealed(parcel));
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (!stillValid(player) || (type == ClickType.SWAP && button == heldSlot)
                || (slot >= 0 && slot < slots.size() && isLocked(slots.get(slot)))) return;
        super.clicked(slot, button, type, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!stillValid(player) || !slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;

        ItemStack source = slot.getItem();
        ItemStack original = source.copy();
        boolean moved = index < CRATE_SLOTS
                ? moveItemStackTo(source, CRATE_SLOTS, slots.size(), true)
                : Parcels.canPack(source) && moveItemStackTo(source, 0, CRATE_SLOTS, false);
        if (!moved) return ItemStack.EMPTY;

        if (source.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, source);
        return original;
    }

    boolean isLocked(Slot slot) {
        return slot instanceof HeldSlot;
    }

    private static final class HeldSlot extends Slot {
        private HeldSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
