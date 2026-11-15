// Author: dishanhai
package com.dishanhai.sh_common_lib.forge;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.stacks.AEKey;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import com.dishanhai.sh_common_lib.api.ShCommonLib;
import com.dishanhai.sh_common_lib.api.ulk.compat.Ae2AccessibleGrid;
import com.dishanhai.sh_common_lib.client.jei.JeiOrderableClient;
import com.dishanhai.sh_common_lib.internal.ulk.Ae2OrderableSync;
import io.netty.buffer.Unpooled;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

final class ForgeJeiOrderableNetwork {
    private static final Ae2OrderableSync SYNC = new Ae2OrderableSync();
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ShCommonLib.MOD_ID, "jei_orderable"), () -> VERSION,
            NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));

    private ForgeJeiOrderableNetwork() {}

    static void register() {
        CHANNEL.messageBuilder(Ae2OrderableSync.Request.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Ae2OrderableSync.Request::write).decoder(Ae2OrderableSync.Request::read)
                .consumerMainThread((request, supplied) -> {
                    var context = supplied.get();
                    var player = context.getSender();
                    var connection = context.getNetworkManager();
                    if (player == null || !supported(connection)) return;
                    SYNC.respond(player, request, () -> findGrid(player), () -> new FriendlyByteBuf(Unpooled.buffer()),
                            AEKey::writeKey, reply -> CHANNEL.sendTo(reply, connection, NetworkDirection.PLAY_TO_CLIENT));
                }).add();
        CHANNEL.messageBuilder(Ae2OrderableSync.Reply.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Ae2OrderableSync.Reply::write).decoder(Ae2OrderableSync.Reply::read)
                .consumerMainThread((reply, supplied) -> JeiOrderableClient.INSTANCE.accept(
                        supplied.get().getNetworkManager(), reply, (buffer, level) -> new FriendlyByteBuf(buffer), AEKey::readKey))
                .add();
    }

    static void bindClient() {
        JeiOrderableClient.INSTANCE.bind((connection, request) -> {
            if (supported(connection)) CHANNEL.sendTo(request, connection, NetworkDirection.PLAY_TO_SERVER);
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) JeiOrderableClient.INSTANCE.syncSession();
        });
    }

    private static boolean supported(Connection connection) {
        return connection.isConnected() && CHANNEL.isRemotePresent(connection);
    }

    private static IGrid findGrid(ServerPlayer player) {
        return Ae2AccessibleGrid.find(player, player.getBlockReach(), pos -> player.canReach(pos, 0),
                terminal -> true, slot -> wirelessNode(player, slot));
    }

    private static IGridNode wirelessNode(ServerPlayer player, int slot) {
        var stack = player.getInventory().getItem(slot);
        if (!(stack.getItem() instanceof WirelessTerminalItem terminal) || !terminal.hasPower(player, 1, stack)
                || terminal.getLinkedGrid(stack, player.level(), null) == null) return null;
        if (terminal.getMenuHost(player, slot, stack, null) instanceof WirelessTerminalMenuHost host && host.rangeCheck()) {
            return host.getActionableNode();
        }
        return null;
    }
}
