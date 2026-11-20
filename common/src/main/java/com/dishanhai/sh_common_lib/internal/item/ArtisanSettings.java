package com.dishanhai.sh_common_lib.internal.item;

public record ArtisanSettings(boolean infiniteDurability, boolean unlimitedMiningLevel,
                              double pickaxe, double axe, double shovel, double hoe,
                              int maxDurability, double attackDamage, double attackSpeed, int useCost, boolean enabled) {
    public static final double MIN_MINING_SPEED = 0.25;
    public static final double MAX_MINING_SPEED = 20.0;

    public static final ArtisanSettings DEFAULT = new ArtisanSettings(false, false);
    public static final ArtisanSettings COSMIC_DEFAULT = new ArtisanSettings(true, true);

    public ArtisanSettings(boolean infiniteDurability, boolean unlimitedMiningLevel) {
        this(infiniteDurability, unlimitedMiningLevel, 1, 1, 1, 1, 250, 9, 0.9, 1);
    }

    public ArtisanSettings(boolean infiniteDurability, boolean unlimitedMiningLevel, double pickaxe, double axe,
                           double shovel, double hoe, int maxDurability, double attackDamage, double attackSpeed, int useCost) {
        this(infiniteDurability, unlimitedMiningLevel, pickaxe, axe, shovel, hoe, maxDurability, attackDamage, attackSpeed, useCost, true);
    }

    public ArtisanSettings withEnabled(boolean value) {
        return new ArtisanSettings(infiniteDurability, unlimitedMiningLevel, pickaxe, axe, shovel, hoe,
                maxDurability, attackDamage, attackSpeed, useCost, value);
    }

    public ArtisanSettings withInfiniteDurability(boolean enabled) {
        return new ArtisanSettings(enabled, unlimitedMiningLevel, pickaxe, axe, shovel, hoe,
                maxDurability, attackDamage, attackSpeed, useCost, this.enabled);
    }

    public ArtisanSettings withUnlimitedMiningLevel(boolean enabled) {
        return new ArtisanSettings(infiniteDurability, enabled, pickaxe, axe, shovel, hoe,
                maxDurability, attackDamage, attackSpeed, useCost, this.enabled);
    }

    public record Passives(boolean flight, boolean damageImmunity, boolean fullFood, boolean fullSaturation,
                           double flightSpeedMultiplier) {
        public static final double MIN_FLIGHT_SPEED = 0.25;
        public static final double MAX_FLIGHT_SPEED = 4.0;
        public static final Passives DEFAULT = new Passives(true, true, true, true);

        public Passives {
            flightSpeedMultiplier = Double.isFinite(flightSpeedMultiplier)
                    ? Math.max(MIN_FLIGHT_SPEED, Math.min(MAX_FLIGHT_SPEED, flightSpeedMultiplier)) : 1.0;
        }

        public Passives(boolean flight, boolean damageImmunity, boolean fullFood, boolean fullSaturation) {
            this(flight, damageImmunity, fullFood, fullSaturation, 1.0);
        }

        public Passives withFlight(boolean enabled) { return new Passives(enabled, damageImmunity, fullFood, fullSaturation, flightSpeedMultiplier); }
        public Passives withDamageImmunity(boolean enabled) { return new Passives(flight, enabled, fullFood, fullSaturation, flightSpeedMultiplier); }
        public Passives withFullFood(boolean enabled) { return new Passives(flight, damageImmunity, enabled, fullSaturation, flightSpeedMultiplier); }
        public Passives withFullSaturation(boolean enabled) { return new Passives(flight, damageImmunity, fullFood, enabled, flightSpeedMultiplier); }
        public Passives withFlightSpeedMultiplier(double value) { return new Passives(flight, damageImmunity, fullFood, fullSaturation, value); }
    }

    public record Profiles(ArtisanSettings regular, ArtisanSettings cosmic, Passives passives) {
        public static final Profiles DEFAULTS = new Profiles(DEFAULT, COSMIC_DEFAULT, Passives.DEFAULT);

        public ArtisanSettings get(boolean cosmicItem) { return cosmicItem ? cosmic : regular; }

        public Profiles with(boolean cosmicItem, ArtisanSettings value) {
            return cosmicItem ? new Profiles(regular, value, passives) : new Profiles(value, cosmic, passives);
        }

        public Profiles withPassives(Passives value) {
            return new Profiles(regular, cosmic, value);
        }
    }
}
