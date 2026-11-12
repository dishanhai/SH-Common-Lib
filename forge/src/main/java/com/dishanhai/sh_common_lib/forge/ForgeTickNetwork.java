// Author: dishanhai
package com.dishanhai.sh_common_lib.forge;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import com.dishanhai.sh_common_lib.internal.performance.ClientTickMonitor;
import com.dishanhai.sh_common_lib.internal.performance.ServerTickSample;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

final class ForgeTickNetwork {
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ShCommonLib.MOD_ID, "server_ticks"),
            () -> VERSION, NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));

    private ForgeTickNetwork() {}

    static void register() {
        CHANNEL.messageBuilder(Request.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((request, buffer) -> {})
                .decoder(buffer -> Request.INSTANCE)
                .consumerMainThread((request, supplied) -> {
                    var context = supplied.get();
                    var player = context.getSender();
                    var connection = context.getNetworkManager();
                    if (player != null && supported(connection)) {
                        ServerTickSample.reply(player, MinecraftServer::getAverageTickTime,
                                sample -> CHANNEL.sendTo(sample, connection, NetworkDirection.PLAY_TO_CLIENT));
                    }
                }).add();
        CHANNEL.messageBuilder(ServerTickSample.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ServerTickSample::encode)
                .decoder(ServerTickSample::decode)
                .consumerMainThread((sample, context) ->
                        ClientTickMonitor.INSTANCE.accept(context.get().getNetworkManager(), sample)).add();
    }

    static void request(Connection connection) {
        if (supported(connection)) CHANNEL.sendTo(Request.INSTANCE, connection, NetworkDirection.PLAY_TO_SERVER);
    }

    private static boolean supported(Connection connection) {
        return connection.isConnected() && CHANNEL.isRemotePresent(connection);
    }

    private enum Request { INSTANCE }
}
