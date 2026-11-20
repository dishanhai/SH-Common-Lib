package com.dishanhai.sh_common_lib.internal.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.world.entity.player.Abilities;

public final class ArtisanSettingsTest {
    public static void main(String[] args) {
        var defaults = ArtisanSettings.Profiles.DEFAULTS;
        if (!defaults.regular().enabled() || !defaults.cosmic().enabled()) throw new AssertionError("Items should default to enabled");
        var disabled = defaults.with(false, defaults.regular().withEnabled(false));
        if (disabled.regular().enabled() || !disabled.cosmic().enabled()) throw new AssertionError("Profiles must be independent");
        if (disabled.regular().withInfiniteDurability(true).enabled()) throw new AssertionError("Editing settings must preserve enabled state");
        if (disabled.regular().withUnlimitedMiningLevel(true).enabled()) throw new AssertionError("Mining settings must preserve enabled state");
        if (!disabled.with(true, disabled.cosmic().withEnabled(false)).regular().equals(disabled.regular())) throw new AssertionError("Updating cosmic must preserve regular settings");
        verifyFlightSpeedSettings(defaults.passives());
        verifyFlightSpeedLifecycle();
    }

    private static void verifyFlightSpeedSettings(ArtisanSettings.Passives defaults) {
        if (defaults.flightSpeedMultiplier() != 1.0) throw new AssertionError("Old configurations must use standard flight speed");
        if (defaults.withFlightSpeedMultiplier(0.0).flightSpeedMultiplier() != 0.25) throw new AssertionError("Flight speed must respect its lower bound");
        if (defaults.withFlightSpeedMultiplier(100.0).flightSpeedMultiplier() != 4.0) throw new AssertionError("Flight speed must respect its upper bound");
        if (defaults.withFlightSpeedMultiplier(Double.NaN).flightSpeedMultiplier() != 1.0) throw new AssertionError("Invalid flight speed must use the default");
        var edited = defaults.withFlightSpeedMultiplier(2.5);
        var toggled = edited.withFlight(false).withDamageImmunity(false).withFullFood(false).withFullSaturation(false);
        if (toggled.flightSpeedMultiplier() != 2.5) throw new AssertionError("Passive toggles must preserve the configured flight speed");
        if (toggled.withFlightSpeedMultiplier(0.5).flight()) throw new AssertionError("Editing flight speed must preserve the flight toggle");
    }

    private static void verifyFlightSpeedLifecycle() {
        var abilities = new Abilities();
        var data = new CompoundTag();
        float baseline = abilities.getFlyingSpeed();
        checkSpeed(abilities, data, 1.0, baseline, false);
        checkSpeed(abilities, data, 2.0, baseline * 2, true);
        checkSpeed(abilities, data, 2.0, baseline * 2, false);
        checkSpeed(abilities, data, 0.5, baseline / 2, true);
        if (new ClientboundPlayerAbilitiesPacket(abilities).getFlyingSpeed() != baseline / 2) {
            throw new AssertionError("The vanilla abilities packet must include the adjusted speed");
        }

        // An autosave can capture the adjusted ability together with the two persisted speeds.
        var reloaded = new Abilities();
        reloaded.setFlyingSpeed(abilities.getFlyingSpeed());
        var saved = data.copy();
        checkSpeed(reloaded, saved, 0.5, baseline / 2, false);
        checkSpeed(reloaded, saved, 1.0, baseline, true);
        checkSpeed(reloaded, saved, 1.0, baseline, false);
        if (!saved.isEmpty()) throw new AssertionError("Restoring speed must clear the saved markers");

        abilities.setFlyingSpeed(0.04F);
        checkSpeed(abilities, data, 2.0, 0.08F, true);
        checkSpeed(abilities, data, 1.0, 0.04F, true);
        checkSpeed(abilities, data, 2.0, 0.08F, true);
        abilities.setFlyingSpeed(0.06F);
        checkSpeed(abilities, data, 1.0, 0.06F, false);
    }

    private static void checkSpeed(Abilities abilities, CompoundTag data, double multiplier, float expected, boolean changed) {
        if (CosmicArtisanEffects.updateFlightSpeed(abilities, data, multiplier) != changed
                || abilities.getFlyingSpeed() != expected) {
            throw new AssertionError("Expected flight speed " + expected + " after applying multiplier " + multiplier);
        }
    }
}
