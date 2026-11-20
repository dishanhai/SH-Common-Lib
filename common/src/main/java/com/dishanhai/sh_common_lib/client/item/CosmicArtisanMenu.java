// Author: dishanhai
package com.dishanhai.sh_common_lib.client.item;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes.Mode;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes.BossPolicy;
import java.util.ArrayList;
import java.util.List;

/** One catalog shared by the settings list and the quick wheel. */
final class CosmicArtisanMenu {
    enum Kind { MODE, BOSS, EMPTY_TELEPORT, BEACONS, ACCELERATION, MINING_SPEED, FLIGHT_SPEED }
    record Option(String key, Kind kind, Mode mode, BossPolicy policy) {
        static Option mode(Mode mode) { return new Option(mode.translationKey(), Kind.MODE, mode, null); }
        static Option extra(String key, Kind kind) { return new Option(prefix(key), kind, null, null); }
        boolean toggle() {
            return switch (kind) {
                case MODE, BOSS, EMPTY_TELEPORT -> true;
                case BEACONS, ACCELERATION, MINING_SPEED, FLIGHT_SPEED -> false;
            };
        }
    }
    record Group(String name, GraphicIcon icon, List<Option> options) {
        Group { options = List.copyOf(options); }
    }
    private CosmicArtisanMenu() {}

    static List<Group> groups(boolean aeAvailable, boolean wheel) {
        var defaults = List.of(
                group("tools", GraphicIcon.TOOL, Mode.SILK_TOUCH, Mode.FORTUNE),
                group("combat", GraphicIcon.CRITICAL_WARNING, Mode.HOSTILE_FRIENDLY, Mode.FORCE_KILL,
                        Mode.BEHEADING, Mode.CAPTURE, Mode.AREA_DAMAGE, Mode.KILL_AURA, Mode.PROTECTION_LIST),
                group("auxiliary", GraphicIcon.SETTINGS, Mode.TIME_ACCELERATION, Mode.PLAYER_PROTECTION,
                        Mode.ADVANCED_STEALTH, Mode.MAGNET, Mode.AUTO_CLICK),
                group("travel", GraphicIcon.GOAL, Mode.TELEPORT, Mode.BEACON_HUD),
                group("mining", GraphicIcon.BLOCK, Mode.CONSTRUCTION_WAND, Mode.ANGEL_CORE, Mode.DESTRUCTION_CORE,
                        Mode.CHAIN_MINING, Mode.FORCE_MINING, Mode.AUTO_SMELT, Mode.AE_STORAGE_PRIORITY, Mode.AE_LINK, Mode.WRENCH_TAG),
                group("vanilla", GraphicIcon.MINECRAFT, Mode.HOE_PRIORITY, Mode.CROP_HARVEST, Mode.SHEARS,
                        Mode.FLINT_AND_STEEL, Mode.RIPEN, Mode.FORCE_GROW));
        var ordered = new ArrayList<Group>();
        for (String name : ArtisanAppearancePreferences.quickGroups().split(","))
            for (var group : defaults) if (group.name().equals(name) && !ordered.contains(group)) ordered.add(group);
        for (var group : defaults) if (!ordered.contains(group)) ordered.add(group);
        ordered.replaceAll(group -> {
            var options = new ArrayList<>(group.options().stream().filter(option -> aeAvailable
                    || option.mode() != Mode.AE_LINK && option.mode() != Mode.AE_STORAGE_PRIORITY).toList());
            switch (group.name()) {
                case "combat" -> {
                    options.add(new Option(prefix("boss_friendly"), Kind.BOSS, null, BossPolicy.FRIENDLY));
                    options.add(new Option(prefix("boss_hostile"), Kind.BOSS, null, BossPolicy.HOSTILE));
                }
                case "travel" -> {
                    options.add(Option.extra("empty_teleport", Kind.EMPTY_TELEPORT));
                    options.add(Option.extra("beacon_destinations", Kind.BEACONS));
                }
                case "auxiliary" -> {
                    if (!wheel) options.add(0, Option.extra("flight_speed", Kind.FLIGHT_SPEED));
                    options.add(Option.extra("acceleration_config", Kind.ACCELERATION));
                }
                case "mining" -> {
                    if (!wheel) options.add(0, Option.extra("mining_speed", Kind.MINING_SPEED));
                }
                default -> {}
            }
            return new Group(group.name(), group.icon(), options);
        });
        if (wheel) ordered.add(0, group("quick", GraphicIcon.QUICK, Mode.FORCE_KILL, Mode.SILK_TOUCH,
                Mode.FORTUNE, Mode.CHAIN_MINING, Mode.AUTO_SMELT, Mode.MAGNET));
        return ordered;
    }
    private static Group group(String name, GraphicIcon icon, Mode... modes) {
        return new Group(name, icon, java.util.Arrays.stream(modes).map(Option::mode).toList());
    }
    static String prefix(String key) { return "screen.sh_common_lib.cosmic_artisan." + key; }
}
