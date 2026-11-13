// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.performance;

import com.dishanhai.sh_common_lib.api.ulk.InventorySource;
import com.dishanhai.sh_common_lib.api.ulk.InventorySnapshot;
import com.dishanhai.sh_common_lib.api.ulk.UnifiedLibKernel;
import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Only aggregate data for the requesting player crosses the network. */
public record InventoryHudSnapshot(String dimension, int flags, boolean scanned, Map<InventorySource, Source> sources,
                                   int ecoState, int ecoNodes, int ecoActive, boolean ecoTruncated) {
    public static final int ECO_NOT_INSTALLED = 0, ECO_NO_TERMINAL = 1, ECO_CONNECTED = 2, ECO_ERROR = 3;
    private static final WeakHashMap<ServerPlayer, Long> LAST_REQUEST = new WeakHashMap<>();
    public record Source(InventorySnapshot.Status status, int entries, long amount) {}

    public InventoryHudSnapshot { sources = Map.copyOf(sources); }

    public static boolean allow(ServerPlayer player) {
        long now = System.nanoTime();
        Long previous = LAST_REQUEST.get(player);
        if (previous != null && now - previous < TimeUnit.SECONDS.toNanos(20)) return false;
        LAST_REQUEST.put(player, now);
        return true;
    }

    public static Map<InventorySource, Source> inspect(ServerPlayer player) {
        InventorySnapshot inventory = UnifiedLibKernel.inspect(player);
        var counts = new EnumMap<InventorySource, Integer>(InventorySource.class);
        var amounts = new EnumMap<InventorySource, Long>(InventorySource.class);
        for (var entry : inventory.entries()) {
            counts.merge(entry.source(), 1, Integer::sum);
            amounts.merge(entry.source(), entry.amount(), (left, right) ->
                    Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right);
        }
        var result = new EnumMap<InventorySource, Source>(InventorySource.class);
        for (var source : InventorySource.values()) result.put(source, new Source(
                inventory.sources().getOrDefault(source, InventorySnapshot.Status.UNAVAILABLE),
                counts.getOrDefault(source, 0), amounts.getOrDefault(source, 0L)));
        return result;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(dimension, 256).writeVarInt(flags).writeBoolean(scanned);
        for (var source : InventorySource.values()) {
            Source data = sources.getOrDefault(source, new Source(InventorySnapshot.Status.UNAVAILABLE, 0, 0));
            buf.writeVarInt(data.status().ordinal()).writeVarInt(data.entries()).writeVarLong(data.amount());
        }
        buf.writeVarInt(ecoState).writeVarInt(ecoNodes).writeVarInt(ecoActive).writeBoolean(ecoTruncated);
    }

    public static InventoryHudSnapshot decode(FriendlyByteBuf buf) {
        String dimension = buf.readUtf(256);
        int flags = buf.readVarInt();
        if (flags < 1 || flags > 3) throw new IllegalArgumentException("Invalid inventory HUD flags");
        boolean scanned = buf.readBoolean();
        var sources = new EnumMap<InventorySource, Source>(InventorySource.class);
        for (var source : InventorySource.values()) {
            int status = buf.readVarInt(), entries = buf.readVarInt();
            long amount = buf.readVarLong();
            if (status < 0 || status >= InventorySnapshot.Status.values().length || entries < 0 || entries > 16384 || amount < 0)
                throw new IllegalArgumentException("Invalid inventory HUD source");
            sources.put(source, new Source(InventorySnapshot.Status.values()[status], entries, amount));
        }
        int state = buf.readVarInt(), nodes = buf.readVarInt(), active = buf.readVarInt();
        boolean truncated = buf.readBoolean();
        if (state < ECO_NOT_INSTALLED || state > ECO_ERROR || nodes < 0 || nodes > 1024 || active < 0 || active > nodes)
            throw new IllegalArgumentException("Invalid ECO HUD state");
        return new InventoryHudSnapshot(dimension, flags, scanned, sources, state, nodes, active, truncated);
    }
}
