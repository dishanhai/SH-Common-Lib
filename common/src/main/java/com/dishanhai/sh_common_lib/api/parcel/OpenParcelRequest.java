package com.dishanhai.sh_common_lib.api.parcel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

record OpenParcelRequest(int slot, UUID sealId) {
    static boolean isEligibleSlot(int slot) {
        return (slot >= 0 && slot < Inventory.INVENTORY_SIZE) || slot == Inventory.SLOT_OFFHAND;
    }

    static OpenParcelRequest read(FriendlyByteBuf buffer) {
        return new OpenParcelRequest(buffer.readVarInt(), buffer.readUUID());
    }

    void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(slot);
        buffer.writeUUID(sealId);
    }

    void handle(ServerPlayer player) {
        if (player.isSpectator() || !player.isAlive() || player.containerMenu != player.inventoryMenu
                || !player.inventoryMenu.getCarried().isEmpty() || !isEligibleSlot(slot)) return;
        ItemStack parcel = player.getInventory().getItem(slot);
        if (Parcels.isParcel(parcel) && sealId.equals(ParcelStorage.sealId(parcel))
                && ParcelItem.unpack(player, parcel)) {
            player.inventoryMenu.broadcastChanges();
        }
    }
}
