// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.beacon;

import net.minecraft.core.GlobalPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class BeaconPackets {
    private BeaconPackets() {}

    public sealed interface Request permits DirectoryRequest, Edit, Warp {
        static Request read(FriendlyByteBuf buf) {
            return switch (buf.readUnsignedByte()) {
                case 0 -> new DirectoryRequest();
                case 1 -> new Edit(readView(buf));
                case 2 -> new Warp(buf.readGlobalPos(), buf.readUUID());
                default -> throw new IllegalArgumentException("Unknown beacon request");
            };
        }
        static void write(FriendlyByteBuf buf, Request message) {
            if (message instanceof Edit edit) {
                buf.writeByte(1);
                writeView(buf, edit.beacon());
            } else if (message instanceof Warp warp) {
                buf.writeByte(2);
                buf.writeGlobalPos(warp.point());
                buf.writeUUID(warp.id());
            } else buf.writeByte(0);
        }
    }

    public record DirectoryRequest() implements Request {}
    public record Edit(CosmicBeacon.View beacon) implements Request {}
    public record Warp(GlobalPos point, UUID id) implements Request {}

    public sealed interface Reply permits Editor, Directory {
        static Reply read(FriendlyByteBuf buf) {
            return switch (buf.readUnsignedByte()) {
                case 0 -> new Editor(readView(buf), buf.readBoolean(), buf.readBoolean());
                case 1 -> {
                    int count = buf.readVarInt();
                    if (count < 0 || count > CosmicBeacon.DIRECTORY_LIMIT) throw new IllegalArgumentException("Invalid beacon count");
                    var entries = new ArrayList<CosmicBeacon.View>(count);
                    for (int i = 0; i < count; i++) entries.add(readView(buf));
                    yield new Directory(entries);
                }
                default -> throw new IllegalArgumentException("Unknown beacon reply");
            };
        }
        static void write(FriendlyByteBuf buf, Reply message) {
            if (message instanceof Editor edit) {
                buf.writeByte(0);
                writeView(buf, edit.beacon());
                buf.writeBoolean(edit.editable());
                buf.writeBoolean(edit.canLock());
            } else if (message instanceof Directory directory) {
                buf.writeByte(1);
                buf.writeVarInt(directory.entries().size());
                for (var entry : directory.entries()) writeView(buf, entry);
            }
        }
    }

    public record Editor(CosmicBeacon.View beacon, boolean editable, boolean canLock) implements Reply {}
    public record Directory(List<CosmicBeacon.View> entries) implements Reply {
        public Directory { entries = List.copyOf(entries); }
    }

    private static CosmicBeacon.View readView(FriendlyByteBuf buf) {
        return new CosmicBeacon.View(buf.readGlobalPos(), buf.readUUID(), buf.readUtf(CosmicBeacon.NAME_LIMIT * 2),
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    private static void writeView(FriendlyByteBuf buf, CosmicBeacon.View view) {
        buf.writeGlobalPos(view.point());
        buf.writeUUID(view.id());
        buf.writeUtf(view.name(), CosmicBeacon.NAME_LIMIT * 2);
        buf.writeBoolean(view.locked());
        buf.writeBoolean(view.crossDimension());
        buf.writeBoolean(view.hud());
    }
}
