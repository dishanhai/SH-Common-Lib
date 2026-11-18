// Author: dishanhai
package com.dishanhai.sh_common_lib.client.item;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;

public final class AccelerationInput {
    private AccelerationInput() {}
    public static boolean mark(Minecraft client, boolean control) {
        var snapshot = AccelerationClient.snapshot();
        if (client.screen != null || !AccelerationClient.holding(client) || snapshot == null
                || !snapshot.settings().master() || snapshot.settings().editing() == 0) return false;
        if (client.hitResult instanceof BlockHitResult hit) {
            // The position is validated against the server-side owned range and interaction distance.
            AccelerationClient.mark(hit.getBlockPos(), control);
        }
        return true;
    }
}
