// Author: dishanhai
package com.dishanhai.sh_common_lib.client.hud;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.Connection;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Counts the client's bytes at the transport edge, without decoding or retaining packets. */
final class GameTraffic {
    private Channel channel;
    private Meter meter;
    private long previousTime, previousReceived, previousSent;
    private Rate rate;

    void tick(Connection connection) {
        Channel next = connection != null && connection.isConnected() && !connection.isMemoryConnection()
                ? connection.channel() : null;
        if (channel != next) {
            detach();
            channel = next;
            if (next != null) {
                meter = new Meter();
                Meter added = meter;
                try {
                    next.eventLoop().execute(() -> {
                        if (next.isOpen()) {
                            next.pipeline().addFirst(added);
                            added.installed = true;
                        }
                    });
                } catch (RuntimeException ignored) {
                    // The connection may already be closing.
                }
                previousTime = System.nanoTime();
            }
        }
        if (meter == null || !meter.installed) return;
        long now = System.nanoTime(), elapsed = now - previousTime;
        if (elapsed < TimeUnit.SECONDS.toNanos(1)) return;
        long received = meter.received.get(), sent = meter.sent.get();
        rate = new Rate((received - previousReceived) * 1_000_000_000.0 / elapsed,
                (sent - previousSent) * 1_000_000_000.0 / elapsed);
        previousReceived = received;
        previousSent = sent;
        previousTime = now;
    }

    Optional<Rate> rate() { return Optional.ofNullable(rate); }

    private void detach() {
        Channel oldChannel = channel;
        Meter oldMeter = meter;
        if (oldChannel != null && oldMeter != null) {
            try {
                oldChannel.eventLoop().execute(() -> {
                    if (oldChannel.pipeline().context(oldMeter) != null) oldChannel.pipeline().remove(oldMeter);
                });
            } catch (RuntimeException ignored) {
                // The event loop can have already shut down with the channel.
            }
        }
        channel = null;
        meter = null;
        previousReceived = previousSent = 0;
        rate = null;
    }

    record Rate(double received, double sent) {}

    private static final class Meter extends ChannelDuplexHandler {
        final AtomicLong received = new AtomicLong();
        final AtomicLong sent = new AtomicLong();
        volatile boolean installed;

        @Override public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof ByteBuf bytes) received.addAndGet(bytes.readableBytes());
            super.channelRead(ctx, msg);
        }

        @Override public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof ByteBuf bytes) sent.addAndGet(bytes.readableBytes());
            super.write(ctx, msg, promise);
        }
    }
}
