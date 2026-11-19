// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.fumo;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/** The standard Fumo reward roll shared by all variants on a server. */
public final class FumoRewards {
    public static final String POOL_RESOURCE = FumoRewardDefinition.RESOURCE_ID;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int COOLDOWN_SECONDS = 15;
    private static final float REWARD_CHANCE = 0.20F;
    private static final FumoRewardPool<Item> DEFAULT_POOL = FumoRewardPool.uniform(List.of(
            Items.APPLE, Items.BREAD, Items.COAL, Items.COPPER_INGOT, Items.IRON_INGOT,
            Items.GOLD_INGOT, Items.REDSTONE, Items.LAPIS_LAZULI, Items.PAPER, Items.TORCH));
    private static final Map<MinecraftServer, Cache<UUID, Boolean>> COOLDOWNS = new WeakHashMap<>();
    private static volatile FumoRewardPool<Item> pool = DEFAULT_POOL;

    private FumoRewards() {}

    /** Rolls once per player per cooldown. Call on the server thread for an accepted interaction. */
    public static void tryGive(ServerLevel level, Player player) {
        Cache<UUID, Boolean> cooldowns = cooldowns(level.getServer());
        if (cooldowns.asMap().putIfAbsent(player.getUUID(), Boolean.TRUE) != null) return;

        var random = level.getRandom();
        if (random.nextFloat() < REWARD_CHANCE) {
            Item reward = pool.choose(random::nextInt);
            if (reward != null) player.getInventory().placeItemBackInInventory(new ItemStack(reward));
        }
    }

    /** Installs a complete pool snapshot on each server data reload. */
    public static SimplePreparableReloadListener<?> reloadListener(
            Path configDirectory, Function<ResourceManager, Optional<Resource>> findPack,
            Function<String, Item> resolveItem) {
        Path config = configDirectory.resolve(FumoRewardDefinition.CONFIG_PATH);
        Function<String, Item> findReward = id -> {
            Item item = resolveItem.apply(id);
            return item == Items.AIR ? null : item;
        };
        return new SimplePreparableReloadListener<FumoRewardPool<Item>>() {
            @Override
            protected FumoRewardPool<Item> prepare(ResourceManager manager, ProfilerFiller profiler) {
                var pack = findPack.apply(manager);
                if (pack.isPresent()) {
                    try (var input = pack.get().open()) {
                        return FumoRewardPool.read(input, findReward);
                    } catch (IOException | RuntimeException error) {
                        LOGGER.warn("Invalid Fumo reward pool {} from {}: {}", POOL_RESOURCE,
                                pack.get().sourcePackId(), error.getMessage());
                    }
                }
                try (var input = Files.newInputStream(config)) {
                    return FumoRewardPool.read(input, findReward);
                } catch (NoSuchFileException missing) {
                    return DEFAULT_POOL;
                } catch (IOException | RuntimeException error) {
                    LOGGER.warn("Invalid Fumo reward pool {}: {}", config, error.getMessage());
                    return DEFAULT_POOL;
                }
            }

            @Override
            protected void apply(FumoRewardPool<Item> rewards, ResourceManager manager, ProfilerFiller profiler) {
                pool = rewards;
            }
        };
    }

    public static void resetPool() {
        pool = DEFAULT_POOL;
    }

    private static synchronized Cache<UUID, Boolean> cooldowns(MinecraftServer server) {
        return COOLDOWNS.computeIfAbsent(server, ignored -> CacheBuilder.newBuilder()
                .expireAfterWrite(COOLDOWN_SECONDS, TimeUnit.SECONDS)
                .build());
    }
}
