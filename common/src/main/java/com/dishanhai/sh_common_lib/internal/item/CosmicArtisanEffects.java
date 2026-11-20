package com.dishanhai.sh_common_lib.internal.item;

import com.dishanhai.sh_common_lib.internal.item.ArtisanSettings.Passives;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.Item;

public final class CosmicArtisanEffects {
    private static final String ORIGINAL_FLIGHT_SPEED = "sh_common_lib.cosmic_artisan.original_flight_speed";
    private static final String APPLIED_FLIGHT_SPEED = "sh_common_lib.cosmic_artisan.applied_flight_speed";

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

    public static boolean updateFlightSpeed(ServerPlayer player, Passives settings) {
        if (settings == null || !settings.flight() || player.isCreative() || player.isSpectator()) {
            return clearFlightSpeed(player);
        }
        return updateFlightSpeed(player.getAbilities(), player.getPersistentData(), settings.flightSpeedMultiplier());
    }

    public static boolean clearFlightSpeed(ServerPlayer player) {
        return updateFlightSpeed(player.getAbilities(), player.getPersistentData(), 1.0);
    }

    static boolean updateFlightSpeed(Abilities abilities, CompoundTag data, double multiplier) {
        boolean restoring = multiplier == 1.0;
        if (restoring && !data.contains(APPLIED_FLIGHT_SPEED)) return false;

        float current = abilities.getFlyingSpeed();
        float original = originalFlightSpeed(data, current);
        float adjusted = (float) (original * multiplier);
        if (restoring) {
            data.remove(ORIGINAL_FLIGHT_SPEED);
            data.remove(APPLIED_FLIGHT_SPEED);
        }
        if (adjusted == current || !Float.isFinite(adjusted)) return false;
        if (!restoring) {
            // Player abilities are saved too. Keep the baseline across autosaves and abrupt exits.
            data.putFloat(ORIGINAL_FLIGHT_SPEED, original);
            data.putFloat(APPLIED_FLIGHT_SPEED, adjusted);
        }
        abilities.setFlyingSpeed(adjusted);
        return true;
    }

    private static float originalFlightSpeed(CompoundTag data, float current) {
        // When another system has changed the ability, use its value as the new baseline.
        if (!FloatTag.valueOf(current).equals(data.get(APPLIED_FLIGHT_SPEED))) return current;
        var original = data.get(ORIGINAL_FLIGHT_SPEED);
        return original == null ? current : Codec.FLOAT.parse(NbtOps.INSTANCE, original).result()
                .filter(Float::isFinite).orElse(current);
    }
}
