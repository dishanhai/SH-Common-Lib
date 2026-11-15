package com.dishanhai.sh_common_lib.client.control;

import com.dishanhai.sh_common_lib.internal.item.ArtisanConfig;
import com.dishanhai.sh_common_lib.internal.item.ArtisanSettings.Passives;
import com.dishanhai.sh_common_lib.internal.item.ArtisanSettings.Profiles;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Predicate;

final class ArtisanControlSettings {
    private ArtisanControlSettings() {}

    static boolean editable() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        return server != null && !server.isPublished() && ArtisanConfig.available();
    }

    static boolean save(Profiles settings) {
        return editable() && ArtisanConfig.save(settings);
    }

    static SettingsGroup group(ControlCenterDraft draft) {
        var entries = new ArrayList<SettingsGroup.Entry>();
        addProfile(entries, draft, false);
        addProfile(entries, draft, true);
        return new SettingsGroup(profileTitle(false), GraphicIcon.SETTINGS, entries, true);
    }

    private static Component profileTitle(boolean cosmic) {
        return Component.translatable(cosmic ? "item.sh_common_lib.cosmic_artisan" : "item.sh_common_lib.artisan");
    }

    private static void addProfile(List<SettingsGroup.Entry> entries, ControlCenterDraft draft, boolean cosmic) {
        var title = profileTitle(cosmic);
        entries.add(new SettingsGroup.Heading(title));
        entries.add(new SettingsGroup.Toggle(enabled -> label("artisan_enabled", enabled),
                () -> draft.artisan(cosmic).enabled(),
                enabled -> draft.setArtisan(cosmic, draft.artisan(cosmic).withEnabled(enabled)),
                ArtisanControlSettings::editable));
        entries.add(
                new SettingsGroup.Toggle(enabled -> label("artisan_infinite_durability", enabled),
                        () -> draft.artisan(cosmic).infiniteDurability(),
                        enabled -> draft.setArtisan(cosmic, draft.artisan(cosmic).withInfiniteDurability(enabled)),
                        ArtisanControlSettings::editable));
        entries.add(
                new SettingsGroup.Toggle(enabled -> label("artisan_unlimited_mining_level", enabled),
                        () -> draft.artisan(cosmic).unlimitedMiningLevel(),
                        enabled -> draft.setArtisan(cosmic, draft.artisan(cosmic).withUnlimitedMiningLevel(enabled)),
                        ArtisanControlSettings::editable));
        if (cosmic) {
            entries.add(passive(draft, "artisan_flight", Passives::flight, Passives::withFlight));
            entries.add(passive(draft, "artisan_damage_immunity", Passives::damageImmunity, Passives::withDamageImmunity));
            entries.add(passive(draft, "artisan_full_food", Passives::fullFood, Passives::withFullFood));
            entries.add(passive(draft, "artisan_full_saturation", Passives::fullSaturation, Passives::withFullSaturation));
        } else {
            entries.add(new SettingsGroup.Toggle(enabled -> label("artisan_target_colors", enabled),
                    draft::artisanTargetColors, draft::setArtisanTargetColors));
        }
        entries.add(
                new SettingsGroup.Page(ControlCenterModule.label("artisan_advanced"), GraphicIcon.SETTINGS,
                        () -> editable() ? null : ControlCenterModule.label("artisan_server_owned"),
                        (parent, focus) -> {
                            var client = Minecraft.getInstance();
                            client.setScreen(ArtisanConfigPage.create(parent, title, cosmic, draft.artisan(cosmic), value -> {
                                if (!editable()) return false;
                                draft.setArtisan(cosmic, value);
                                return true;
                            }, focus));
                        }));
    }

    private static SettingsGroup.Toggle passive(ControlCenterDraft draft, String key, Predicate<Passives> read,
                                               BiFunction<Passives, Boolean, Passives> edit) {
        return new SettingsGroup.Toggle(enabled -> label(key, enabled),
                () -> read.test(draft.artisanPassives()),
                enabled -> draft.setArtisanPassives(edit.apply(draft.artisanPassives(), enabled)),
                ArtisanControlSettings::editable);
    }

    private static Component label(String setting, boolean enabled) {
        return ControlCenterModule.label(setting + (enabled ? "_on" : "_off"));
    }
}
