// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.stacks.AEKey;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.locator.MenuLocators;
import com.dishanhai.sh_common_lib.api.ulk.compat.Ae2AccessibleGrid;
import com.dishanhai.sh_common_lib.client.jei.JeiOrderableClient;
import com.dishanhai.sh_common_lib.internal.ulk.Ae2OrderableSync;
import io.netty.buffer.Unpooled;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

final class NeoForgeJeiOrderableNetwork {
    private static final Ae2OrderableSync SYNC = new Ae2OrderableSync();

    private NeoForgeJeiOrderableNetwork() {}

    static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1").optional();
        registrar.playToServer(Request.TYPE, Request.CODEC, (request, context) -> {
            if (!(context.player() instanceof ServerPlayer player) || !supported(context.connection())) return;
            SYNC.respond(player, request.value(), () -> findGrid(player),
                    () -> new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess()),
                    AEKey::writeKey, reply -> context.reply(new Reply(reply)));
        });
        registrar.playToClient(Reply.TYPE, Reply.CODEC,
                (reply, context) -> JeiOrderableClient.INSTANCE.accept(context.connection(), reply.value(),
                        (buffer, level) -> new RegistryFriendlyByteBuf(buffer, level.registryAccess()), AEKey::readKey));
    }

    static void bindClient() {
        JeiOrderableClient.INSTANCE.bind((connection, request) -> {
            if (supported(connection)) connection.send(new ServerboundCustomPayloadPacket(new Request(request)));
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> JeiOrderableClient.INSTANCE.syncSession());
    }

    private static IGrid findGrid(ServerPlayer player) {
        return Ae2AccessibleGrid.find(player, player.blockInteractionRange(),
                pos -> NeoForgeShCommonLib.canReachTerminal(player, pos), terminal -> terminal.getLinkStatus().connected(),
                slot -> wirelessNode(player, slot));
    }

    private static IGridNode wirelessNode(ServerPlayer player, int slot) {
        var stack = player.getInventory().getItem(slot);
        if (!(stack.getItem() instanceof WirelessTerminalItem terminal) || !terminal.hasPower(player, 1, stack)) return null;
        var host = terminal.getMenuHost(player, MenuLocators.forInventorySlot(slot), null);
        return host != null && host.getLinkStatus().connected() ? host.getActionableNode() : null;
    }

    private static boolean supported(Connection connection) {
        return connection.isConnected()
                && NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, Request.TYPE.id())
                && NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, Reply.TYPE.id());
    }

    private record Request(Ae2OrderableSync.Request value) implements CustomPacketPayload {
        private static final Type<Request> TYPE = NeoForgeShCommonLib.orderablePayloadType("jei_orderable/request");
        private static final StreamCodec<FriendlyByteBuf, Request> CODEC = StreamCodec.of(
                (buffer, request) -> request.value().write(buffer), buffer -> new Request(Ae2OrderableSync.Request.read(buffer)));
        @Override public Type<Request> type() { return TYPE; }
    }

    private record Reply(Ae2OrderableSync.Reply value) implements CustomPacketPayload {
        private static final Type<Reply> TYPE = NeoForgeShCommonLib.orderablePayloadType("jei_orderable/reply");
        private static final StreamCodec<FriendlyByteBuf, Reply> CODEC = StreamCodec.of(
                (buffer, reply) -> reply.value().write(buffer), buffer -> new Reply(Ae2OrderableSync.Reply.read(buffer)));
        @Override public Type<Reply> type() { return TYPE; }
    }
}
