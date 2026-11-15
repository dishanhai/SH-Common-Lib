package com.dishanhai.sh_common_lib.internal.item;

public final class ArtisanSettingsTest {
    public static void main(String[] args) {
        var defaults = ArtisanSettings.Profiles.DEFAULTS;
        if (!defaults.regular().enabled() || !defaults.cosmic().enabled()) throw new AssertionError("Items should default to enabled");
        var disabled = defaults.with(false, defaults.regular().withEnabled(false));
        if (disabled.regular().enabled() || !disabled.cosmic().enabled()) throw new AssertionError("Profiles must be independent");
        if (disabled.regular().withInfiniteDurability(true).enabled()) throw new AssertionError("Editing settings must preserve enabled state");
        if (disabled.regular().withUnlimitedMiningLevel(true).enabled()) throw new AssertionError("Mining settings must preserve enabled state");
        if (!disabled.with(true, disabled.cosmic().withEnabled(false)).regular().equals(disabled.regular())) throw new AssertionError("Updating cosmic must preserve regular settings");
    }
}
