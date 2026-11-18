// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge;

import com.dishanhai.sh_common_lib.api.ulk.acceleration.AccelerationPackets;
import com.dishanhai.sh_common_lib.client.item.AccelerationClient;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanAcceleration;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

final class NeoForgeAccelerationNetwork {
    private NeoForgeAccelerationNetwork() {}

    static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1").optional();
        registrar.playToServer(Inbound.TYPE, Inbound.CODEC, (message, context) -> {
            if (context.player() instanceof ServerPlayer player)
                CosmicArtisanAcceleration.receive(player, message.request(), reply -> reply(player, reply));
        });
        registrar.playToClient(Outbound.TYPE, Outbound.CODEC,
                (message, context) -> AccelerationClient.receive(context.connection(), message.reply()));
        registrar.playToClient(Clock.TYPE, Clock.CODEC,
                (message, context) -> AccelerationClient.receiveClock(context.connection(), message.dimension(), message.ticks()));
    }

    static boolean supported(Connection connection) {
        return connection != null && connection.isConnected()
                && NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, Inbound.TYPE.id())
                && NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, Outbound.TYPE.id());
    }

    static boolean send(Connection connection, AccelerationPackets.Request request) {
        if (!supported(connection)) return false;
        connection.send(new ServerboundCustomPayloadPacket(new Inbound(request)));
        return true;
    }

    static void reply(ServerPlayer player, AccelerationPackets.Reply reply) {
        if (supported(player.connection.getConnection())) player.connection.send(new ClientboundCustomPayloadPacket(new Outbound(reply)));
    }

    static void syncClock(ServerPlayer player, String dimension, long ticks) {
        if (supported(player.connection.getConnection())) player.connection.send(new ClientboundCustomPayloadPacket(new Clock(dimension, ticks)));
    }

    private record Inbound(AccelerationPackets.Request request) implements CustomPacketPayload {
        static final Type<Inbound> TYPE = new Type<>(Identifier.fromNamespaceAndPath("sh_common_lib", "acceleration/request"));
        static final StreamCodec<FriendlyByteBuf, Inbound> CODEC = StreamCodec.of(
                (buf, packet) -> AccelerationPackets.Request.write(buf, packet.request()),
                buf -> new Inbound(AccelerationPackets.Request.read(buf)));
        @Override public Type<Inbound> type() { return TYPE; }
    }

    private record Outbound(AccelerationPackets.Reply reply) implements CustomPacketPayload {
        static final Type<Outbound> TYPE = new Type<>(Identifier.fromNamespaceAndPath("sh_common_lib", "acceleration/reply"));
        static final StreamCodec<FriendlyByteBuf, Outbound> CODEC = StreamCodec.of(
                (buf, packet) -> AccelerationPackets.Reply.write(buf, packet.reply()),
                buf -> new Outbound(AccelerationPackets.Reply.read(buf)));
        @Override public Type<Outbound> type() { return TYPE; }
    }

    private record Clock(String dimension, long ticks) implements CustomPacketPayload {
        static final Type<Clock> TYPE = new Type<>(Identifier.fromNamespaceAndPath("sh_common_lib", "acceleration/clock"));
        static final StreamCodec<FriendlyByteBuf, Clock> CODEC = StreamCodec.of(
                (buf, packet) -> { buf.writeUtf(packet.dimension(), 256); buf.writeLong(packet.ticks()); },
                buf -> new Clock(buf.readUtf(256), buf.readLong()));
        @Override public Type<Clock> type() { return TYPE; }
    }
}
