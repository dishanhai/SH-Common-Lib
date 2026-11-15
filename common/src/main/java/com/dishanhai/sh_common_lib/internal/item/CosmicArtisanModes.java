// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.item;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

public final class CosmicArtisanModes {
    public static final int ATTACK_DAMAGE = 10000;
    public static final int ATTACK_SPEED = 40;
    public static final int FORTUNE_LEVEL = 3;
    public static final String TAG = ShCommonLib.MOD_ID + ":cosmic_modes";
    public static final String BEACON_PROJECTION_TAG = ShCommonLib.MOD_ID + ":beacon_projection";

    private CosmicArtisanModes() {}

    public static int readFlags(int stored, boolean beaconProjection) {
        int projectionBit = Mode.BEACON_HUD.bit();
        // Older stacks used bit 30 for disabling the default-on marker.
        return beaconProjection ? stored | projectionBit : stored & ~projectionBit;
    }

    public static void writeFlags(CompoundTag tag, int flags) {
        tag.putInt(TAG, readFlags(flags, false));
        tag.putBoolean(BEACON_PROJECTION_TAG, Mode.BEACON_HUD.enabled(flags));
    }

    public static boolean isCosmic(ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && ShCommonLib.MOD_ID.equals(id.getNamespace()) && Artisan.COSMIC_ID.equals(id.getPath());
    }

    public static boolean isActive(ItemStack stack) {
        return isCosmic(stack) && ArtisanConfig.current(true).enabled();
    }

    public static final String BOSS_POLICY_TAG = ShCommonLib.MOD_ID + ":cosmic_boss_policy";

    public enum BossPolicy {
        NEUTRAL(0), FRIENDLY(1), HOSTILE(2);

        private final int id;
        BossPolicy(int id) { this.id = id; }
        public int id() { return id; }
        public int request() { return -id; }
        public static BossPolicy fromId(int id) {
            return switch (id) {
                case 1 -> FRIENDLY;
                case 2 -> HOSTILE;
                default -> NEUTRAL;
            };
        }
    }

    public enum Mode {
        SILK_TOUCH(0, "silk_touch"),
        FORTUNE(1, "fortune"),
        FORCE_KILL(2, "force_kill"),
        BEHEADING(3, "beheading"),
        AREA_DAMAGE(4, "area_damage"),
        KILL_AURA(5, "kill_aura"),
        PROTECTION_LIST(6, "protection_list"),
        PLAYER_PROTECTION(7, "player_protection", true),
        MAGNET(8, "magnet", true),
        AUTO_CLICK(9, "auto_click"),
        HOE_PRIORITY(10, "hoe_priority"),
        CROP_HARVEST(11, "crop_harvest", true),
        SHEARS(12, "shears", true),
        FLINT_AND_STEEL(13, "flint_and_steel", true),
        RIPEN(14, "ripen"),
        FORCE_GROW(15, "force_grow"),
        WRENCH_TAG(16, "wrench_tag", true),
        CHAIN_MINING(17, "chain_mining"),
        TELEPORT(18, "teleport"),
        AUTO_SMELT(19, "auto_smelt"),
        TIME_ACCELERATION(20, "time_acceleration"),
        ENTITY_ACCELERATION(21, "entity_acceleration"),
        ADVANCED_STEALTH(22, "advanced_stealth"),
        FORCE_MINING(23, "force_mining"),
        CAPTURE(24, "capture"),
        CONSTRUCTION_WAND(25, "construction_wand"),
        ANGEL_CORE(26, "angel_core"),
        DESTRUCTION_CORE(27, "destruction_core"),
        AE_STORAGE_PRIORITY(28, "ae_storage_priority"),
        AE_LINK(29, "ae_link"),
        BEACON_HUD(30, "beacon_hud"),
        HOSTILE_FRIENDLY(31, "hostile_friendly");

        private final int bit;
        private final String key;
        private final boolean defaultEnabled;

        Mode(int index, String key) {
            this(index, key, false);
        }

        Mode(int index, String key, boolean defaultEnabled) {
            this.bit = 1 << index;
            this.key = key;
            this.defaultEnabled = defaultEnabled;
        }

        public boolean enabled(int flags) { return defaultEnabled != ((flags & bit) != 0); }
        public int toggle(int flags) {
            int updated = flags ^ bit;
            if (enabled(updated)) {
                if (this == ANGEL_CORE) return updated & ~DESTRUCTION_CORE.bit;
                if (this == DESTRUCTION_CORE) return updated & ~ANGEL_CORE.bit;
            }
            return updated;
        }
        public int bit() { return bit; }
        public String translationKey() { return "screen.sh_common_lib.cosmic_artisan." + key; }

        public static Mode fromBit(int bit) {
            for (Mode mode : values()) {
                if (mode.bit == bit) return mode;
            }
            return null;
        }
    }
}
