// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import com.dishanhai.sh_common_lib.internal.performance.ClientTickMonitor;
import com.dishanhai.sh_common_lib.internal.performance.ServerTickSample;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

final class NeoForgeTickNetwork {
    private NeoForgeTickNetwork() {}

    static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1").optional();
        registrar.playToServer(Request.TYPE, StreamCodec.unit(Request.INSTANCE), (request, context) -> {
            if (context.player() instanceof ServerPlayer player && supported(context.connection())) {
                ServerTickSample.reply(player, server -> server.getAverageTickTimeNanos() / 1_000_000.0,
                        sample -> context.reply(new Reply(sample)));
            }
        });
        registrar.playToClient(Reply.TYPE, Reply.CODEC,
                (reply, context) -> ClientTickMonitor.INSTANCE.accept(context.connection(), reply.sample()));
    }

    static void request(Connection connection) {
        if (supported(connection)) connection.send(new ServerboundCustomPayloadPacket(Request.INSTANCE));
    }

    private static boolean supported(Connection connection) {
        return connection.isConnected() && NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, Request.TYPE.id())
                && NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, Reply.TYPE.id());
    }

    private enum Request implements CustomPacketPayload {
        INSTANCE;
        private static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                ShCommonLib.MOD_ID, "server_ticks/request"));

        @Override public Type<Request> type() { return TYPE; }
    }

    private record Reply(ServerTickSample sample) implements CustomPacketPayload {
        private static final Type<Reply> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                ShCommonLib.MOD_ID, "server_ticks/reply"));
        private static final StreamCodec<FriendlyByteBuf, Reply> CODEC = StreamCodec.of(
                (buffer, reply) -> reply.sample.encode(buffer), buffer -> new Reply(ServerTickSample.decode(buffer)));

        @Override public Type<Reply> type() { return TYPE; }
    }
}
