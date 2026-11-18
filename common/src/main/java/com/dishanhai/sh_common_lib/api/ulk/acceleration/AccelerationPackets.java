// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.acceleration;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Bounded transport for the acceleration controller. The server validates every request. */
public final class AccelerationPackets {
    public static final int SYNC = 0, SET_RATE = 1, SET_MODE = 2, SET_NO_AI = 3, SET_SIZE = 4,
            SET_PLACING = 5, SET_EDITING = 6, SET_FILTER = 7, SET_RANGE_ENABLED = 8,
            RECLAIM = 9, SET_MASTER = 10, SELECT_RANGE = 11, OPENED = 12, SCROLL = 13, MARK = 14;
    public static final UUID NO_ID = new UUID(0, 0);
    public static final int MAX_RANGES = 128;
    public static final int MAX_VISUALS = 2048;

    private AccelerationPackets() {}

    public record Request(int action, int a, int b, int c, UUID id, BlockPos pos) {
        public Request {
            if (id == null || pos == null) throw new IllegalArgumentException("Missing request fields");
        }

        public static Request read(FriendlyByteBuf buf) {
            return new Request(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readUUID(), buf.readBlockPos());
        }

        public static void write(FriendlyByteBuf buf, Request message) {
            buf.writeVarInt(message.action); buf.writeVarInt(message.a); buf.writeVarInt(message.b);
            buf.writeVarInt(message.c); buf.writeUUID(message.id); buf.writeBlockPos(message.pos);
        }
    }

    public record Settings(int rate, int mode, boolean disableAi, int x, int y, int z, boolean placing,
                           int editing, boolean accelerationWhitelist, boolean sleepWhitelist, boolean master,
                           UUID selected) {}

    public record RangeView(UUID id, String dimension, BlockPos pos, long created, boolean enabled,
                            int x, int y, int z, int rate, boolean accelerationWhitelist,
                            boolean sleepWhitelist, int accelerationCount, int sleepCount) {}

    /** A negative remaining value denotes a permanent target. */
    public record Visual(String dimension, BlockPos pos, int entityId, int kind, int remaining, int duration,
                         boolean sleeping) {}

    public record Reply(Settings settings, List<RangeView> ranges, List<Visual> visuals, long worldExtraTicks) {
        public Reply { ranges = List.copyOf(ranges); visuals = List.copyOf(visuals); }

        public static Reply read(FriendlyByteBuf buf) {
            var s = new Settings(buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readBoolean(),
                    buf.readBoolean(), buf.readBoolean(), buf.readUUID());
            int count = count(buf, MAX_RANGES);
            var ranges = new ArrayList<RangeView>(count);
            for (int i = 0; i < count; i++) {
                ranges.add(new RangeView(buf.readUUID(), buf.readUtf(256), buf.readBlockPos(), buf.readLong(),
                        buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt()));
            }
            count = count(buf, MAX_VISUALS);
            var visuals = new ArrayList<Visual>(count);
            for (int i = 0; i < count; i++) {
                visuals.add(new Visual(buf.readUtf(256), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
            }
            return new Reply(s, ranges, visuals, buf.readLong());
        }

        public static void write(FriendlyByteBuf buf, Reply reply) {
            var s = reply.settings;
            buf.writeVarInt(s.rate); buf.writeVarInt(s.mode); buf.writeBoolean(s.disableAi);
            buf.writeVarInt(s.x); buf.writeVarInt(s.y); buf.writeVarInt(s.z); buf.writeBoolean(s.placing);
            buf.writeVarInt(s.editing); buf.writeBoolean(s.accelerationWhitelist); buf.writeBoolean(s.sleepWhitelist);
            buf.writeBoolean(s.master); buf.writeUUID(s.selected);
            if (reply.ranges.size() > MAX_RANGES || reply.visuals.size() > MAX_VISUALS)
                throw new IllegalArgumentException("Acceleration snapshot too large");
            buf.writeVarInt(reply.ranges.size());
            for (var r : reply.ranges) {
                buf.writeUUID(r.id); buf.writeUtf(r.dimension, 256); buf.writeBlockPos(r.pos); buf.writeLong(r.created);
                buf.writeBoolean(r.enabled); buf.writeVarInt(r.x); buf.writeVarInt(r.y); buf.writeVarInt(r.z);
                buf.writeVarInt(r.rate); buf.writeBoolean(r.accelerationWhitelist); buf.writeBoolean(r.sleepWhitelist);
                buf.writeVarInt(r.accelerationCount); buf.writeVarInt(r.sleepCount);
            }
            buf.writeVarInt(reply.visuals.size());
            for (var v : reply.visuals) {
                buf.writeUtf(v.dimension, 256); buf.writeBlockPos(v.pos); buf.writeVarInt(v.entityId);
                buf.writeVarInt(v.kind); buf.writeVarInt(v.remaining); buf.writeVarInt(v.duration); buf.writeBoolean(v.sleeping);
            }
            buf.writeLong(reply.worldExtraTicks);
        }
    }

    private static int count(FriendlyByteBuf buf, int maximum) {
        int count = buf.readVarInt();
        if (count < 0 || count > maximum) throw new IllegalArgumentException("Invalid acceleration list size");
        return count;
    }
}
