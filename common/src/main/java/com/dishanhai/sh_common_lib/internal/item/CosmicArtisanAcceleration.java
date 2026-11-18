// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.item;

import com.dishanhai.sh_common_lib.api.ulk.acceleration.AccelerationPackets;
import com.dishanhai.sh_common_lib.internal.acceleration.AccelerationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;

/** Handheld entry point; acceleration is scheduled by the server-wide manager. */
public final class CosmicArtisanAcceleration {
    private static Platform platform;

    private CosmicArtisanAcceleration() {}

    public static void install(Platform implementation) { platform = implementation; }

    public static boolean startBlock(Player player, BlockPos pos, BlockState state) {
        if (!(player instanceof ServerPlayer owner)) return state.hasBlockEntity() || state.isRandomlyTicking();
        return platform != null && AccelerationManager.startBlock(owner, pos, state, platform);
    }

    public static boolean startEntity(Player player, Entity target) {
        if (target instanceof Player) return false;
        if (!(player instanceof ServerPlayer owner)) return true;
        return platform != null && AccelerationManager.startEntity(owner, target, platform);
    }

    /** Called before other right-click actions so placement and list editing remain exclusive. */
    public static boolean handleBlock(Player player, BlockPos pos) {
        return player instanceof ServerPlayer owner && platform != null
                && AccelerationManager.handleBlock(owner, pos, platform);
    }

    public static boolean startWorld(Player player) {
        return player instanceof ServerPlayer owner && platform != null && AccelerationManager.startWorld(owner, platform);
    }

    public static boolean isEditing(Player player) {
        return player instanceof ServerPlayer owner && AccelerationManager.isEditing(owner);
    }

    public static void receive(ServerPlayer player, AccelerationPackets.Request request,
                               Consumer<AccelerationPackets.Reply> reply) {
        if (platform != null) AccelerationManager.receive(player, request, reply, platform);
    }

    public static void tick(ServerPlayer player, ObjIntConsumer<AgeableMob> unused) {
        if (platform != null) AccelerationManager.tickPlayer(player, platform);
    }

    public static void tickServer(MinecraftServer server) {
        if (platform != null) AccelerationManager.tickServer(server, platform);
    }

    public static void shutdown(MinecraftServer server) { AccelerationManager.shutdown(server); }

    public static void entityJoined(ServerLevel level, Entity entity) { AccelerationManager.entityJoined(level, entity); }

    public interface Platform {
        boolean masterEnabled(ServerPlayer player);
        void setMaster(ServerPlayer player, boolean enabled);
        boolean tickAe(ServerLevel level, BlockPos pos, BlockEntity entity);
        void tickEntity(ServerLevel level, Entity entity);
        void tickWorld(ServerLevel level);
        void strikeLightning(ServerLevel level, BlockPos pos);
        void syncWorld(ServerLevel level, long worldExtraTicks);
        void showHint(ServerPlayer player);
    }
}
