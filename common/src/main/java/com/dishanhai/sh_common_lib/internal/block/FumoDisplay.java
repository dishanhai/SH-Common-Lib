// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** The server owns the single stack displayed by a placed Fumo. */
public final class FumoDisplay {
    private FumoDisplay() {}

    public static boolean use(Level level, BlockPos pos, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) return false;
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DishanhaiFumoBlock)
                || !(level.getBlockEntity(pos) instanceof DishanhaiFumoBlock.DanceEntity plush)) return false;

        boolean removing = player.isShiftKeyDown();
        ItemStack previous = plush.heldItem();
        ItemStack inHand = player.getItemInHand(hand);
        if (removing ? previous.isEmpty() && !state.getValue(DishanhaiFumoBlock.HOLDING) : inHand.isEmpty()) return false;

        if (level instanceof ServerLevel server) {
            if (!server.mayInteract(player, pos)) return false;
            ItemStack displayed = removing ? ItemStack.EMPTY : inHand.split(1);
            plush.setHeldItem(displayed);
            plush.setChanged();
            var next = state.setValue(DishanhaiFumoBlock.HOLDING, !displayed.isEmpty());
            if (next == state) {
                server.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
            } else {
                server.setBlock(pos, next, Block.UPDATE_CLIENTS);
            }
            if (!previous.isEmpty()) player.getInventory().placeItemBackInInventory(previous);
        }
        return true;
    }

    static void drop(DishanhaiFumoBlock.DanceEntity plush) {
        ItemStack displayed = plush.heldItem();
        if (plush.getLevel() instanceof ServerLevel server && !displayed.isEmpty()) {
            BlockPos pos = plush.getBlockPos();
            plush.setHeldItem(ItemStack.EMPTY);
            Containers.dropItemStack(server, pos.getX(), pos.getY(), pos.getZ(), displayed);
        }
    }
}
