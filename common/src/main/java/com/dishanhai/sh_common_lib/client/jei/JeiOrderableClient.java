// Author: dishanhai
package com.dishanhai.sh_common_lib.client.jei;

import appeng.api.stacks.AEKey;
import com.dishanhai.sh_common_lib.api.client.ulk.compat.Ae2CraftableIndex;
import com.dishanhai.sh_common_lib.internal.ulk.Ae2OrderableSync;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.Connection;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class JeiOrderableClient {
    public static final JeiOrderableClient INSTANCE = new JeiOrderableClient();

    private BiConsumer<Connection, Ae2OrderableSync.Request> send;
    private ClientLevel level;
    private Connection connection;
    private long nextRequest;
    private long expires;
    private int requestId;
    private int knownSnapshot;
    private boolean enabled;
    private Set<AEKey> keys = Set.of();
    private final Set<AEKey> pending = new HashSet<>();

    private JeiOrderableClient() {}

    public void bind(BiConsumer<Connection, Ae2OrderableSync.Request> send) {
        this.send = send;
        Ae2CraftableIndex.installBackground(this::get);
    }

    public void syncSession() {
        var client = Minecraft.getInstance();
        var listener = client.getConnection();
        var current = listener == null ? null : listener.getConnection();
        boolean selected = JeiSearchPreferences.current().ae2Orderable();
        if (level == client.level && connection == current && enabled == selected) return;
        level = client.level;
        connection = current;
        enabled = selected;
        nextRequest = expires = System.nanoTime();
        requestId++;
        knownSnapshot = 0;
        keys = Set.of();
        pending.clear();
    }

    private Set<AEKey> get() {
        syncSession();
        long now = System.nanoTime();
        if (enabled && level != null && connection != null && now - nextRequest >= 0) {
            nextRequest = now + Ae2OrderableSync.REFRESH;
            pending.clear();
            send.accept(connection, new Ae2OrderableSync.Request(++requestId, knownSnapshot));
        }
        return now - expires < 0 ? keys : Set.of();
    }

    public <B extends ByteBuf> void accept(Connection sender, Ae2OrderableSync.Reply reply,
                                           BiFunction<ByteBuf, ClientLevel, B> wrap, Function<B, AEKey> readKey) {
        syncSession();
        if (sender != connection || level == null || reply.id() != requestId || !enabled) return;

        if (!reply.unchanged()) {
            var buffer = wrap.apply(Unpooled.wrappedBuffer(reply.keys()), level);
            try {
                while (buffer.isReadable()) {
                    var key = readKey.apply(buffer);
                    if (key != null) pending.add(key);
                }
            } finally {
                buffer.release();
            }
        }
        if (reply.last()) {
            if (!reply.unchanged()) keys = Set.copyOf(pending);
            knownSnapshot = reply.id();
            expires = System.nanoTime() + 2 * Ae2OrderableSync.REFRESH;
            pending.clear();
        }
    }
}
