// Author: dishanhai
package com.dishanhai.sh_common_lib.client.item;

import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes.Mode;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes.BossPolicy;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

public final class CosmicArtisanShortcut {
    private final KeyMapping mapping;
    private final CosmicArtisanGesture gesture = new CosmicArtisanGesture();
    private InputConstants.Key heldKey;
    private CosmicArtisanScreen wheel;

    public CosmicArtisanShortcut(KeyMapping mapping) {
        this.mapping = mapping;
    }

    public void input(InputConstants.Key key, int action) {
        var client = Minecraft.getInstance();
        if (action == GLFW.GLFW_RELEASE && key.equals(heldKey)) {
            heldKey = null;
            gesture.release(now());
        } else if (action == GLFW.GLFW_PRESS && heldKey == null && client.screen == null
                && canOpen(client) && mapping.isActiveAndMatches(key)) {
            heldKey = key;
            gesture.press(now());
        }
    }

    public void tick(Minecraft client, long window, boolean supported, ToIntFunction<ItemStack> read,
                     Consumer<Mode> toggle, BooleanSupplier aeAvailable, Function<ItemStack, BossPolicy> bossPolicy,
                     Consumer<BossPolicy> toggleBoss, Runnable toggleEmptyTeleport) {
        while (mapping.consumeClick()) {
            // Raw press and release events retain the timing even while the wheel is open.
        }
        if (!supported || !canOpen(client) || client.screen != wheel) {
            if (wheel != null && client.screen == wheel) client.setScreen(null);
            wheel = null;
            heldKey = null;
            gesture.cancel();
            return;
        }
        long now = now();
        if (heldKey != null && !isStillDown(window)) {
            heldKey = null;
            gesture.release(now);
        }
        switch (gesture.poll(now)) {
            case OPEN_CONFIG -> {
                AccelerationClient.openedConfig();
                client.setScreen(new CosmicArtisanScreen(read, toggle, aeAvailable.getAsBoolean(), false, bossPolicy, toggleBoss,
                        toggleEmptyTeleport));
            }
            case OPEN_WHEEL -> {
                wheel = new CosmicArtisanScreen(read, toggle, aeAvailable.getAsBoolean(), true, bossPolicy, toggleBoss,
                        toggleEmptyTeleport);
                client.setScreen(wheel);
            }
            case CLOSE_WHEEL -> {
                wheel = null;
                client.setScreen(null);
            }
            case NONE -> {}
        }
    }

    private boolean isStillDown(long window) {
        return switch (heldKey.getType()) {
            case KEYSYM -> GLFW.glfwGetKey(window, heldKey.getValue()) == GLFW.GLFW_PRESS;
            case MOUSE -> GLFW.glfwGetMouseButton(window, heldKey.getValue()) == GLFW.GLFW_PRESS;
            case SCANCODE -> true;
        };
    }

    private static boolean canOpen(Minecraft client) {
        return client.isWindowActive() && client.player != null && !client.player.isSpectator()
                && CosmicArtisanModes.isActive(client.player.getMainHandItem());
    }

    private static long now() {
        return System.nanoTime() / 1_000_000L;
    }
}
