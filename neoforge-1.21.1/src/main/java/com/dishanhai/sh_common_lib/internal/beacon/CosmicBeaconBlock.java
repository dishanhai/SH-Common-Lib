// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.beacon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.function.BiConsumer;

public final class CosmicBeaconBlock extends Block {
    private final BiConsumer<ServerPlayer, BeaconPackets.Reply> send;

    public CosmicBeaconBlock(Properties properties, BiConsumer<ServerPlayer, BeaconPackets.Reply> send) {
        super(properties.strength(4f).lightLevel(state -> 12));
        this.send = send;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel server && placer != null) BeaconDirectory.placed(server, pos, placer.getUUID());
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!next.is(this) && level instanceof ServerLevel server) BeaconDirectory.removed(server, pos);
        super.onRemove(state, level, pos, next, moving);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (player.isSpectator()) return InteractionResult.PASS;
        if (level instanceof ServerLevel server && player instanceof ServerPlayer user) {
            send.accept(user, BeaconDirectory.editor(user, server, pos));
        }
        return InteractionResult.SUCCESS;
    }
}
