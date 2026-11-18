// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.acceleration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

final class AccelerationEnvironment {
    private static final ResourceLocation KEY = ResourceLocation.fromNamespaceAndPath("sh_common_lib", "cosmic_acceleration");
    private AccelerationEnvironment() {}
    static CompoundTag read(MinecraftServer server) { return server.getCommandStorage().get(KEY); }
    static void write(MinecraftServer server, CompoundTag tag) { server.getCommandStorage().set(KEY, tag); }
    static String dimension(ServerLevel level) { return level.dimension().location().toString(); }
    static void feedback(ServerPlayer player, Component message) { player.displayClientMessage(message, true); }
    static boolean aimsAtCelestial(ServerPlayer player) {
        double angle = ((ServerLevel) player.level()).getTimeOfDay(1.0F) * Math.PI * 2;
        var direction = new Vec3(-Math.sin(angle), Math.cos(angle), 0);
        return Math.abs(player.getLookAngle().dot(direction)) >= 0.985;
    }
}
