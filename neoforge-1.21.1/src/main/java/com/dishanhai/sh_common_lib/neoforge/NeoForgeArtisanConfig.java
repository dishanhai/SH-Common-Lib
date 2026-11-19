package com.dishanhai.sh_common_lib.neoforge;

import com.dishanhai.sh_common_lib.internal.item.ArtisanConfig;
import com.dishanhai.sh_common_lib.internal.item.ArtisanSettings;
import com.dishanhai.sh_common_lib.internal.item.ArtisanSettings.Passives;
import com.dishanhai.sh_common_lib.internal.item.ArtisanSettings.Profiles;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;

final class NeoForgeArtisanConfig {
    private NeoForgeArtisanConfig() {}

    static void register(ModContainer container) {
        var builder = new ModConfigSpec.Builder();
        var regular = new Values(builder, ArtisanSettings.DEFAULT);
        builder.push("cosmic");
        var cosmic = new Values(builder, ArtisanSettings.COSMIC_DEFAULT);
        var passives = new PassiveValues(builder);
        builder.pop();
        var spec = builder.build();
        container.registerConfig(ModConfig.Type.SERVER, spec, ArtisanConfig.FILE_NAME);
        ArtisanConfig.bind(spec::isLoaded, () -> new Profiles(regular.read(), cosmic.read(), passives.read()), settings -> {
            regular.write(settings.regular());
            cosmic.write(settings.cosmic());
            passives.write(settings.passives());
        }, spec::save);
    }

    private record PassiveValues(ModConfigSpec.BooleanValue flight, ModConfigSpec.BooleanValue immunity,
                                 ModConfigSpec.BooleanValue food, ModConfigSpec.BooleanValue saturation) {
        PassiveValues(ModConfigSpec.Builder builder) {
            this(builder.define("flight", Passives.DEFAULT.flight()),
                    builder.define("damageImmunity", Passives.DEFAULT.damageImmunity()),
                    builder.define("fullFood", Passives.DEFAULT.fullFood()),
                    builder.define("fullSaturation", Passives.DEFAULT.fullSaturation()));
        }

        Passives read() {
            return new Passives(flight.get(), immunity.get(), food.get(), saturation.get());
        }

        void write(Passives settings) {
            flight.set(settings.flight());
            immunity.set(settings.damageImmunity());
            food.set(settings.fullFood());
            saturation.set(settings.fullSaturation());
        }
    }

    private record Values(ModConfigSpec.BooleanValue enabled, ModConfigSpec.BooleanValue infinite, ModConfigSpec.BooleanValue level,
                          ModConfigSpec.DoubleValue pickaxe, ModConfigSpec.DoubleValue axe, ModConfigSpec.DoubleValue shovel, ModConfigSpec.DoubleValue hoe,
                          ModConfigSpec.IntValue durability, ModConfigSpec.DoubleValue damage, ModConfigSpec.DoubleValue speed, ModConfigSpec.IntValue cost) {
        Values(ModConfigSpec.Builder builder, ArtisanSettings defaults) {
            this(builder.define("enabled", true), builder.define("infiniteDurability", defaults.infiniteDurability()),
                    builder.define("unlimitedMiningLevel", defaults.unlimitedMiningLevel()),
                    miningSpeed(builder, "pickaxeSpeed"), miningSpeed(builder, "axeSpeed"),
                    miningSpeed(builder, "shovelSpeed"), miningSpeed(builder, "hoeSpeed"),
                    builder.defineInRange("maxDurability", 250, 1, 100000),
                    builder.defineInRange("attackDamage", 9.0, 0.0, 1024.0), builder.defineInRange("attackSpeed", 0.9, 0.1, 16.0),
                    builder.defineInRange("useDurabilityCost", 1, 0, 1000));
        }

        private static ModConfigSpec.DoubleValue miningSpeed(ModConfigSpec.Builder builder, String key) {
            return builder.defineInRange(key, 1.0, ArtisanSettings.MIN_MINING_SPEED, ArtisanSettings.MAX_MINING_SPEED);
        }

        ArtisanSettings read() {
            return new ArtisanSettings(infinite.get(), level.get(), pickaxe.get(), axe.get(), shovel.get(), hoe.get(),
                    durability.get(), damage.get(), speed.get(), cost.get(), enabled.get());
        }

        void write(ArtisanSettings settings) {
            enabled.set(settings.enabled());
            infinite.set(settings.infiniteDurability());
            level.set(settings.unlimitedMiningLevel());
            pickaxe.set(settings.pickaxe());
            axe.set(settings.axe());
            shovel.set(settings.shovel());
            hoe.set(settings.hoe());
            durability.set(settings.maxDurability());
            damage.set(settings.attackDamage());
            speed.set(settings.attackSpeed());
            cost.set(settings.useCost());
        }
    }
}
