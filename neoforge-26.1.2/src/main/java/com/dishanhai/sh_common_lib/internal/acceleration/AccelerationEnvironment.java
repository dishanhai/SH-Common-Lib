// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.acceleration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.phys.Vec3;

final class AccelerationEnvironment {
    private static final Identifier KEY = Identifier.fromNamespaceAndPath("sh_common_lib", "cosmic_acceleration");
    private AccelerationEnvironment() {}
    static CompoundTag read(MinecraftServer server) { return server.getCommandStorage().get(KEY); }
    static void write(MinecraftServer server, CompoundTag tag) { server.getCommandStorage().set(KEY, tag); }
    static String dimension(ServerLevel level) { return level.dimension().identifier().toString(); }
    static void feedback(ServerPlayer player, Component message) { player.sendOverlayMessage(message); }
    static boolean aimsAtCelestial(ServerPlayer player) {
        var level = (ServerLevel) player.level();
        var origin = player.getEyePosition();
        double sun = Math.toRadians(level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, origin));
        double moon = Math.toRadians(level.environmentAttributes().getValue(EnvironmentAttributes.MOON_ANGLE, origin));
        var aim = player.getLookAngle();
        return aim.dot(new Vec3(-Math.sin(sun), Math.cos(sun), 0)) >= 0.985
                || aim.dot(new Vec3(-Math.sin(moon), Math.cos(moon), 0)) >= 0.985;
    }
}
