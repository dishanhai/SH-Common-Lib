package com.dishanhai.sh_common_lib.client.hud;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public final class PlayerInfoHud {
    static final String LANG = "hud.sh_common_lib.player_info.";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MARGIN = 4;
    private static final int PADDING = 4;
    private static final int ICON_SIZE = GraphicIcon.PIXELS / 2;
    private static final int TEXT_X = PADDING + ICON_SIZE + 2;
    private static final String[] HEADINGS = {
            "south", "south_west", "west", "north_west", "north", "north_east", "east", "south_east"
    };

    private final Path settingsFile;
    private final KeyMapping editKey = new KeyMapping("key.sh_common_lib.player_info", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8, "key.categories.sh_common_lib");
    private PlayerInfoHudSettings settings = PlayerInfoHudSettings.DEFAULT;
    private LocalPlayer sampledPlayer;
    private Panel panel;
    private long nextSample;

    public PlayerInfoHud(Path configDirectory) {
        settingsFile = configDirectory.resolve("sh_common_lib-player-info.properties");
        try {
            settings = PlayerInfoHudSettings.read(settingsFile);
        } catch (IOException | IllegalArgumentException error) {
            LOGGER.warn("Could not read player info HUD settings from {}", settingsFile, error);
        }
    }

    public KeyMapping editKey() {
        return editKey;
    }

    public void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            sampledPlayer = null;
            panel = null;
        }
        if (!editKey.consumeClick()) return;
        while (editKey.consumeClick()) {
            // Open only one editor when several key presses accumulate before the next tick.
        }
        openEditor(null, () -> null);
    }

    public void openEditor(Screen parent, Supplier<? extends GuiEventListener> returnFocus) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen == parent && client.player != null && client.level != null) {
            panel = null;
            client.setScreen(new PlayerInfoHudScreen(this, settings, sample(client), parent, returnFocus));
        }
    }

    public boolean isEnabled() {
        return settings.enabled();
    }

    public boolean setEnabled(boolean enabled) {
        return save(new PlayerInfoHudSettings(enabled, settings.x(), settings.y()));
    }

    public void render(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (!settings.enabled() || client.options.hideGui || client.screen != null
                || client.player == null || client.level == null) return;

        Panel current = sample(client);
        Placement placement = current.fit(graphics.guiWidth(), graphics.guiHeight());
        current.draw(graphics, client.font, placement.x(settings.x()), placement.y(settings.y()), placement.scale());
    }

    boolean save(PlayerInfoHudSettings updated) {
        try {
            updated.write(settingsFile);
            settings = updated;
            panel = null;
            return true;
        } catch (IOException error) {
            LOGGER.warn("Could not save player info HUD settings to {}", settingsFile, error);
            return false;
        }
    }

    private Panel sample(Minecraft client) {
        long now = Util.getMillis();
        if (panel != null && sampledPlayer == client.player && now < nextSample) return panel;
        sampledPlayer = client.player;
        nextSample = now + 250;
        ClientLevel level = client.level;
        LocalPlayer player = client.player;
        BlockPos pos = player.blockPosition();
        ChunkPos chunk = player.chunkPosition();
        List<Row> rows = new ArrayList<>();

        PlayerInfo info = player.connection.getPlayerInfo(player.getUUID());
        Component latency = info == null || client.hasSingleplayerServer()
                ? Component.empty() : line("latency", value(info.getLatency()));
        rows.add(HudIcon.SIGNAL.row(client, line("player", value(player.getGameProfile().getName()), value(client.getFps()), latency)));
        LocalTime localTime = LocalTime.now();
        rows.add(HudIcon.CLOCK.row(client, line("local_time", value(clock(localTime.getHour(), localTime.getMinute())))));
        long dayTime = level.getDayTime();
        int gameMinutes = (int) ((Math.floorMod(dayTime, 24_000L) * 60 / 1_000 + 360) % 1_440);
        Component dayPhase = level.dimensionType().hasSkyLight() && !level.dimensionType().hasFixedTime()
                ? line(level.isDay() ? "day" : "night").withStyle(ChatFormatting.YELLOW) : Component.empty();
        rows.add(HudIcon.CALENDAR.row(client, line("world_time", value(Math.floorDiv(dayTime, 24_000L) + 1),
                value(clock(gameMinutes / 60, gameMinutes % 60)), dayPhase)));

        ResourceLocation dimension = level.dimension().location();
        Component dimensionName = switch (dimension.toString()) {
            case "minecraft:overworld" -> line("overworld");
            case "minecraft:the_nether" -> line("nether");
            case "minecraft:the_end" -> line("end");
            default -> Component.translatableWithFallback(dimension.toLanguageKey("dimension"), dimension.toString());
        };
        rows.add(HudIcon.WORLD.row(client, line("world", value(dimensionName),
                value(level.getChunkSource().getLoadedChunksCount()), value(level.getEntityCount()))));
        var biome = level.getBiome(pos);
        Component biomeName = biome.unwrapKey().map(key -> {
            ResourceLocation id = key.location();
            return Component.translatableWithFallback(id.toLanguageKey("biome"), id.toString());
        }).orElseGet(() -> line("unknown"));
        Biome biomeData = biome.value();
        rows.add(HudIcon.BIOME.row(client, line("biome", value(biomeName),
                value(String.format(Locale.ROOT, "%.2f", biomeData.getBaseTemperature())))));

        int heading = Mth.floor(player.getYRot() / 45.0 + 0.5) & 7;
        rows.add(HudIcon.LOCATION.row(client, line("position", value(pos.getX()), value(pos.getY()), value(pos.getZ()),
                value(line(HEADINGS[heading])))));
        rows.add(HudIcon.CHUNK.row(client, line("chunk", value(chunk.x), value(chunk.z),
                value(Math.floorMod(pos.getX(), 16)), value(Math.floorMod(pos.getZ(), 16)))));
        if (level.getChunkSource().hasChunk(chunk.x, chunk.z)) {
            rows.add(HudIcon.TORCH.row(client, line("light", value(level.getMaxLocalRawBrightness(pos)),
                    value(level.getBrightness(LightLayer.SKY, pos)), value(level.getBrightness(LightLayer.BLOCK, pos)))));
        }

        int textWidth = rows.stream().mapToInt(row -> client.font.width(row.text())).max().orElse(0);
        panel = new Panel(rows, TEXT_X + textWidth + PADDING, rows.size() * rowHeight(client.font) + 2 * PADDING);
        return panel;
    }

    private static String clock(int hour, int minute) {
        return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
    }

    private static MutableComponent line(String key, Object... args) {
        return Component.translatable(LANG + key, args);
    }

    private static Component value(Object value) {
        return (value instanceof Component text ? text.copy() : Component.literal(String.valueOf(value)))
                .withStyle(ChatFormatting.GOLD);
    }

    private static int rowHeight(Font font) {
        return Math.max(ICON_SIZE, font.lineHeight) + 1;
    }

    private enum HudIcon {
        SIGNAL(GraphicIcon.STATISTICS, 0xFF65F06B),
        CLOCK(GraphicIcon.CLOCK, 0xFFDBE4EF),
        CALENDAR(GraphicIcon.CALENDAR, 0xFFFFD36A),
        WORLD(GraphicIcon.BLOCK, 0xFF75C8FF),
        BIOME(GraphicIcon.MAP, 0xFF87D581),
        LOCATION(GraphicIcon.PIN, 0xFFFF9B82),
        CHUNK(GraphicIcon.TOPOLOGY, 0xFF9EC4D2),
        TORCH(GraphicIcon.LIGHT_MODE, 0xFFFFB94F);

        private final GraphicIcon compact;
        private final GraphicIcon fallback;
        private final int color;

        HudIcon(GraphicIcon fallback, int color) {
            // Resolve the texture by ID so older supported SH-Better-UI JARs can still use the fallback.
            this.compact = new GraphicIcon(fallback.texture().withPath("textures/gui/display/compact_"
                    + name().toLowerCase(Locale.ROOT) + ".png"));
            this.fallback = fallback;
            this.color = color;
        }

        Row row(Minecraft client, Component text) {
            GraphicIcon icon = client.getResourceManager().getResource(compact.texture()).isPresent() ? compact : fallback;
            return new Row(icon, color, text);
        }
    }

    private record Row(GraphicIcon icon, int color, Component text) {}

    record Panel(List<Row> rows, int width, int height) {
        Placement fit(int screenWidth, int screenHeight) {
            int availableWidth = Math.max(1, screenWidth - 2 * MARGIN);
            int availableHeight = Math.max(1, screenHeight - 2 * MARGIN);
            float scale = Math.min(1, Math.min((float) availableWidth / width, (float) availableHeight / height));
            int drawnWidth = Math.min(availableWidth, (int) Math.ceil(width * scale));
            int drawnHeight = Math.min(availableHeight, (int) Math.ceil(height * scale));
            return new Placement(drawnWidth, drawnHeight, availableWidth - drawnWidth, availableHeight - drawnHeight, scale);
        }

        void draw(GuiGraphics graphics, Font font, int x, int y, float scale) {
            var pose = graphics.pose();
            pose.pushPose();
            pose.translate(x, y, 0);
            pose.scale(scale, scale, 1);
            graphics.fill(0, 0, width, height, 0xB0101010);
            int rowHeight = rowHeight(font);
            int iconY = PADDING + (rowHeight - 1 - ICON_SIZE) / 2;
            pose.pushPose();
            pose.scale(0.5f, 0.5f, 1);
            for (Row row : rows) {
                row.icon().draw(graphics, PADDING * 2, iconY * 2, 1, row.color());
                iconY += rowHeight;
            }
            pose.popPose();
            int lineY = PADDING + (rowHeight - 1 - font.lineHeight) / 2;
            for (Row row : rows) {
                graphics.drawString(font, row.text(), TEXT_X, lineY, 0xFFFFFF, true);
                lineY += rowHeight;
            }
            pose.popPose();
        }
    }

    record Placement(int width, int height, int travelX, int travelY, float scale) {
        int x(double fraction) { return MARGIN + (int) Math.round(travelX * fraction); }
        int y(double fraction) { return MARGIN + (int) Math.round(travelY * fraction); }

        double moveX(double x, double current) { return fraction(x, travelX, current); }
        double moveY(double y, double current) { return fraction(y, travelY, current); }

        private static double fraction(double coordinate, int travel, double current) {
            return travel == 0 ? current : Math.max(0, Math.min(1, (coordinate - MARGIN) / travel));
        }
    }
}
