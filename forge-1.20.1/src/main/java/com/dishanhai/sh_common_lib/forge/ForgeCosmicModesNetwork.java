// Author: dishanhai
package com.dishanhai.sh_common_lib.forge;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

final class ForgeCosmicModesNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(ShCommonLib.MOD_ID, "cosmic_modes_v2"),
            () -> "1", NetworkRegistry.acceptMissingOr("1"), NetworkRegistry.acceptMissingOr("1"));

    private ForgeCosmicModesNetwork() {}

    static void register() {
        CHANNEL.messageBuilder(Toggle.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((message, buf) -> buf.writeVarInt(message.bit()))
                .decoder(buf -> new Toggle(buf.readVarInt()))
                .consumerMainThread((message, supplied) -> {
                    var player = supplied.get().getSender();
                    if (player != null) ForgeCosmicModes.toggle(player, message.bit());
                }).add();
    }

    static boolean supported(Connection connection) {
        return connection.isConnected() && CHANNEL.isRemotePresent(connection);
    }

    static void send(Connection connection, int bit) {
        if (supported(connection)) {
            CHANNEL.sendTo(new Toggle(bit), connection, NetworkDirection.PLAY_TO_SERVER);
        }
    }

    private record Toggle(int bit) {}
}
