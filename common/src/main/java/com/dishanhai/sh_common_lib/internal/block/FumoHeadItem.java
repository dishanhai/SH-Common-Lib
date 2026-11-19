// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.block;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** A Fumo block item can also occupy the vanilla head slot. */
public final class FumoHeadItem extends BlockItem {
    public FumoHeadItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public EquipmentSlot getEquipmentSlot(ItemStack stack) {
        return EquipmentSlot.HEAD;
    }
}
