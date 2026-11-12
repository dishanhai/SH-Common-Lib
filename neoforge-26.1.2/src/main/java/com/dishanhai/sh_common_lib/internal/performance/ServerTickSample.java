// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.performance;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

public record ServerTickSample(int tickCount, long nanoTime, double mspt) {
    private static final WeakHashMap<ServerPlayer, Long> LAST_REQUEST = new WeakHashMap<>();

    /** Called on the logical server thread, and only after channel negotiation. */
    public static void reply(ServerPlayer player, ToDoubleFunction<net.minecraft.server.MinecraftServer> averageMspt,
                             Consumer<ServerTickSample> send) {
        long now = System.nanoTime();
        Long last = LAST_REQUEST.get(player);
        if (last != null && now - last < TimeUnit.SECONDS.toNanos(1)) return;
        var server = player.level().getServer();
        LAST_REQUEST.put(player, now);
        send.accept(new ServerTickSample(server.getTickCount(), now, averageMspt.applyAsDouble(server)));
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(tickCount).writeLong(nanoTime).writeDouble(mspt);
    }

    public static ServerTickSample decode(FriendlyByteBuf buffer) {
        return new ServerTickSample(buffer.readInt(), buffer.readLong(), buffer.readDouble());
    }
}
