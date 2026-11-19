// Author: dishanhai
package com.dishanhai.sh_common_lib.client.control;

import com.dishanhai.sh_better_ui.kernel.api.config.ConfigPage;
import com.dishanhai.sh_better_ui.kernel.api.config.ConfigPage.NumberOption;
import com.dishanhai.sh_better_ui.kernel.api.config.ConfigPage.Preset;
import com.dishanhai.sh_common_lib.internal.item.ArtisanConfig;
import com.dishanhai.sh_common_lib.internal.item.ArtisanSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class ArtisanConfigPage {
    private static final List<NumberOption<ArtisanSettings>> OPTIONS = List.of(
            miningField(Field.PICKAXE), miningField(Field.AXE), miningField(Field.SHOVEL), miningField(Field.HOE),
            field(Field.DURABILITY, 1, 100000, true), field(Field.DAMAGE, 0, 1024, false),
            field(Field.SPEED, .1, 16, false), field(Field.COST, 0, 1000, true));
    private static final List<NumberOption<ArtisanSettings>> MINING_OPTIONS = OPTIONS.subList(0, 4);
    private static final List<Preset<ArtisanSettings>> PRESETS =
            List.of(preset(1), preset(2), preset(4), preset(10), preset(20));
    private static final List<NumberOption<ArtisanSettings>> COSMIC_OPTIONS = List.of(
            OPTIONS.get(0), OPTIONS.get(1), OPTIONS.get(2), OPTIONS.get(3), OPTIONS.get(4), OPTIONS.get(7));

    private ArtisanConfigPage() {}

    static Screen create(Screen parent, Component title, boolean cosmic, ArtisanSettings original, Predicate<ArtisanSettings> stage,
                         Supplier<? extends GuiEventListener> returnFocus) {
        var description = label("artisan_number_intro", label("artisan_number_highlight").copy().withStyle(ChatFormatting.BOLD));
        return new ConfigPage<>(title, description, cosmic ? COSMIC_OPTIONS : OPTIONS, PRESETS)
                .screen(parent, original, stage, returnFocus);
    }

    public static boolean miningSpeedEditable() {
        return ArtisanControlSettings.editable();
    }

    public static void openMiningSpeed(Screen parent, Component title, Supplier<? extends GuiEventListener> returnFocus) {
        if (!miningSpeedEditable()) return;
        var original = ArtisanConfig.current(true);
        var description = Component.translatable("screen.sh_common_lib.cosmic_artisan.mining_speed_hint");
        var page = new ConfigPage<>(title, description, MINING_OPTIONS, PRESETS);
        Minecraft.getInstance().setScreen(page.screen(parent, original, edited -> miningSpeedEditable()
                && ArtisanConfig.update(profiles -> profiles.with(true, mergeMiningSpeed(profiles.cosmic(), original, edited))),
                returnFocus));
    }

    private static ArtisanSettings mergeMiningSpeed(ArtisanSettings current, ArtisanSettings original, ArtisanSettings edited) {
        for (var option : MINING_OPTIONS) {
            double value = option.read().applyAsDouble(edited);
            if (Double.compare(value, option.read().applyAsDouble(original)) != 0) {
                current = option.edit().apply(current, value);
            }
        }
        return current;
    }

    private static NumberOption<ArtisanSettings> miningField(Field field) {
        return field(field, ArtisanSettings.MIN_MINING_SPEED, ArtisanSettings.MAX_MINING_SPEED, false);
    }

    private static NumberOption<ArtisanSettings> field(Field field, double min, double max, boolean integral) {
        String id = field.name().toLowerCase(Locale.ROOT);
        return new NumberOption<>(id, label("artisan_field_" + id), label("artisan_number_hint_" + id),
                min, max, integral, field::read, field::write);
    }

    private static Preset<ArtisanSettings> preset(int multiplier) {
        var values = new LinkedHashMap<NumberOption<ArtisanSettings>, Double>();
        for (var option : MINING_OPTIONS) values.put(option, (double) multiplier);
        return new Preset<>(label("artisan_preset_" + multiplier), label("artisan_preset_hint", multiplier), values);
    }

    private static Component label(String key, Object... args) {
        return Component.translatable("screen.sh_common_lib.control_center." + key, args);
    }

    private enum Field {
        PICKAXE, AXE, SHOVEL, HOE, DURABILITY, DAMAGE, SPEED, COST;

        double read(ArtisanSettings settings) {
            return switch (this) {
                case PICKAXE -> settings.pickaxe();
                case AXE -> settings.axe();
                case SHOVEL -> settings.shovel();
                case HOE -> settings.hoe();
                case DURABILITY -> settings.maxDurability();
                case DAMAGE -> settings.attackDamage();
                case SPEED -> settings.attackSpeed();
                case COST -> settings.useCost();
            };
        }

        ArtisanSettings write(ArtisanSettings settings, double value) {
            return new ArtisanSettings(settings.infiniteDurability(), settings.unlimitedMiningLevel(),
                    this == PICKAXE ? value : settings.pickaxe(), this == AXE ? value : settings.axe(),
                    this == SHOVEL ? value : settings.shovel(), this == HOE ? value : settings.hoe(),
                    this == DURABILITY ? (int) value : settings.maxDurability(),
                    this == DAMAGE ? value : settings.attackDamage(), this == SPEED ? value : settings.attackSpeed(),
                    this == COST ? (int) value : settings.useCost(), settings.enabled());
        }
    }
}
