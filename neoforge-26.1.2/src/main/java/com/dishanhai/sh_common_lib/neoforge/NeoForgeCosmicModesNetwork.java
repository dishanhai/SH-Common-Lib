// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

final class NeoForgeCosmicModesNetwork {
    private NeoForgeCosmicModesNetwork() {}

    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToServer(Toggle.TYPE, Toggle.CODEC, (message, context) -> {
            if (context.player() instanceof ServerPlayer player) NeoForgeCosmicModes.toggle(player, message.bit());
        });
    }

    static boolean supported(Connection connection) {
        return connection.isConnected() && NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, Toggle.TYPE.id());
    }

    static void send(Connection connection, int bit) {
        if (supported(connection)) connection.send(new ServerboundCustomPayloadPacket(new Toggle(bit)));
    }

    private record Toggle(int bit) implements CustomPacketPayload {
        private static final Type<Toggle> TYPE = new Type<>(Identifier.fromNamespaceAndPath(ShCommonLib.MOD_ID, "cosmic_modes_v2"));
        private static final StreamCodec<FriendlyByteBuf, Toggle> CODEC = StreamCodec.of(
                (buf, message) -> buf.writeVarInt(message.bit()), buf -> new Toggle(buf.readVarInt()));
        @Override public Type<Toggle> type() { return TYPE; }
    }
}
