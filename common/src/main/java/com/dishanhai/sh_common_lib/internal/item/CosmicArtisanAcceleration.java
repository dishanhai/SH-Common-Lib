// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.item;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.ObjIntConsumer;

public final class CosmicArtisanAcceleration {
    private static final int DURATION = 600;
    private static final int MAX_RATE = 16;
    private static final Map<ServerPlayer, BlockWork> BLOCKS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ServerPlayer, EntityWork> ENTITIES = Collections.synchronizedMap(new WeakHashMap<>());

    private CosmicArtisanAcceleration() {}

    public static boolean startBlock(Player player, BlockPos pos, BlockState state) {
        if (player instanceof ServerPlayer owner && player.level() instanceof ServerLevel level) {
            var entity = level.getBlockEntity(pos);
            if (!level.mayInteract(player, pos) || (!state.isRandomlyTicking()
                    && (entity == null || state.getTicker(level, entity.getType()) == null))) return false;
            var current = BLOCKS.get(owner);
            int rate = current != null && current.level() == level && current.pos().equals(pos) && current.block() == state.getBlock()
                    ? Math.min(current.rate() * 2, MAX_RATE) : 2;
            BLOCKS.put(owner, new BlockWork(level, pos.immutable(), state.getBlock(), owner.tickCount + DURATION, rate));
            return true;
        }
        return state.isRandomlyTicking() || state.hasBlockEntity();
    }

    public static boolean startEntity(Player player, LivingEntity target) {
        if (!(target instanceof AgeableMob)) return false;
        if (player instanceof ServerPlayer owner) {
            var current = ENTITIES.get(owner);
            int rate = current != null && current.target() == target ? Math.min(current.rate() * 2, MAX_RATE) : 2;
            ENTITIES.put(owner, new EntityWork((AgeableMob) target, owner.tickCount + DURATION, rate));
        }
        return true;
    }

    public static void tick(ServerPlayer player, ObjIntConsumer<AgeableMob> extraTimers) {
        var block = BLOCKS.get(player);
        if (block != null && !tickBlock(player, block)) BLOCKS.remove(player);
        var entity = ENTITIES.get(player);
        if (entity != null && !tickEntity(player, entity, extraTimers)) ENTITIES.remove(player);
    }

    private static boolean tickBlock(ServerPlayer player, BlockWork work) {
        var level = work.level();
        var pos = work.pos();
        if (player.tickCount >= work.until() || player.level() != level || !level.hasChunkAt(pos)) return false;
        for (int i = 0; i < work.rate(); i++) {
            var state = level.getBlockState(pos);
            if (!state.is(work.block())) return false;
            var entity = level.getBlockEntity(pos);
            if (entity != null) {
                if (!tickBlockEntity(level, pos, state, entity)) return false;
            } else if (state.isRandomlyTicking()) {
                state.randomTick(level, pos, level.getRandom());
            } else {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> boolean tickBlockEntity(ServerLevel level, BlockPos pos, BlockState state, T entity) {
        var ticker = state.getTicker(level, (BlockEntityType<T>) entity.getType());
        if (ticker == null || entity.isRemoved()) return false;
        ticker.tick(level, pos, state, entity);
        return true;
    }

    private static boolean tickEntity(ServerPlayer player, EntityWork work, ObjIntConsumer<AgeableMob> extraTimers) {
        var target = work.target();
        if (player.tickCount >= work.until() || !target.isAlive() || player.level() != target.level()) return false;
        int age = target.getAge();
        if (age < 0) target.setAge(Math.min(0, age + work.rate()));
        if (age > 0) target.setAge(Math.max(0, age - work.rate()));
        extraTimers.accept(target, work.rate());
        return true;
    }

    private record BlockWork(ServerLevel level, BlockPos pos, Block block, int until, int rate) {}
    private record EntityWork(AgeableMob target, int until, int rate) {}
}
