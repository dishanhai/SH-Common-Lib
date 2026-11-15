// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.item;

import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes.Mode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.ToIntFunction;

public final class CosmicArtisanMining {
    private static final int CHAIN_RADIUS = 8;
    private static final int CHAIN_LIMIT = 64;
    private static final ThreadLocal<Boolean> CHAINING = new ThreadLocal<>();
    private static final Map<ServerPlayer, Pending> PENDING =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<WeakHashMap<ItemStack, Boolean>> HARVEST_TOOLS =
            ThreadLocal.withInitial(WeakHashMap::new);

    private CosmicArtisanMining() {}

    public static void markHarvest(ItemStack copy) {
        HARVEST_TOOLS.get().put(copy, Boolean.TRUE);
    }

    public static boolean takeHarvestMark(ItemStack copy) {
        var tools = HARVEST_TOOLS.get();
        boolean marked = tools.remove(copy) != null;
        if (tools.isEmpty()) HARVEST_TOOLS.remove();
        return marked;
    }

    public static void afterBreak(ServerPlayer player, BlockPos pos, BlockState state, ToIntFunction<ItemStack> read) {
        var tool = player.getMainHandItem();
        if (Boolean.TRUE.equals(CHAINING.get()) || player.isCreative() || player.isSpectator()
                || !CosmicArtisanModes.isActive(tool) || !Mode.CHAIN_MINING.enabled(read.applyAsInt(tool))) return;
        var level = (ServerLevel) player.level();
        PENDING.putIfAbsent(player, new Pending(level, pos.immutable(), state, tool));
    }

    public static void tick(ServerPlayer player, ToIntFunction<ItemStack> read) {
        var work = PENDING.remove(player);
        if (work == null) return;
        var level = work.level();
        var origin = work.pos();
        var state = work.state();
        var tool = work.tool();
        if (!player.isAlive() || player.level() != level || player.getMainHandItem() != tool
                || !CosmicArtisanModes.isActive(tool) || !Mode.CHAIN_MINING.enabled(read.applyAsInt(tool))
                || !level.hasChunkAt(origin) || level.getBlockState(origin).is(state.getBlock())) return;
        chain(player, level, origin, state.getBlock());
    }

    private static void chain(ServerPlayer player, ServerLevel level, BlockPos origin,
                              net.minecraft.world.level.block.Block block) {
        var pending = new ArrayDeque<BlockPos>();
        var seen = new HashSet<BlockPos>();
        pending.add(origin);
        seen.add(origin);
        int broken = 1;
        CHAINING.set(true);
        try {
            while (!pending.isEmpty() && broken < CHAIN_LIMIT) {
                BlockPos current = pending.removeFirst();
                for (Direction direction : Direction.values()) {
                    BlockPos next = current.relative(direction);
                    if (!origin.closerThan(next, CHAIN_RADIUS + 1) || !seen.add(next)
                            || !level.hasChunkAt(next) || !level.getWorldBorder().isWithinBounds(next)
                            || !level.mayInteract(player, next)) continue;
                    if (level.getBlockState(next).is(block) && player.gameMode.destroyBlock(next)) {
                        pending.addLast(next);
                        if (++broken == CHAIN_LIMIT) break;
                    }
                }
            }
        } finally {
            CHAINING.remove();
        }
    }

    public static List<ItemStack> smelt(List<ItemStack> drops, Function<ItemStack, ItemStack> output) {
        var result = new ArrayList<ItemStack>(drops.size());
        for (ItemStack drop : drops) {
            ItemStack cooked = output.apply(drop);
            if (cooked.isEmpty()) {
                result.add(drop);
                continue;
            }
            long remaining = (long) drop.getCount() * cooked.getCount();
            while (remaining > 0) {
                int count = (int) Math.min(remaining, cooked.getMaxStackSize());
                result.add(cooked.copyWithCount(count));
                remaining -= count;
            }
        }
        return result;
    }

    private record Pending(ServerLevel level, BlockPos pos, BlockState state, ItemStack tool) {}
}
