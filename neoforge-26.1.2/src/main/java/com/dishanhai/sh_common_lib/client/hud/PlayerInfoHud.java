package com.dishanhai.sh_common_lib.client.hud;

import com.dishanhai.sh_better_ui.kernel.api.system.ComputerStatusService;
import com.dishanhai.sh_common_lib.api.client.ComputerStatus;
import com.dishanhai.sh_common_lib.client.control.ControlCenterModule;
import com.dishanhai.sh_common_lib.client.ClientPanels;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.dishanhai.sh_common_lib.internal.performance.ClientTickMonitor;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import net.minecraft.util.Util;
import com.dishanhai.sh_common_lib.neoforge.NeoForgeKeyMappings;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.dishanhai.sh_common_lib.client.hud.PlayerInfoItem.*;

public final class PlayerInfoHud {
    static final String LANG = "hud.sh_common_lib.player_info.";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String[] HEADINGS = {
            "south", "south_west", "west", "north_west", "north", "north_east", "east", "south_east"
    };

    private final Path settingsFile;
    private final Consumer<Connection> tickRequest;
    private final BooleanSupplier debugVisible;
    private final KeyMapping editKey = new KeyMapping("key.sh_common_lib.player_info", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F6, NeoForgeKeyMappings.CATEGORY);
    private PlayerInfoHudSettings settings = PlayerInfoHudSettings.DEFAULT;
    private LocalPlayer sampledPlayer;
    private PlayerInfoHudLayout layout;
    private long nextSample;

    public PlayerInfoHud(Path configDirectory, Consumer<Connection> tickRequest, BooleanSupplier debugVisible) {
        settingsFile = configDirectory.resolve("sh_common_lib-player-info.properties");
        this.tickRequest = tickRequest;
        this.debugVisible = debugVisible;
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
            layout = null;
        }
        var connection = client.getConnection();
        boolean detectTicks = settings.enabled() && (shows(TPS) || shows(MSPT))
                && client.level != null && client.player != null && !client.isPaused();
        ClientTickMonitor.INSTANCE.tick(detectTicks && connection != null ? connection.getConnection() : null, tickRequest);
        if (!editKey.consumeClick()) return;
        while (editKey.consumeClick()) {
            // Open only one editor when several key presses accumulate before the next tick.
        }
        openEditor(null, () -> null);
    }

    public void openEditor(Screen parent, Supplier<? extends GuiEventListener> returnFocus) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen == parent && client.player != null && client.level != null) {
            layout = null;
            PlayerInfoHudLayout preview = sample(client);
            if (preview.isEmpty()) {
                preview = new PlayerInfoHudLayout(client.font, List.of(HudIcon.SIGNAL.row(client, line("no_items"))), null);
            }
            client.setScreen(new PlayerInfoHudScreen(this, settings, preview, parent, returnFocus));
        }
    }

    public boolean isEnabled() {
        return settings.enabled();
    }

    public boolean setEnabled(boolean enabled) {
        return save(new PlayerInfoHudSettings(enabled, settings.x(), settings.y(), settings.scalePercent(), settings.visibleItems()));
    }

    public boolean shows(PlayerInfoItem item) {
        return settings.shows(item);
    }

    public boolean setVisible(PlayerInfoItem item, boolean visible) {
        return save(settings.withItem(item, visible));
    }

    public void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (!settings.enabled() || client.options.hideGui || debugVisible.getAsBoolean()
                || client.player == null || client.level == null) return;

        var view = sample(client).fit(graphics.guiWidth(), graphics.guiHeight(), graphics.guiHeight(), settings.scalePercent(),
                client.getWindow().getGuiScale());
        var placement = view.placement();
        view.panel().draw(graphics, client.font,
                placement.x(settings.x()), placement.y(settings.y()), placement.scale());
    }

    boolean save(PlayerInfoHudSettings updated) {
        try {
            updated.write(settingsFile);
            settings = updated;
            layout = null;
            return true;
        } catch (IOException error) {
            LOGGER.warn("Could not save player info HUD settings to {}", settingsFile, error);
            return false;
        }
    }

    private PlayerInfoHudLayout sample(Minecraft client) {
        long now = Util.getMillis();
        if (layout != null && sampledPlayer == client.player && now < nextSample) return layout;
        sampledPlayer = client.player;
        nextSample = now + 250;
        ClientLevel level = client.level;
        LocalPlayer player = client.player;
        BlockPos pos = player.blockPosition();
        ChunkPos chunk = player.chunkPosition();
        List<PlayerInfoHudLayout.Row> rows = new ArrayList<>();
        var playerDetails = new ArrayList<Component>();
        if (shows(PLAYER)) playerDetails.add(value(player.getGameProfile().name()));
        if (shows(FPS)) playerDetails.add(line("fps", value(client.getFps())));
        if (shows(PING) && !client.hasSingleplayerServer()) {
            PlayerInfo info = player.connection.getPlayerInfo(player.getUUID());
            if (info != null) playerDetails.add(line("latency", value(info.getLatency())));
        }
        if (!playerDetails.isEmpty()) rows.add(HudIcon.SIGNAL.row(client, join(playerDetails)));
        if (shows(LOCAL_TIME)) {
            LocalTime localTime = LocalTime.now();
            rows.add(HudIcon.CLOCK.row(client, line("local_time", value(clock(localTime.getHour(), localTime.getMinute())))));
        }
        if (shows(WORLD_TIME)) {
            boolean hasLocalClock = level.dimensionType().defaultClock().isPresent();
            long dayTime = hasLocalClock ? level.getDefaultClockTime() : level.getOverworldClockTime();
            int gameMinutes = (int) ((Math.floorMod(dayTime, 24_000L) * 60 / 1_000 + 360) % 1_440);
            Component dayPhase = level.dimensionType().hasSkyLight() && hasLocalClock
                    ? line(level.getSkyDarken() < 4 ? "day" : "night") : Component.empty();
            rows.add(HudIcon.CALENDAR.row(client, line("world_time", value(Math.floorDiv(dayTime, 24_000L) + 1),
                    value(clock(gameMinutes / 60, gameMinutes % 60)), dayPhase)));
        }
        var worldDetails = new ArrayList<Component>();
        if (shows(DIMENSION)) worldDetails.add(value(dimensionName(level.dimension().identifier())));
        if (shows(CHUNKS)) worldDetails.add(line("chunks", value(level.getChunkSource().getLoadedChunksCount())));
        if (shows(ENTITIES)) worldDetails.add(line("entities", value(level.getEntityCount())));
        if (!worldDetails.isEmpty()) rows.add(HudIcon.WORLD.row(client, join(worldDetails)));
        if (shows(BIOME)) {
            var biome = level.getBiome(pos);
            Component biomeName = biome.unwrapKey().map(key -> {
                Identifier id = key.identifier();
                return Component.translatableWithFallback(id.toLanguageKey("biome"), id.toString());
            }).orElseGet(() -> line("unknown"));
            Biome biomeData = biome.value();
            rows.add(HudIcon.BIOME.row(client, line("biome", value(biomeName),
                    value(String.format(Locale.ROOT, "%.2f", biomeData.getBaseTemperature())))));
        }
        if (shows(POSITION)) {
            int heading = Mth.floor(player.getYRot() / 45.0 + 0.5) & 7;
            rows.add(HudIcon.LOCATION.row(client, line("position", value(pos.getX()), value(pos.getY()), value(pos.getZ()),
                    value(line(HEADINGS[heading])))));
        }
        if (shows(CHUNK)) rows.add(HudIcon.CHUNK.row(client, line("chunk", value(chunk.x()), value(chunk.z()),
                value(Math.floorMod(pos.getX(), 16)), value(Math.floorMod(pos.getZ(), 16)))));
        if (shows(LIGHT) && level.getChunkSource().hasChunk(chunk.x(), chunk.z())) {
            rows.add(HudIcon.TORCH.row(client, line("light", value(level.getMaxLocalRawBrightness(pos)),
                    value(level.getBrightness(LightLayer.SKY, pos)), value(level.getBrightness(LightLayer.BLOCK, pos)))));
        }
        addPerformance(client, rows);

        KeyMapping openKey = ControlCenterModule.openKey();
        var shortcut = openKey.isUnbound() ? null : new HudPanel.Row(HudPanel.icon("keyboard"), 0xFFDBE4EF,
                line("open_control_center", value(openKey.getTranslatedKeyMessage())));
        layout = new PlayerInfoHudLayout(client.font, rows, shortcut);
        return layout;
    }

    private void addPerformance(Minecraft client, List<PlayerInfoHudLayout.Row> rows) {
        var ticks = ClientTickMonitor.INSTANCE;
        if (shows(TPS)) rows.add(HudIcon.TPS.row(client, line("tps", number(ticks.tps(), "%.1f"))));
        if (shows(MSPT)) rows.add(HudIcon.MSPT.row(client, line("mspt", number(ticks.mspt(), "%.1f"))));
        var metrics = EnumSet.noneOf(ComputerStatusService.Metric.class);
        if (shows(MEMORY)) metrics.add(ComputerStatusService.Metric.MEMORY);
        if (shows(CPU)) metrics.add(ComputerStatusService.Metric.CPU);
        if (shows(GPU)) metrics.add(ComputerStatusService.Metric.GPU);
        if (shows(DISK)) metrics.add(ComputerStatusService.Metric.DISK);
        if (metrics.isEmpty()) return;
        var status = ComputerStatus.read(metrics);
        if (shows(MEMORY)) {
            var memory = status.memory();
            rows.add(HudIcon.MEMORY.row(client, line("memory",
                    memory.flatMap(ComputerStatusService.Memory::system).map(PlayerInfoHud::space).orElseGet(PlayerInfoHud::unavailable),
                    memory.map(data -> space(data.heap())).orElseGet(PlayerInfoHud::unavailable))));
        }
        if (shows(CPU)) {
            rows.add(HudIcon.CPU.row(client, status.cpu()
                    .map(cpu -> line("cpu", percent(cpu.systemLoad()), percent(cpu.processLoad())))
                    .orElseGet(() -> line("cpu", unavailable(), unavailable()))));
        }
        if (shows(GPU)) rows.add(HudIcon.GPU.row(client, line("gpu",
                status.gpu().map(PlayerInfoHud::gpu).orElseGet(PlayerInfoHud::unavailable))));
        if (shows(DISK)) rows.add(HudIcon.DISK.row(client, line("disk",
                status.disk().map(PlayerInfoHud::space).orElseGet(PlayerInfoHud::unavailable))));
    }

    private static Component gpu(ComputerStatusService.Gpu gpu) {
        if (gpu.busiestEngineLoad().isPresent()) return percent(gpu.busiestEngineLoad());
        if (gpu.adapters().isEmpty()) return unavailable();
        var adapter = gpu.adapters().getFirst();
        return adapter.vramBytes().isPresent()
                ? line("gpu_adapter", value(adapter.name()), number(gib(adapter.vramBytes().getAsLong()), "%.1f"))
                : value(adapter.name());
    }

    private static Component space(ComputerStatusService.Space space) {
        return line("space", number(gib(space.usedBytes()), "%.1f"), number(gib(space.totalBytes()), "%.1f"));
    }

    private static double gib(long bytes) {
        return bytes / 1_073_741_824.0;
    }

    private static Component percent(OptionalDouble load) {
        return load.isPresent() ? number(load.getAsDouble() * 100, "%.0f%%") : unavailable();
    }

    private static Component number(double number, String format) {
        return Double.isFinite(number) ? value(String.format(Locale.ROOT, format, number)) : unavailable();
    }

    private static Component unavailable() {
        return line("unavailable");
    }

    private static Component dimensionName(Identifier dimension) {
        return switch (dimension.toString()) {
            case "minecraft:overworld" -> line("overworld");
            case "minecraft:the_nether" -> line("nether");
            case "minecraft:the_end" -> line("end");
            default -> Component.translatableWithFallback(dimension.toLanguageKey("dimension"), dimension.toString());
        };
    }

    private static Component join(List<Component> fields) {
        var text = Component.empty();
        for (int index = 0; index < fields.size(); index++) {
            if (index != 0) text.append("   ");
            text.append(fields.get(index));
        }
        return text;
    }

    private static String clock(int hour, int minute) {
        return String.format(Locale.ROOT, "%02d:%02d", hour, minute);
    }

    private static MutableComponent line(String key, Object... args) {
        return Component.translatable(LANG + key, args);
    }

    private static Component value(Object value) {
        return (value instanceof Component text ? text.copy() : Component.literal(String.valueOf(value)))
                .withStyle(style -> style.withColor(ClientPanels.ACCENT & 0xFFFFFF));
    }

    private enum HudIcon {
        SIGNAL("statistics", 0xFF65F06B, 0),
        CLOCK("clock", 0xFFDBE4EF),
        CALENDAR("calendar", 0xFFFFD36A, 1),
        WORLD("block", 0xFF75C8FF, 1),
        BIOME("map", 0xFF87D581, 1),
        LOCATION("pin", 0xFFFF9B82, 0),
        CHUNK("topology", 0xFF9EC4D2),
        TORCH("light_mode", 0xFFFFB94F),
        TPS("tps", 0xFF87D581, 1),
        MSPT("mspt", 0xFFFFD36A, 1),
        MEMORY("cache", 0xFF9EC4D2),
        CPU("cpu", 0xFF75C8FF),
        GPU("gpu", 0xFFB6A5FF),
        DISK("disk", 0xFFDBE4EF);

        private final Identifier compact;
        private final Identifier fallback;
        private final int color;
        private final int priority;

        HudIcon(String fallback, int color) {
            this(fallback, color, 2);
        }

        HudIcon(String fallback, int color, int priority) {
            this.compact = HudPanel.icon("compact_" + name().toLowerCase(Locale.ROOT));
            this.fallback = HudPanel.icon(fallback);
            this.color = color;
            this.priority = priority;
        }

        PlayerInfoHudLayout.Row row(Minecraft client, Component text) {
            Identifier icon = client.getResourceManager().getResource(compact).isPresent() ? compact : fallback;
            return new PlayerInfoHudLayout.Row(new HudPanel.Row(icon, color, text), priority);
        }
    }
}
