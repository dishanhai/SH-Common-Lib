// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge;

import com.dishanhai.sh_common_lib.client.beacon.BeaconClient;
import com.dishanhai.sh_common_lib.internal.beacon.BeaconDirectory;
import com.dishanhai.sh_common_lib.internal.beacon.BeaconPackets;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

final class NeoForgeBeaconNetwork {
    private NeoForgeBeaconNetwork() {}

    static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1").optional();
        registrar.playToServer(Inbound.TYPE, Inbound.CODEC, (message, context) -> {
            if (context.player() instanceof ServerPlayer player)
                BeaconDirectory.receive(player, message.request(), reply -> reply(player, reply));
        });
        registrar.playToClient(Outbound.TYPE, Outbound.CODEC,
                (message, context) -> BeaconClient.receive(context.connection(), message.reply()));
    }

    static boolean send(Connection connection, BeaconPackets.Request request) {
        if (!supported(connection)) return false;
        connection.send(new ServerboundCustomPayloadPacket(new Inbound(request)));
        return true;
    }

    static void reply(ServerPlayer player, BeaconPackets.Reply reply) {
        if (supported(player.connection.getConnection())) player.connection.send(new ClientboundCustomPayloadPacket(new Outbound(reply)));
    }

    private static boolean supported(Connection connection) {
        return connection != null && connection.isConnected()
                && NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, Inbound.TYPE.id())
                && NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, Outbound.TYPE.id());
    }

    private record Inbound(BeaconPackets.Request request) implements CustomPacketPayload {
        static final Type<Inbound> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("sh_common_lib", "cosmic_beacons/request"));
        static final StreamCodec<FriendlyByteBuf, Inbound> CODEC = StreamCodec.of(
                (buf, packet) -> BeaconPackets.Request.write(buf, packet.request()),
                buf -> new Inbound(BeaconPackets.Request.read(buf)));
        @Override public Type<Inbound> type() { return TYPE; }
    }

    private record Outbound(BeaconPackets.Reply reply) implements CustomPacketPayload {
        static final Type<Outbound> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("sh_common_lib", "cosmic_beacons/reply"));
        static final StreamCodec<FriendlyByteBuf, Outbound> CODEC = StreamCodec.of(
                (buf, packet) -> BeaconPackets.Reply.write(buf, packet.reply()),
                buf -> new Outbound(BeaconPackets.Reply.read(buf)));
        @Override public Type<Outbound> type() { return TYPE; }
    }
}
