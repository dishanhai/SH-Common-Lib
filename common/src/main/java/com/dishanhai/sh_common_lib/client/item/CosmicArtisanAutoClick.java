// Author: dishanhai
package com.dishanhai.sh_common_lib.client.item;

import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes.Mode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.function.Function;

public final class CosmicArtisanAutoClick {
    private static final int CLICKS_PER_TICK = 4;
    private static ClientPacketListener connection;
    private static boolean enabled;

    private CosmicArtisanAutoClick() {}

    public static int flags(int serverFlags) {
        int flags = serverFlags & ~Mode.AUTO_CLICK.bit();
        return enabled ? flags | Mode.AUTO_CLICK.bit() : flags;
    }

    public static void toggle() {
        enabled = !enabled;
    }

    public static void tick(Minecraft client, Function<EntityHitResult, InteractionResult> interact) {
        if (connection != client.getConnection()) {
            connection = client.getConnection();
            enabled = false;
        }
        var player = client.player;
        var gameMode = client.gameMode;
        if (!enabled || player == null || gameMode == null || client.screen != null || client.isPaused()
                || !client.isWindowActive() || player.isSpectator() || player.isHandsBusy() || player.isUsingItem()
                || gameMode.isDestroying() || !CosmicArtisanModes.isActive(player.getMainHandItem())) return;
        var hand = InteractionHand.MAIN_HAND;
        for (int i = 0; i < CLICKS_PER_TICK && !player.isUsingItem(); i++) {
            var hit = client.hitResult;
            InteractionResult result = hit instanceof EntityHitResult entity ? interact.apply(entity)
                    : hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                        ? gameMode.useItemOn(player, hand, block) : InteractionResult.PASS;
            if (result == InteractionResult.PASS) result = gameMode.useItem(player, hand);
            if (!result.consumesAction()) break;
            player.swing(hand);
        }
    }
}
