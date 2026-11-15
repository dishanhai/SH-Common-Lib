// Author: dishanhai
package com.dishanhai.sh_common_lib.forge;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import com.dishanhai.sh_common_lib.api.client.ulk.ClientHotWords;
import com.dishanhai.sh_common_lib.api.ulk.HotWordLedger;
import com.dishanhai.sh_common_lib.internal.ulk.ServerHotWords;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.ArrayList;
import java.util.List;

final class ForgeHotWordsNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ShCommonLib.MOD_ID, "hotwords_v1"), () -> "1",
            NetworkRegistry.acceptMissingOr("1"), NetworkRegistry.acceptMissingOr("1"));
    private ForgeHotWordsNetwork() {}

    static void register() {
        CHANNEL.messageBuilder(Request.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((value, buf) -> {}).decoder(buf -> Request.INSTANCE)
                .consumerMainThread((value, supplied) -> {
                    var context = supplied.get(); var player = context.getSender();
                    if (player == null || !CHANNEL.isRemotePresent(context.getNetworkManager())) return;
                    ServerHotWords.top(player.getServer(), player.getUUID()).ifPresent(words ->
                            CHANNEL.sendTo(new Reply(words), context.getNetworkManager(), NetworkDirection.PLAY_TO_CLIENT));
                }).add();
        CHANNEL.messageBuilder(Report.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder((value, buf) -> buf.writeUtf(value.word(), HotWordLedger.MAX_WORD_LENGTH))
                .decoder(buf -> new Report(buf.readUtf(HotWordLedger.MAX_WORD_LENGTH)))
                .consumerMainThread((value, supplied) -> {
                    var player = supplied.get().getSender();
                    if (player != null) ServerHotWords.report(player.getServer(), player.getUUID(), value.word());
                }).add();
        CHANNEL.messageBuilder(Reply.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((value, buf) -> {
                    buf.writeVarInt(value.words().size());
                    for (String word : value.words()) buf.writeUtf(word, HotWordLedger.MAX_WORD_LENGTH);
                }).decoder(buf -> {
                    int size = buf.readVarInt();
                    if (size < 0 || size > HotWordLedger.MAX_RESULTS) throw new IllegalArgumentException("Invalid hotword count");
                    var words = new ArrayList<String>(size);
                    for (int i = 0; i < size; i++) words.add(buf.readUtf(HotWordLedger.MAX_WORD_LENGTH));
                    return new Reply(List.copyOf(words));
                }).consumerMainThread((value, supplied) -> ClientHotWords.accept(supplied.get().getNetworkManager(), value.words())).add();
    }

    static void bindClient() {
        ClientHotWords.install(new ClientHotWords.Transport() {
            private boolean supported(Object value) {
                return value instanceof Connection connection && connection.isConnected() && CHANNEL.isRemotePresent(connection);
            }
            @Override public void request(Object connection) {
                if (supported(connection)) CHANNEL.sendTo(Request.INSTANCE, (Connection) connection, NetworkDirection.PLAY_TO_SERVER);
            }
            @Override public void report(Object connection, String query) {
                if (supported(connection)) CHANNEL.sendTo(new Report(query), (Connection) connection, NetworkDirection.PLAY_TO_SERVER);
            }
        });
    }
    private enum Request { INSTANCE }
    private record Report(String word) {}
    private record Reply(List<String> words) {}
}
