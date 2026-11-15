package com.dishanhai.sh_common_lib.forge;

import com.dishanhai.sh_common_lib.internal.item.ArtisanConfig;
import com.dishanhai.sh_common_lib.internal.item.ArtisanSettings;
import com.dishanhai.sh_common_lib.internal.item.ArtisanSettings.Passives;
import com.dishanhai.sh_common_lib.internal.item.ArtisanSettings.Profiles;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

final class ForgeArtisanConfig {
    private ForgeArtisanConfig() {}

    static void register(FMLJavaModLoadingContext context) {
        var builder = new ForgeConfigSpec.Builder();
        var regular = new Values(builder, ArtisanSettings.DEFAULT);
        builder.push("cosmic");
        var cosmic = new Values(builder, ArtisanSettings.COSMIC_DEFAULT);
        var passives = new PassiveValues(builder);
        builder.pop();
        var spec = builder.build();
        context.registerConfig(ModConfig.Type.SERVER, spec, ArtisanConfig.FILE_NAME);
        ArtisanConfig.bind(spec::isLoaded, () -> new Profiles(regular.read(), cosmic.read(), passives.read()), settings -> {
            regular.write(settings.regular());
            cosmic.write(settings.cosmic());
            passives.write(settings.passives());
        }, spec::save);
    }

    private record PassiveValues(ForgeConfigSpec.BooleanValue flight, ForgeConfigSpec.BooleanValue immunity,
                                 ForgeConfigSpec.BooleanValue food, ForgeConfigSpec.BooleanValue saturation) {
        PassiveValues(ForgeConfigSpec.Builder builder) {
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

    private record Values(ForgeConfigSpec.BooleanValue enabled, ForgeConfigSpec.BooleanValue infinite, ForgeConfigSpec.BooleanValue level,
                          ForgeConfigSpec.DoubleValue pickaxe, ForgeConfigSpec.DoubleValue axe, ForgeConfigSpec.DoubleValue shovel, ForgeConfigSpec.DoubleValue hoe,
                          ForgeConfigSpec.IntValue durability, ForgeConfigSpec.DoubleValue damage, ForgeConfigSpec.DoubleValue speed, ForgeConfigSpec.IntValue cost) {
        Values(ForgeConfigSpec.Builder builder, ArtisanSettings defaults) {
            this(builder.define("enabled", true), builder.define("infiniteDurability", defaults.infiniteDurability()),
                    builder.define("unlimitedMiningLevel", defaults.unlimitedMiningLevel()),
                    builder.defineInRange("pickaxeSpeed", 1.0, 0.25, 16.0), builder.defineInRange("axeSpeed", 1.0, 0.25, 16.0),
                    builder.defineInRange("shovelSpeed", 1.0, 0.25, 16.0), builder.defineInRange("hoeSpeed", 1.0, 0.25, 16.0),
                    builder.defineInRange("maxDurability", 250, 1, 100000),
                    builder.defineInRange("attackDamage", 9.0, 0.0, 1024.0), builder.defineInRange("attackSpeed", 0.9, 0.1, 16.0),
                    builder.defineInRange("useDurabilityCost", 1, 0, 1000));
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
