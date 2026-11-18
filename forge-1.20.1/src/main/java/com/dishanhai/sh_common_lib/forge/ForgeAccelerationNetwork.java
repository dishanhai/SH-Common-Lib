// Author: dishanhai
package com.dishanhai.sh_common_lib.forge;

import com.dishanhai.sh_common_lib.api.ulk.acceleration.AccelerationPackets;
import com.dishanhai.sh_common_lib.client.item.AccelerationClient;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanAcceleration;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

final class ForgeAccelerationNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("sh_common_lib", "acceleration"),
            () -> "1", NetworkRegistry.acceptMissingOr("1"), NetworkRegistry.acceptMissingOr("1"));

    private ForgeAccelerationNetwork() {}

    static void register() {
        CHANNEL.messageBuilder(Inbound.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((packet, buf) -> AccelerationPackets.Request.write(buf, packet.message()))
                .decoder(buf -> new Inbound(AccelerationPackets.Request.read(buf)))
                .consumerMainThread((packet, context) -> {
                    var player = context.get().getSender();
                    if (player != null) CosmicArtisanAcceleration.receive(player, packet.message(), reply -> reply(player, reply));
                }).add();
        CHANNEL.messageBuilder(Outbound.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((packet, buf) -> AccelerationPackets.Reply.write(buf, packet.message()))
                .decoder(buf -> new Outbound(AccelerationPackets.Reply.read(buf)))
                .consumerMainThread((packet, context) -> AccelerationClient.receive(context.get().getNetworkManager(), packet.message())).add();
        CHANNEL.messageBuilder(Clock.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((packet, buf) -> { buf.writeUtf(packet.dimension(), 256); buf.writeLong(packet.ticks()); })
                .decoder(buf -> new Clock(buf.readUtf(256), buf.readLong()))
                .consumerMainThread((packet, context) -> AccelerationClient.receiveClock(context.get().getNetworkManager(), packet.dimension(), packet.ticks())).add();
    }

    static boolean supported(Connection connection) {
        return connection != null && connection.isConnected() && CHANNEL.isRemotePresent(connection);
    }

    static boolean send(Connection connection, AccelerationPackets.Request request) {
        if (!supported(connection)) return false;
        CHANNEL.sendTo(new Inbound(request), connection, NetworkDirection.PLAY_TO_SERVER);
        return true;
    }

    static void reply(ServerPlayer player, AccelerationPackets.Reply reply) {
        var connection = player.connection.connection;
        if (supported(connection)) CHANNEL.sendTo(new Outbound(reply), connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    static void syncClock(ServerPlayer player, String dimension, long ticks) {
        var connection = player.connection.connection;
        if (supported(connection)) CHANNEL.sendTo(new Clock(dimension, ticks), connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    private record Inbound(AccelerationPackets.Request message) {}
    private record Outbound(AccelerationPackets.Reply message) {}
    private record Clock(String dimension, long ticks) {}
}
