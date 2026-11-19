// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

abstract class FumoItemEntity extends BlockEntity {
    private static final String HELD_ITEM = "held_item";
    private ItemStack heldItem = ItemStack.EMPTY;

    FumoItemEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    final ItemStack heldItem() {
        return heldItem;
    }

    final void setHeldItem(ItemStack stack) {
        heldItem = stack;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveCustomOnly(registries);
        // NeoForge 1.21.1 skips an empty update tag.
        if (heldItem.isEmpty()) tag.put(HELD_ITEM, new CompoundTag());
        return tag;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        heldItem = ItemStack.parseOptional(registries, tag.getCompound(HELD_ITEM));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!heldItem.isEmpty()) tag.put(HELD_ITEM, heldItem.save(registries));
    }
}
