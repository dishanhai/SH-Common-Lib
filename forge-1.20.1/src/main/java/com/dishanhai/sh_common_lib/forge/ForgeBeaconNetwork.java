// Author: dishanhai
package com.dishanhai.sh_common_lib.forge;

import com.dishanhai.sh_common_lib.client.beacon.BeaconClient;
import com.dishanhai.sh_common_lib.internal.beacon.BeaconDirectory;
import com.dishanhai.sh_common_lib.internal.beacon.BeaconPackets;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

final class ForgeBeaconNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("sh_common_lib", "cosmic_beacons"),
            () -> "1", NetworkRegistry.acceptMissingOr("1"), NetworkRegistry.acceptMissingOr("1"));

    private ForgeBeaconNetwork() {}

    static void register() {
        CHANNEL.messageBuilder(Inbound.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((packet, buf) -> BeaconPackets.Request.write(buf, packet.message()))
                .decoder(buf -> new Inbound(BeaconPackets.Request.read(buf)))
                .consumerMainThread((packet, context) -> {
                    var player = context.get().getSender();
                    if (player != null) BeaconDirectory.receive(player, packet.message(), reply -> reply(player, reply));
                }).add();
        CHANNEL.messageBuilder(Outbound.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((packet, buf) -> BeaconPackets.Reply.write(buf, packet.message()))
                .decoder(buf -> new Outbound(BeaconPackets.Reply.read(buf)))
                .consumerMainThread((packet, context) -> BeaconClient.receive(context.get().getNetworkManager(), packet.message())).add();
    }

    static boolean send(Connection connection, BeaconPackets.Request request) {
        if (!supported(connection)) return false;
        CHANNEL.sendTo(new Inbound(request), connection, NetworkDirection.PLAY_TO_SERVER);
        return true;
    }

    static void reply(ServerPlayer player, BeaconPackets.Reply reply) {
        var connection = player.connection.connection;
        if (supported(connection)) CHANNEL.sendTo(new Outbound(reply), connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    private static boolean supported(Connection connection) {
        return connection != null && connection.isConnected() && CHANNEL.isRemotePresent(connection);
    }

    private record Inbound(BeaconPackets.Request message) {}
    private record Outbound(BeaconPackets.Reply message) {}
}
