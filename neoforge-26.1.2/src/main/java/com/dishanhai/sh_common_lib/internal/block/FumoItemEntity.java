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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

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
        return saveCustomOnly(registries);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        heldItem = input.read(HELD_ITEM, ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!heldItem.isEmpty()) output.store(HELD_ITEM, ItemStack.CODEC, heldItem);
    }
}
