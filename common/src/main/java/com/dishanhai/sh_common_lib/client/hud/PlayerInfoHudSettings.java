// Author: dishanhai
package com.dishanhai.sh_common_lib.client.hud;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumSet;
import java.util.Properties;
import java.util.Set;

/** Immutable HUD configuration; persistence remains owned by the HUD. */
public record PlayerInfoHudSettings(boolean enabled, double x, double y, int scalePercent, Set<PlayerInfoItem> visibleItems,
                                    Set<HudGroups.Group> visibleGroups, boolean showGroupTitles, boolean showGroupSeparators) {
    static final int MIN_SCALE = 50;
    static final int MAX_SCALE = 200;
    static final int SCALE_STEP = 5;
    private static final int DEFAULT_SCALE = 75;
    public static final PlayerInfoHudSettings DEFAULT = fromProperties(new Properties());

    public PlayerInfoHudSettings {
        int bounded = Math.clamp(scalePercent, MIN_SCALE, MAX_SCALE);
        scalePercent = MIN_SCALE + Math.round((bounded - MIN_SCALE) / (float) SCALE_STEP) * SCALE_STEP;
        visibleItems = Set.copyOf(visibleItems);
        visibleGroups = Set.copyOf(visibleGroups);
    }

    public PlayerInfoHudSettings(boolean enabled, double x, double y, int scalePercent, Set<PlayerInfoItem> visibleItems) {
        this(enabled, x, y, scalePercent, visibleItems, EnumSet.allOf(HudGroups.Group.class), true, true);
    }

    public boolean shows(PlayerInfoItem item) {
        return visibleItems.contains(item);
    }

    public PlayerInfoHudSettings withEnabled(boolean value) {
        return new PlayerInfoHudSettings(value, x, y, scalePercent, visibleItems, visibleGroups, showGroupTitles, showGroupSeparators);
    }

    public PlayerInfoHudSettings withItem(PlayerInfoItem item, boolean visible) {
        var items = EnumSet.noneOf(PlayerInfoItem.class);
        items.addAll(visibleItems);
        if (visible) items.add(item);
        else items.remove(item);
        return new PlayerInfoHudSettings(enabled, x, y, scalePercent, items, visibleGroups, showGroupTitles, showGroupSeparators);
    }

    public boolean showsGroup(HudGroups.Group group) { return group == HudGroups.Group.CONTROLS || visibleGroups.contains(group); }

    public PlayerInfoHudSettings withGroup(HudGroups.Group group, boolean visible) {
        if (group == HudGroups.Group.CONTROLS) return this;
        var groups = EnumSet.noneOf(HudGroups.Group.class);
        groups.addAll(visibleGroups);
        if (visible) groups.add(group); else groups.remove(group);
        return new PlayerInfoHudSettings(enabled, x, y, scalePercent, visibleItems, groups, showGroupTitles, showGroupSeparators);
    }

    public PlayerInfoHudSettings withDecorations(boolean titles, boolean separators) {
        return new PlayerInfoHudSettings(enabled, x, y, scalePercent, visibleItems, visibleGroups, titles, separators);
    }

    public PlayerInfoHudSettings withLayout(boolean value, double newX, double newY, int scale) {
        return new PlayerInfoHudSettings(value, newX, newY, scale, visibleItems, visibleGroups, showGroupTitles, showGroupSeparators);
    }

    static PlayerInfoHudSettings read(Path file) throws IOException {
        Properties properties = new Properties();
        try (var input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (NoSuchFileException ignored) {
            return DEFAULT;
        }
        return fromProperties(properties);
    }

    private static PlayerInfoHudSettings fromProperties(Properties properties) {
        var items = EnumSet.noneOf(PlayerInfoItem.class);
        for (var item : PlayerInfoItem.values()) {
            if (readBoolean(properties, "show." + item.id(), item.defaultVisible())) items.add(item);
        }
        var groups = EnumSet.noneOf(HudGroups.Group.class);
        for (var group : HudGroups.Group.values()) {
            if (readBoolean(properties, "group." + group.name().toLowerCase(java.util.Locale.ROOT), true)) groups.add(group);
        }
        return new PlayerInfoHudSettings(readBoolean(properties, "enabled", true),
                readPosition(properties, "x"), readPosition(properties, "y"), readScale(properties), items, groups,
                readBoolean(properties, "groupTitles", true), readBoolean(properties, "groupSeparators", true));
    }

    private static int readScale(Properties properties) {
        try {
            return Integer.parseInt(properties.getProperty("scalePercent", "").trim());
        } catch (NumberFormatException ignored) {
            return DEFAULT_SCALE;
        }
    }

    private static boolean readBoolean(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key, "").trim();
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        return fallback;
    }

    private static double readPosition(Properties properties, String key) {
        try {
            double value = Double.parseDouble(properties.getProperty(key, "0"));
            return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    void write(Path file) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("enabled", Boolean.toString(enabled));
        properties.setProperty("x", Double.toString(x));
        properties.setProperty("y", Double.toString(y));
        properties.setProperty("scalePercent", Integer.toString(scalePercent));
        for (var item : PlayerInfoItem.values()) {
            properties.setProperty("show." + item.id(), Boolean.toString(shows(item)));
        }
        for (var group : HudGroups.Group.values()) {
            properties.setProperty("group." + group.name().toLowerCase(java.util.Locale.ROOT), Boolean.toString(showsGroup(group)));
        }
        properties.setProperty("groupTitles", Boolean.toString(showGroupTitles));
        properties.setProperty("groupSeparators", Boolean.toString(showGroupSeparators));
        Path directory = Files.createDirectories(file.getParent());
        Path pending = Files.createTempFile(directory, "sh_common_lib-player-info-", ".tmp");
        try {
            try (var output = Files.newOutputStream(pending)) {
                properties.store(output, "Player info HUD: x and y are fractions of the available screen space (0 to 1).");
            }
            try {
                Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(pending);
        }
    }
}
