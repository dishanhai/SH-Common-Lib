// Author: dishanhai
package com.dishanhai.sh_common_lib.forge;

import net.minecraft.server.level.ServerLevel;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Forge 1.20.1 ships SRG names at runtime but uses Mojang names in development. */
final class ForgeWeatherAccess {
    private static final Method ADVANCE = findAdvance();
    private ForgeWeatherAccess() {}

    private static Method findAdvance() {
        for (String name : new String[] {"advanceWeatherCycle", "m_184096_"}) {
            try {
                var method = ServerLevel.class.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // Try the name used by the other Forge runtime.
            }
        }
        throw new IllegalStateException("Cannot find the server weather tick method");
    }

    static void advance(ServerLevel level) {
        try {
            ADVANCE.invoke(level);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access the server weather tick method", e);
        } catch (InvocationTargetException e) {
            var cause = e.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Server weather tick failed", cause);
        }
    }
}
