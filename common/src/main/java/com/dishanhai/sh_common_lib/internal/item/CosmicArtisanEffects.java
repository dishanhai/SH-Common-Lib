package com.dishanhai.sh_common_lib.internal.item;

import com.dishanhai.sh_common_lib.internal.item.ArtisanSettings.Passives;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

public final class CosmicArtisanEffects {
    private CosmicArtisanEffects() {}

    public static boolean heldBy(ServerPlayer player, Item item) {
        return ArtisanConfig.current(true).enabled() && player.isAlive()
                && (player.getMainHandItem().is(item) || player.getOffhandItem().is(item));
    }

    public static boolean carriedBy(ServerPlayer player, Item item) {
        if (!ArtisanConfig.current(true).enabled() || !player.isAlive()) return false;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(item)) return true;
        }
        return false;
    }

    public static void replenishFood(ServerPlayer player, Passives settings) {
        var food = player.getFoodData();
        if (settings.fullFood() && food.getFoodLevel() < 20) food.setFoodLevel(20);
        if (settings.fullSaturation() && food.getSaturationLevel() < 20.0F) food.setSaturation(20.0F);
    }
}
