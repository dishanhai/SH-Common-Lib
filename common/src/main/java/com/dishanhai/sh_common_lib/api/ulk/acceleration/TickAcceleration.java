// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.acceleration;

import com.dishanhai.sh_common_lib.internal.acceleration.AccelerationManager;
import net.minecraft.server.MinecraftServer;

/** Shared server-thread scheduler for additional tick work supplied by other mods. */
public final class TickAcceleration {
    private TickAcceleration() {}

    /**
     * Enqueues {@code count} invocations under the server-wide acceleration budget.
     * Returns false when its bounded queue is full. A callback must not block indefinitely;
     * an individual invocation cannot be interrupted after it begins.
     * Work is in-memory and is not restored after a server restart.
     */
    public static boolean submit(MinecraftServer server, Runnable tick, long count) {
        if (server == null) throw new IllegalArgumentException("server");
        return AccelerationManager.submit(server, tick, count);
    }
}
