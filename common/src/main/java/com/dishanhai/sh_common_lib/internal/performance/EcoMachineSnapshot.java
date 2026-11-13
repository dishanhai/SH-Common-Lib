// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.performance;

import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Versioned, bounded telemetry for machines visible through the player's AE network. */
public record EcoMachineSnapshot(String dimension, int mask, int state, boolean truncated, List<Machine> machines) {
    public static final int NOT_INSTALLED = 0, NO_TERMINAL = 1, CONNECTED = 2, ERROR = 3;
    public static final int CRAFTING = 0, COMPUTATION = 1, STORAGE = 2, WORKSTATION = 3;
    public static final int MAX_MACHINES = 32;
    private static final WeakHashMap<ServerPlayer, Long> LAST_REQUEST = new WeakHashMap<>();
    public record Machine(int type, String dimension, int x, int y, int z, boolean active,
                          long first, long second, long third, long fourth) {}
    public EcoMachineSnapshot { machines = List.copyOf(machines); }

    public static boolean allow(ServerPlayer player) {
        long now = System.nanoTime();
        Long last = LAST_REQUEST.get(player);
        if (last != null && now - last < TimeUnit.SECONDS.toNanos(20)) return false;
        LAST_REQUEST.put(player, now);
        return true;
    }
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(dimension, 256).writeVarInt(mask).writeVarInt(state).writeBoolean(truncated);
        buf.writeVarInt(machines.size());
        for (var machine : machines) {
            buf.writeVarInt(machine.type()).writeUtf(machine.dimension(), 256)
                    .writeInt(machine.x()).writeInt(machine.y()).writeInt(machine.z()).writeBoolean(machine.active())
                    .writeLong(machine.first()).writeLong(machine.second()).writeLong(machine.third()).writeLong(machine.fourth());
        }
    }
    public static EcoMachineSnapshot decode(FriendlyByteBuf buf) {
        String dimension = buf.readUtf(256);
        int mask = buf.readVarInt(), state = buf.readVarInt();
        boolean truncated = buf.readBoolean();
        int count = buf.readVarInt();
        if (mask < 1 || mask > 15 || state < NOT_INSTALLED || state > ERROR || count < 0 || count > MAX_MACHINES)
            throw new IllegalArgumentException("Invalid ECO machine HUD snapshot");
        var machines = new java.util.ArrayList<Machine>(count);
        for (int i = 0; i < count; i++) {
            int type = buf.readVarInt();
            String machineDimension = buf.readUtf(256);
            int x = buf.readInt(), y = buf.readInt(), z = buf.readInt();
            boolean active = buf.readBoolean();
            long first = buf.readLong(), second = buf.readLong(), third = buf.readLong(), fourth = buf.readLong();
            if (type < CRAFTING || type > WORKSTATION || (mask & (1 << type)) == 0
                    || first < -1 || second < -1 || third < -1 || fourth < -1)
                throw new IllegalArgumentException("Invalid ECO machine HUD entry");
            machines.add(new Machine(type, machineDimension, x, y, z, active, first, second, third, fourth));
        }
        return new EcoMachineSnapshot(dimension, mask, state, truncated, machines);
    }
}
