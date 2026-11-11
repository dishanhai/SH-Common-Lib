package com.dishanhai.sh_common_lib.api.parcel;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class ParcelItem extends Item {
    ParcelItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack parcel, ItemStack carried, Slot slot,
                                             ClickAction action, Player player, SlotAccess cursor) {
        if (action != ClickAction.SECONDARY || parcel.getCount() != 1 || Parcels.isSealed(parcel)
                || !slot.allowModification(player)) return false;

        List<ItemStack> contents = new ArrayList<>(ParcelStorage.read(parcel));
        if (carried.isEmpty()) {
            if (!contents.isEmpty() && cursor.set(contents.get(contents.size() - 1))) {
                contents.remove(contents.size() - 1);
                ParcelStorage.write(parcel, contents);
            }
        } else if (Parcels.canPack(carried)) {
            if (contents.size() < Parcels.MAX_STACKS) {
                contents.add(carried.copy());
            } else {
                int empty = contents.indexOf(ItemStack.EMPTY);
                if (empty < 0) return true;
                contents.set(empty, carried.copy());
            }
            ParcelStorage.write(parcel, contents);
            carried.setCount(0);
        }
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack parcel = player.getItemInHand(hand);
        if (player.isSpectator() || parcel.getCount() != 1) return InteractionResultHolder.fail(parcel);
        if (Parcels.isSealed(parcel)) {
            // Holding the seal shortcut should never also unpack the crate.
            if (player.isShiftKeyDown()) return InteractionResultHolder.fail(parcel);
            if (!level.isClientSide && !unpack(player, parcel)) return InteractionResultHolder.fail(parcel);
        } else {
            if (!player.isShiftKeyDown()) {
                if (player instanceof ServerPlayer serverPlayer) {
                    ParcelStorage.openEditor(serverPlayer, hand, parcel);
                }
                return InteractionResultHolder.sidedSuccess(parcel, level.isClientSide);
            }
            if (Parcels.contents(parcel).isEmpty()) {
                if (!level.isClientSide) player.displayClientMessage(Component.translatable("item.sh_common_lib.sealed_crate.empty"), true);
                return InteractionResultHolder.fail(parcel);
            }
            if (!level.isClientSide) {
                ParcelStorage.seal(parcel, UUID.randomUUID());
                player.displayClientMessage(Component.translatable("item.sh_common_lib.sealed_crate.sealed"), true);
            }
        }
        // Unpacking can refill the hand slot. Return its current stack to vanilla.
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    static boolean unpack(Player player, ItemStack parcel) {
        if (parcel.getCount() != 1 || !Parcels.isSealed(parcel)) return false;
        List<ItemStack> contents = Parcels.contents(parcel);
        if (contents.isEmpty()) return false;
        parcel.shrink(1);
        for (ItemStack stack : contents) {
            player.getInventory().placeItemBackInInventory(stack);
        }
        player.displayClientMessage(Component.translatable("item.sh_common_lib.sealed_crate.opened"), true);
        return true;
    }
}
