// Author: dishanhai
package com.dishanhai.sh_common_lib.client.item;

import com.dishanhai.sh_common_lib.api.ulk.acceleration.AccelerationPackets;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes.Mode;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/** Client-side projection of server-owned acceleration settings and world markers. */
public final class AccelerationClient {
    private static final UUID NIL = new UUID(0L, 0L);
    private static Connection connection;
    private static AccelerationPackets.Reply snapshot;
    private static long receivedAt;
    private static long unansweredSince;
    private static int ticks;
    private static boolean supported;
    private static Consumer<AccelerationPackets.Request> sender;

    private AccelerationClient() {}

    public static void tick(Minecraft client, boolean available, Consumer<AccelerationPackets.Request> send,
                            ToIntFunction<ItemStack> flags, Consumer<Mode> toggle) {
        var listener = client.getConnection();
        var current = listener == null ? null : listener.getConnection();
        if (connection != current) {
            connection = current;
            snapshot = null;
            receivedAt = 0L;
            unansweredSince = 0L;
            ticks = 0;
        }
        supported = available && current != null;
        sender = supported ? send : null;
        if (!holding(client)) {
            snapshot = null;
            return;
        }
        if (supported && ticks++ % 20 == 0) request(AccelerationPackets.SYNC, 0, 0, 0, NIL);
    }

    public static void receive(Connection from, AccelerationPackets.Reply reply) {
        var listener = Minecraft.getInstance().getConnection();
        if (listener == null || listener.getConnection() != from) return;
        if (connection != from) {
            connection = from;
            snapshot = null;
        }
        snapshot = reply;
        receivedAt = System.currentTimeMillis();
        unansweredSince = 0L;
    }

    public static void receiveClock(Connection from, String dimension, long ticks) {
        var listener = Minecraft.getInstance().getConnection();
        if (listener != null && listener.getConnection() == from) {
            AccelerationCloudClock.receive(from, dimension, ticks);
        }
    }

    public static AccelerationPackets.Reply snapshot() { return snapshot; }
    public static long snapshotReceivedAt() { return receivedAt; }

    public static void open(Minecraft client) {
        if (!holding(client) || client.screen != null) return;
        openedConfig();
        request(AccelerationPackets.SYNC, 0, 0, 0, NIL);
        client.setScreen(new AccelerationScreen());
    }

    /** Called when either the G configuration or the X acceleration page is opened. */
    public static void openedConfig() { request(AccelerationPackets.OPENED, 0, 0, 0, NIL); }

    public static boolean scroll(Minecraft client, double amount) {
        if (amount == 0 || client.screen != null || !holding(client)
                || !client.player.isShiftKeyDown() || !supported) return false;
        return request(AccelerationPackets.SCROLL, amount > 0 ? 1 : -1, 0, 0, NIL);
    }

    public static boolean mark(BlockPos pos, boolean control) {
        if (sender == null || connection == null || !connection.isConnected() || pos == null) return false;
        sender.accept(new AccelerationPackets.Request(AccelerationPackets.MARK, control ? 1 : 0, 0, 0, NIL, pos));
        if (unansweredSince == 0L) unansweredSince = System.currentTimeMillis();
        return true;
    }

    static boolean holding(Minecraft client) {
        return client.player != null && !client.player.isSpectator()
                && CosmicArtisanModes.isActive(client.player.getMainHandItem());
    }

    static boolean request(int action, int a, int b, int c, UUID id) {
        if (sender == null || connection == null || !connection.isConnected()) return false;
        sender.accept(new AccelerationPackets.Request(action, a, b, c, id == null ? NIL : id, BlockPos.ZERO));
        if (unansweredSince == 0L) unansweredSince = System.currentTimeMillis();
        return true;
    }

    static boolean waiting() { return unansweredSince != 0L && System.currentTimeMillis() - unansweredSince < 3000L; }
    static boolean unavailable() { return !supported || unansweredSince != 0L && !waiting(); }
    static Component text(String key, Object... args) {
        return Component.translatable("screen.sh_common_lib.acceleration." + key, args);
    }
}
