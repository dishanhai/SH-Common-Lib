// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.ulk;

import appeng.api.networking.IGrid;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class Ae2OrderableSync {
    public static final long REFRESH = TimeUnit.SECONDS.toNanos(2);
    private static final int CHUNK_BYTES = 512 * 1024;
    private final Map<ServerPlayer, Snapshot> lastSent = new WeakHashMap<>();

    public <B extends ByteBuf> void respond(ServerPlayer player, Request request, Supplier<IGrid> findGrid,
                                           Supplier<B> bufferFactory, BiConsumer<B, AEKey> writeKey, Consumer<Reply> send) {
        long now = System.nanoTime();
        var previous = lastSent.get(player);
        if (previous != null && now - previous.nextRequest() < 0) return;

        var grid = findGrid.get();
        var keys = grid == null ? Set.<AEKey>of() : grid.getCraftingService()
                .getCraftables(key -> key instanceof AEItemKey || key instanceof AEFluidKey);
        if (previous != null && previous.id() == request.knownSnapshot() && previous.keys().equals(keys)) {
            send.accept(new Reply(request.id(), true, true, new byte[0]));
        } else {
            var buffer = bufferFactory.get();
            try {
                for (var key : keys) {
                    int start = buffer.writerIndex();
                    writeKey.accept(buffer, key);
                    int end = buffer.writerIndex();
                    if (end <= CHUNK_BYTES) continue;

                    if (start > 0) send.accept(new Reply(request.id(), false, false, bytes(buffer, 0, start)));
                    int length = end - start;
                    if (length <= CHUNK_BYTES) {
                        byte[] remainder = bytes(buffer, start, length);
                        buffer.clear().writeBytes(remainder);
                    } else {
                        buffer.clear();
                    }
                }
                send.accept(new Reply(request.id(), true, false, bytes(buffer, 0, buffer.writerIndex())));
            } finally {
                buffer.release();
            }
        }
        lastSent.put(player, new Snapshot(request.id(), now + REFRESH / 2, Set.copyOf(keys)));
    }

    private static byte[] bytes(ByteBuf buffer, int offset, int length) {
        byte[] result = new byte[length];
        buffer.getBytes(offset, result);
        return result;
    }

    private record Snapshot(int id, long nextRequest, Set<AEKey> keys) {}

    public record Request(int id, int knownSnapshot) {
        public void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(id);
            buffer.writeVarInt(knownSnapshot);
        }

        public static Request read(FriendlyByteBuf buffer) {
            return new Request(buffer.readVarInt(), buffer.readVarInt());
        }
    }

    public record Reply(int id, boolean last, boolean unchanged, byte[] keys) {
        public void write(FriendlyByteBuf buffer) {
            buffer.writeVarInt(id);
            buffer.writeBoolean(last);
            buffer.writeBoolean(unchanged);
            buffer.writeByteArray(keys);
        }

        public static Reply read(FriendlyByteBuf buffer) {
            return new Reply(buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean(), buffer.readByteArray(CHUNK_BYTES));
        }
    }
}
