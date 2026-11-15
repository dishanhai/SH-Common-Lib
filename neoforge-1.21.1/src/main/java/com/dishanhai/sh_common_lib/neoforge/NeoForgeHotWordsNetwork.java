// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import com.dishanhai.sh_common_lib.api.client.ulk.ClientHotWords;
import com.dishanhai.sh_common_lib.api.ulk.HotWordLedger;
import com.dishanhai.sh_common_lib.internal.ulk.ServerHotWords;
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
import java.util.ArrayList;
import java.util.List;

final class NeoForgeHotWordsNetwork {
    private NeoForgeHotWordsNetwork() {}
    static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1").optional();
        registrar.playToServer(Request.TYPE, StreamCodec.unit(Request.INSTANCE), (request, context) -> {
            if (!(context.player() instanceof ServerPlayer player) || !supported(context.connection())) return;
            ServerHotWords.top(player.getServer(), player.getUUID()).ifPresent(words -> context.reply(new Reply(words)));
        });
        registrar.playToServer(Report.TYPE, Report.CODEC, (report, context) -> {
            if (context.player() instanceof ServerPlayer player)
                ServerHotWords.report(player.getServer(), player.getUUID(), report.word());
        });
        registrar.playToClient(Reply.TYPE, Reply.CODEC,
                (reply, context) -> ClientHotWords.accept(context.connection(), reply.words()));
    }
    private static boolean supported(Connection connection) {
        return connection.isConnected() && NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, Request.TYPE.id())
                && NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, Report.TYPE.id())
                && NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, Reply.TYPE.id());
    }
    static void bindClient() {
        ClientHotWords.install(new ClientHotWords.Transport() {
            @Override public void request(Object value) {
                if (value instanceof Connection connection && supported(connection))
                    connection.send(new ServerboundCustomPayloadPacket(Request.INSTANCE));
            }
            @Override public void report(Object value, String query) {
                if (value instanceof Connection connection && supported(connection))
                    connection.send(new ServerboundCustomPayloadPacket(new Report(query)));
            }
        });
    }
    private enum Request implements CustomPacketPayload {
        INSTANCE;
        static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ShCommonLib.MOD_ID, "hotwords_v1/request"));
        @Override public Type<Request> type() { return TYPE; }
    }
    private record Report(String word) implements CustomPacketPayload {
        static final Type<Report> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ShCommonLib.MOD_ID, "hotwords_v1/report"));
        static final StreamCodec<FriendlyByteBuf, Report> CODEC = StreamCodec.of(
                (buf, value) -> buf.writeUtf(value.word(), HotWordLedger.MAX_WORD_LENGTH),
                buf -> new Report(buf.readUtf(HotWordLedger.MAX_WORD_LENGTH)));
        @Override public Type<Report> type() { return TYPE; }
    }
    private record Reply(List<String> words) implements CustomPacketPayload {
        static final Type<Reply> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ShCommonLib.MOD_ID, "hotwords_v1/reply"));
        static final StreamCodec<FriendlyByteBuf, Reply> CODEC = StreamCodec.of((buf, value) -> {
            buf.writeVarInt(value.words().size());
            for (String word : value.words()) buf.writeUtf(word, HotWordLedger.MAX_WORD_LENGTH);
        }, buf -> {
            int size = buf.readVarInt();
            if (size < 0 || size > HotWordLedger.MAX_RESULTS) throw new IllegalArgumentException("Invalid hotword count");
            var words = new ArrayList<String>(size);
            for (int i = 0; i < size; i++) words.add(buf.readUtf(HotWordLedger.MAX_WORD_LENGTH));
            return new Reply(List.copyOf(words));
        });
        @Override public Type<Reply> type() { return TYPE; }
    }
}
