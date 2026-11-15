// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.item;

import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes.BossPolicy;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes.Mode;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.ToIntFunction;

public final class CosmicArtisanRelations {
    private static final int RETALIATION_TICKS = 600;
    private static final Map<LivingEntity, Map<UUID, Integer>> PROVOKED = new WeakHashMap<>();

    private CosmicArtisanRelations() {}

    public static void provoke(LivingEntity boss, ServerPlayer player) {
        if (!CosmicBossType.matches(boss.getType())) return;
        var attackers = PROVOKED.computeIfAbsent(boss, ignored -> new HashMap<>());
        attackers.values().removeIf(until -> until <= boss.tickCount);
        attackers.put(player.getUUID(), boss.tickCount + RETALIATION_TICKS);
    }

    public static boolean pacifies(ServerPlayer player, LivingEntity mob, ToIntFunction<ItemStack> flags,
                                   Function<ItemStack, BossPolicy> bossPolicy) {
        boolean boss = CosmicBossType.matches(mob.getType());
        if (!boss && !(mob instanceof Enemy)) return false;
        var policy = carriedPolicy(player, flags, bossPolicy);
        return policy != null && pacifies(player, mob, boss, policy);
    }

    public static void releaseTargets(ServerPlayer player, ToIntFunction<ItemStack> flags,
                                      Function<ItemStack, BossPolicy> bossPolicy) {
        if (player.tickCount % 10 != 0) return;
        var policy = carriedPolicy(player, flags, bossPolicy);
        if (policy == null) return;
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(48),
                mob -> mob.getTarget() == player)) {
            boolean boss = CosmicBossType.matches(mob.getType());
            if ((boss || mob instanceof Enemy) && pacifies(player, mob, boss, policy)) mob.setTarget(null);
        }
    }

    private static boolean pacifies(ServerPlayer player, LivingEntity mob, boolean boss, BossPolicy policy) {
        if (!boss || policy == BossPolicy.FRIENDLY) return true;
        if (policy == BossPolicy.HOSTILE) return false;
        var attackers = PROVOKED.get(mob);
        return attackers == null || attackers.getOrDefault(player.getUUID(), 0) <= mob.tickCount;
    }

    private static BossPolicy carriedPolicy(ServerPlayer player, ToIntFunction<ItemStack> flags,
                                           Function<ItemStack, BossPolicy> bossPolicy) {
        var main = player.getMainHandItem();
        if (enabled(main, flags)) return bossPolicy.apply(main);
        var off = player.getOffhandItem();
        if (enabled(off, flags)) return bossPolicy.apply(off);
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            var stack = inventory.getItem(slot);
            if (enabled(stack, flags)) return bossPolicy.apply(stack);
        }
        return null;
    }

    private static boolean enabled(ItemStack stack, ToIntFunction<ItemStack> flags) {
        return CosmicArtisanModes.isActive(stack) && Mode.HOSTILE_FRIENDLY.enabled(flags.applyAsInt(stack));
    }
}
