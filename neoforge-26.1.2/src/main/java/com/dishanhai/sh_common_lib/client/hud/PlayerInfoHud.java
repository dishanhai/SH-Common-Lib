package com.dishanhai.sh_common_lib.client.hud;

import com.dishanhai.sh_better_ui.kernel.api.system.ComputerStatusService;
import com.dishanhai.sh_common_lib.api.client.ComputerStatus;
import com.dishanhai.sh_common_lib.client.control.ControlCenterModule;
import com.dishanhai.sh_common_lib.client.ClientPanels;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.dishanhai.sh_common_lib.internal.performance.ClientTickMonitor;
import com.dishanhai.sh_common_lib.internal.performance.ClientWeatherMonitor;
import com.dishanhai.sh_common_lib.internal.performance.WeatherForecast;
import com.dishanhai.sh_common_lib.internal.performance.ClientAe2Monitor;
import com.dishanhai.sh_common_lib.internal.performance.Ae2Snapshot;
import com.dishanhai.sh_common_lib.internal.performance.ClientInventoryHudMonitor;
import com.dishanhai.sh_common_lib.internal.performance.ClientEcoMachineMonitor;
import com.dishanhai.sh_common_lib.internal.performance.EcoMachineSnapshot;
import com.dishanhai.sh_common_lib.api.ulk.InventorySource;
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
import java.util.function.BiConsumer;
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
    private final Consumer<Connection> weatherRequest;
    private Consumer<Connection> ae2Request = ignored -> {};
    private BiConsumer<Connection, Integer> inventoryRequest = (ignored, flags) -> {};
    private BiConsumer<Connection, Integer> ecoMachineRequest = (ignored, mask) -> {};
    private boolean ecoSupported;
    private final BooleanSupplier debugVisible;
    private final HudRuntimeInfo runtime;
    private final GameTraffic gameTraffic = new GameTraffic();
    private FtbHudInfo ftb;
    private final LocalNetworkTraffic localNetwork = new LocalNetworkTraffic(Util.ioPool());
    private final KeyMapping editKey = new KeyMapping("key.sh_common_lib.player_info", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F6, NeoForgeKeyMappings.CATEGORY);
    private PlayerInfoHudSettings settings = PlayerInfoHudSettings.DEFAULT;
    private LocalPlayer sampledPlayer;
    private PlayerInfoHudLayout layout;
    private long nextSample;
    private KeyMapping previousPage, nextPage;
    private int pageIndex, pageCount = 1;

    public PlayerInfoHud(Path configDirectory, Consumer<Connection> tickRequest, BooleanSupplier debugVisible,
                         HudRuntimeInfo runtime) {
        this(configDirectory, tickRequest, ignored -> {}, debugVisible, runtime);
    }

    public PlayerInfoHud(Path configDirectory, Consumer<Connection> tickRequest, Consumer<Connection> weatherRequest, BooleanSupplier debugVisible,
                         HudRuntimeInfo runtime) {
        settingsFile = configDirectory.resolve("sh_common_lib-player-info.properties");
        this.tickRequest = tickRequest;
        this.weatherRequest = weatherRequest;
        this.debugVisible = debugVisible;
        this.runtime = runtime;
        try {
            settings = PlayerInfoHudSettings.read(settingsFile);
        } catch (IOException | IllegalArgumentException error) {
            LOGGER.warn("Could not read player info HUD settings from {}", settingsFile, error);
        }
    }

    public KeyMapping editKey() {
        return editKey;
    }

    public void bindPageKeys(KeyMapping previous, KeyMapping next) {
        previousPage = previous;
        nextPage = next;
    }

    public void bindAe2Request(Consumer<Connection> request) { ae2Request = request; }
    public void bindInventoryRequest(BiConsumer<Connection, Integer> request) { inventoryRequest = request; }
    public void bindEcoMachineRequest(BiConsumer<Connection, Integer> request) { ecoMachineRequest = request; ecoSupported = true; }
    public boolean available(PlayerInfoItem item) { return HudGroups.available(item, runtime::modLoaded, ecoSupported); }
    public boolean available(HudGroups.Group group) { return HudGroups.available(group, runtime::modLoaded, ecoSupported); }

    int pageIndex() { return pageIndex; }

    public void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            sampledPlayer = null;
            layout = null;
            pageIndex = 0;
            pageCount = 1;
            if (ftb != null) ftb.reset();
        }
        var connection = client.getConnection();
        boolean activeHud = settings.enabled() && client.level != null && client.player != null;
        gameTraffic.tick(activeHud && shows(CONNECTION_TRAFFIC) && connection != null ? connection.getConnection() : null);
        localNetwork.read(activeHud && shows(NETWORK_TRAFFIC));
        boolean detectTicks = settings.enabled() && (shows(TPS) || shows(MSPT))
                && client.level != null && client.player != null && !client.isPaused();
        ClientTickMonitor.INSTANCE.tick(detectTicks && connection != null ? connection.getConnection() : null, tickRequest);
        boolean detectWeather = settings.enabled() && shows(WEATHER) && client.player != null && client.level != null
                && client.level.canHaveWeather() && !client.isPaused();
        ClientWeatherMonitor.INSTANCE.tick(detectWeather && connection != null ? connection.getConnection() : null,
                detectWeather ? client.level.dimension().identifier().toString() : null, weatherRequest);
        boolean detectAe2 = activeHud && (shows(AE2_STATUS) || shows(AE2_CHANNELS) || shows(AE2_POWER)) && !client.isPaused();
        ClientAe2Monitor.INSTANCE.tick(detectAe2 && connection != null ? connection.getConnection() : null,
                detectAe2 ? client.level.dimension().identifier().toString() : null, ae2Request);
        int inventoryFlags = !activeHud || client.isPaused() ? 0 :
                ((shows(ULK_SUMMARY) || shows(ULK_SOURCES) ? 1 : 0) | (shows(NEOECO_NETWORK) ? 2 : 0));
        ClientInventoryHudMonitor.INSTANCE.tick(inventoryFlags != 0 && connection != null ? connection.getConnection() : null,
                inventoryFlags != 0 ? client.level.dimension().identifier().toString() : null, inventoryFlags, inventoryRequest);
        int ecoMask = !activeHud || client.isPaused() ? 0 : ecoMachineMask();
        ClientEcoMachineMonitor.INSTANCE.tick(ecoMask != 0 && connection != null ? connection.getConnection() : null,
                ecoMask != 0 ? client.level.dimension().identifier().toString() : null, ecoMask, ecoMachineRequest);
        int move = 0;
        if (previousPage != null) while (previousPage.consumeClick()) move--;
        if (nextPage != null) while (nextPage.consumeClick()) move++;
        if (move != 0 && client.screen == null && settings.enabled() && !client.options.hideGui
                && !debugVisible.getAsBoolean() && client.player != null && client.level != null) {
            pageIndex = Math.floorMod(pageIndex + move, pageCount);
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
            layout = null;
            PlayerInfoHudLayout preview = sample(client);
            client.setScreen(new PlayerInfoHudScreen(this, settings, preview, parent, returnFocus));
        }
    }

    public boolean isEnabled() {
        return settings.enabled();
    }

    public PlayerInfoHudSettings settings() {
        return settings;
    }

    public boolean applySettings(PlayerInfoHudSettings updated) {
        return save(updated);
    }

    public boolean setEnabled(boolean enabled) {
        return save(settings.withEnabled(enabled));
    }

    public boolean shows(PlayerInfoItem item) {
        return settings.shows(item) && settings.showsGroup(HudGroups.group(item));
    }

    public PlayerInfoItem loaderVersionItem() {
        return runtime.loaderVersionItem();
    }

    public boolean setVisible(PlayerInfoItem item, boolean visible) {
        return save(settings.withItem(item, visible));
    }

    public void render(GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        if (!settings.enabled() || client.options.hideGui || debugVisible.getAsBoolean()
                || client.player == null || client.level == null) return;

        var view = sample(client).fit(graphics.guiWidth(), graphics.guiHeight(), graphics.guiHeight(), settings.scalePercent(),
                client.getWindow().getGuiScale(), pageIndex);
        pageCount = view.pages();
        pageIndex = Math.floorMod(pageIndex, pageCount);
        var placement = view.placement();
        int x = placement.x(settings.x());
        int y = placement.y(settings.y());
        view.panel().draw(graphics, client.font, x, y, placement.scale());
        view.drawSeparators(graphics, client.font, x, y);
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
        if (shows(HEALTH)) rows.add(HudIcon.HEALTH.row(client, line("health", value(decimal(player.getHealth())),
                value(decimal(player.getMaxHealth())), value(decimal(player.getAbsorptionAmount())))));
        if (shows(FOOD)) rows.add(HudIcon.FOOD.row(client, line("food", value(player.getFoodData().getFoodLevel()),
                value(decimal(player.getFoodData().getSaturationLevel())))));
        if (shows(ARMOR)) rows.add(HudIcon.ARMOR.row(client, line("armor", value(player.getArmorValue()))));
        if (shows(EXPERIENCE)) rows.add(HudIcon.EXPERIENCE.row(client, line("experience", value(player.experienceLevel),
                value(Math.round(player.experienceProgress * 100)), value(player.totalExperience))));
        if (shows(AIR)) rows.add(HudIcon.AIR.row(client, line("air", value(player.getAirSupply()), value(player.getMaxAirSupply()))));
        if (shows(EFFECTS)) rows.add(HudIcon.EFFECTS.row(client, effects(player)));
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
        if (shows(WEATHER)) {
            String weather = !level.canHaveWeather() ? "weather_none"
                    : level.isThundering() ? "weather_thunder" : level.isRaining() ? "weather_precipitation" : "weather_clear";
            Component currentWeather = line("weather", value(line(weather)));
            rows.add(HudIcon.WEATHER.row(client, level.canHaveWeather()
                    ? join(List.of(currentWeather, weatherForecast())) : currentWeather));
        }
        if (shows(MOON_PHASE)) {
            String phase = level.dimensionType().hasSkyLight()
                    ? "moon_" + level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.MOON_PHASE, pos).getSerializedName()
                    : "unknown";
            rows.add(HudIcon.MOON.row(client, line("moon_phase", value(line(phase)))));
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
        if (shows(CONNECTION_TRAFFIC)) rows.add(HudIcon.CONNECTION.row(client, line("connection_traffic",
                client.hasSingleplayerServer() ? line("traffic_local") : traffic(gameTraffic.rate()))));
        if (shows(NETWORK_TRAFFIC)) rows.add(HudIcon.NETWORK.row(client, line("network_traffic",
                traffic(localNetwork.current()))));
        addAe2(client, rows);
        addInventoryHud(client, rows);
        if (shows(FTB_QUESTS) || shows(FTB_TEAM) || shows(FTB_CHUNKS) || shows(FTB_CHUNK_INFO)) {
            if (ftb == null) ftb = new FtbHudInfo();
            if (shows(FTB_QUESTS)) rows.add(HudIcon.FTB_QUESTS.row(client, line("ftb_quests", ftb.quests(level))));
            if (shows(FTB_TEAM)) rows.add(HudIcon.FTB_TEAM.row(client, line("ftb_team", ftb.team(level))));
            if (shows(FTB_CHUNKS)) rows.add(HudIcon.FTB_CHUNKS.row(client, line("ftb_chunks", ftb.quota(level))));
            if (shows(FTB_CHUNK_INFO)) rows.add(HudIcon.FTB_CHUNK.row(client,
                    line("ftb_chunk", ftb.chunk(level, chunk, chunk.x(), chunk.z()))));
        } else if (ftb != null) {
            ftb.reset();
        }

        KeyMapping openKey = ControlCenterModule.openKey();
        runtime.append(this::shows, PlayerInfoHud::value, (item, text) -> {
            var icon = switch (item) {
                case JAVA_VERSION -> HudIcon.JAVA;
                case SYSTEM_VERSION -> HudIcon.SYSTEM;
                case MINECRAFT_VERSION -> HudIcon.MINECRAFT;
                case FORGE_VERSION -> HudIcon.FORGE;
                case NEOFORGE_VERSION -> HudIcon.NEOFORGE;
                case SHADER_PACK -> HudIcon.SHADER;
                case MOD_STATUS -> HudIcon.MODS;
                default -> HudIcon.RUNTIME;
            };
            rows.add(icon.row(client, text));
        });
        var shortcuts = List.of(
                new HudPanel.Row(HudPanel.icon("keyboard"), 0xFFDBE4EF,
                        line("open_shbui_panel", value(HudRuntimeInfo.shbuiShortcut(client)))),
                new HudPanel.Row(HudPanel.icon("keyboard"), 0xFFDBE4EF,
                        line("open_control_center", value(HudRuntimeInfo.keyLabel(openKey)))));
        Component pageKeys = previousPage == null || nextPage == null ? line("page_keys_default")
                : line("page_keys", HudRuntimeInfo.keyLabel(previousPage), HudRuntimeInfo.keyLabel(nextPage));
        layout = new PlayerInfoHudLayout(client.font, rows, shortcuts, pageKeys,
                settings.showGroupTitles(), settings.showGroupSeparators());
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

    private void addInventoryHud(Minecraft client, List<PlayerInfoHudLayout.Row> rows) {
        var snapshot = ClientInventoryHudMonitor.INSTANCE.current();
        if (shows(NEOECO_NETWORK)) rows.add(HudIcon.NEOECO.row(client, InventoryHudText.eco(snapshot)));
        addEcoMachines(client, rows);
        if (shows(ULK_SUMMARY)) rows.add(HudIcon.ULK.row(client, InventoryHudText.summary(snapshot)));
        if (shows(ULK_SOURCES)) for (var source : InventorySource.values()) {
            if (source != InventorySource.CUSTOM) rows.add(HudIcon.ULK.row(client, InventoryHudText.source(snapshot, source)));
        }
    }

    private int ecoMachineMask() {
        return (shows(NEOECO_CRAFTING) ? 1 : 0) | (shows(NEOECO_COMPUTATION) ? 2 : 0)
                | (shows(NEOECO_STORAGE) ? 4 : 0) | (shows(NEOECO_WORKSTATION) ? 8 : 0);
    }

    private void addEcoMachines(Minecraft client, List<PlayerInfoHudLayout.Row> rows) {
        int mask = ecoMachineMask();
        if (mask == 0) return;
        var data = ClientEcoMachineMonitor.INSTANCE.current();
        if (data == null || data.state() != EcoMachineSnapshot.CONNECTED) {
            rows.add(HudIcon.NEOECO.row(client, EcoMachineHudText.state(data)));
            return;
        }
        for (int type = 0; type < 4; type++) {
            if ((mask & (1 << type)) == 0) continue;
            boolean found = false;
            for (var machine : data.machines()) if (machine.type() == type) {
                rows.add(ecoMachineIcon(type).row(client, EcoMachineHudText.title(machine)));
                rows.add(ecoMachineIcon(type).row(client, EcoMachineHudText.details(machine)));
                found = true;
            }
            if (!found && !data.truncated()) rows.add(ecoMachineIcon(type).row(client, EcoMachineHudText.empty(type)));
        }
        if (data.truncated()) rows.add(HudIcon.NEOECO.row(client, EcoMachineHudText.partial()));
    }

    private static HudIcon ecoMachineIcon(int type) {
        return switch (type) {
            case EcoMachineSnapshot.CRAFTING -> HudIcon.ECO_CRAFTING;
            case EcoMachineSnapshot.COMPUTATION -> HudIcon.ECO_COMPUTATION;
            case EcoMachineSnapshot.STORAGE -> HudIcon.ECO_STORAGE;
            default -> HudIcon.ECO_WORKSTATION;
        };
    }

    private void addAe2(Minecraft client, List<PlayerInfoHudLayout.Row> rows) {
        if (!shows(AE2_STATUS) && !shows(AE2_CHANNELS) && !shows(AE2_POWER)) return;
        Ae2Snapshot snapshot = ClientAe2Monitor.INSTANCE.current();
        if (shows(AE2_STATUS)) {
            Component status;
            if (snapshot == null) status = line("ae2_unavailable");
            else if (snapshot.state() == Ae2Snapshot.NO_MOD) status = line("ae2_no_mod");
            else if (snapshot.state() == Ae2Snapshot.NO_TERMINAL) status = line("ae2_no_terminal");
            else if (snapshot.state() == Ae2Snapshot.ERROR) status = line("ae2_unavailable");
            else if (snapshot.state() == Ae2Snapshot.DISCONNECTED) status = line("ae2_disconnected");
            else status = line("ae2_connected", line(snapshot.booting() ? "ae2_booting"
                            : snapshot.powered() ? "ae2_powered" : "ae2_unpowered"), value(snapshot.nodes()),
                    ae2Enum("controller", snapshot.controller()));
            rows.add(HudIcon.AE2.row(client, line("ae2_status", status)));
        }
        if (shows(AE2_CHANNELS)) rows.add(HudIcon.AE2_CHANNELS.row(client,
                snapshot == null || snapshot.state() != Ae2Snapshot.CONNECTED ? line("ae2_channels_unavailable")
                        : line("ae2_channels", value(snapshot.channels()), value(snapshot.accessUsed()),
                        value(snapshot.accessMax()), ae2Enum("mode", snapshot.channelMode()))));
        if (shows(AE2_POWER)) rows.add(HudIcon.AE2_POWER.row(client,
                snapshot == null || snapshot.state() != Ae2Snapshot.CONNECTED ? line("ae2_power_unavailable")
                        : line("ae2_power", number(snapshot.stored(), "%.1f"), number(snapshot.capacity(), "%.1f"),
                        number(snapshot.usage(), "%.1f"), number(snapshot.input(), "%.1f"))));
    }

    private static Component ae2Enum(String group, String name) {
        return Component.translatableWithFallback(LANG + "ae2_" + group + "." + name.toLowerCase(Locale.ROOT), name);
    }

    private static Component traffic(java.util.Optional<GameTraffic.Rate> rate) {
        return rate.<Component>map(value -> line("traffic_values", speed(value.received()), speed(value.sent())))
                .orElseGet(PlayerInfoHud::unavailable);
    }

    private static Component speed(double bytesPerSecond) {
        String unit = "traffic_bytes";
        if (bytesPerSecond >= 1_073_741_824) { bytesPerSecond /= 1_073_741_824; unit = "traffic_gib"; }
        else if (bytesPerSecond >= 1_048_576) { bytesPerSecond /= 1_048_576; unit = "traffic_mib"; }
        else if (bytesPerSecond >= 1024) { bytesPerSecond /= 1024; unit = "traffic_kib"; }
        return line(unit, number(bytesPerSecond, "%.1f"));
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
        return Double.isFinite(number) ? value(HudNumberFormatter.format(number, format)) : unavailable();
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
        return (value instanceof Component text ? text.copy() : Component.literal(value instanceof Number number
                ? HudNumberFormatter.format(number) : String.valueOf(value)))
                .withStyle(style -> style.withColor(ClientPanels.ACCENT & 0xFFFFFF));
    }

    private static String decimal(float value) {
        return HudNumberFormatter.format(value, "%.1f");
    }

    private static Component effects(LocalPlayer player) {
        var active = player.getActiveEffects();
        var details = active.stream().sorted(java.util.Comparator.comparing(effect -> effect.getDescriptionId()))
                .limit(8).map(effect -> (Component) line("effect_detail",
                        Component.translatable(effect.getDescriptionId()), value(effect.getAmplifier() + 1),
                        effect.isInfiniteDuration() ? line("effect_infinite") : line("effect_seconds", value((effect.getDuration() + 19L) / 20))))
                .toList();
        return line("effects", value(active.size()), details.isEmpty() ? line("effect_none") : join(details));
    }

    private static Component weatherForecast() {
        WeatherForecast forecast = ClientWeatherMonitor.INSTANCE.current();
        if (forecast == null) return line("forecast_unavailable");
        String key = switch (forecast.event()) {
            case WeatherForecast.PAUSED -> "forecast_paused";
            case WeatherForecast.CLEAR_END -> "forecast_clear_end";
            case WeatherForecast.RAIN_START -> "forecast_rain_start";
            case WeatherForecast.RAIN_END -> "forecast_rain_end";
            case WeatherForecast.THUNDER_START -> "forecast_thunder_start";
            case WeatherForecast.THUNDER_END -> "forecast_thunder_end";
            default -> "forecast_unavailable";
        };
        return line(key, value(forecast.ticks()));
    }

    private enum HudIcon {
        SIGNAL("statistics", 0xFF65F06B, 0),
        HEALTH("player", "security", 0xFFFF7580),
        FOOD("player", "minecraft:apple", 0xFFFFFFFF),
        ARMOR("player", "minecraft:iron_chestplate", 0xFFFFFFFF),
        EXPERIENCE("player", "minecraft:experience_bottle", 0xFFFFFFFF),
        AIR("player", "cloud", 0xFF9EDBFF),
        EFFECTS("player", "attributes", 0xFFB6A5FF),
        MOON("clock", "dark_mode", 0xFFDBE4EF),
        CLOCK("clock", 0xFFDBE4EF),
        CALENDAR("calendar", 0xFFFFD36A, 1),
        WEATHER("cloud", 0xFF9EDBFF, 1),
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
        RUNTIME("computer", 0xFF9EC4D2),
        JAVA("computer", "file_java", 0xFFFFB96D),
        SYSTEM("computer", "system", 0xFF9EC4D2),
        MINECRAFT("block", "minecraft", 0xFF87D581),
        FORGE("block", "forge", 0xFFFFB96D),
        NEOFORGE("block", "neoforge", 0xFFFFB96D),
        SHADER("gpu", "render", 0xFFB6A5FF),
        MODS("block", "mod", 0xFF87D581),
        CONNECTION("statistics", "server", 0xFF65F06B),
        NETWORK("statistics", "network", 0xFF75C8FF),
        AE2("block", "ae2", 0xFFB6A5FF),
        AE2_CHANNELS("block", "ae_channel", 0xFF9EDBFF),
        AE2_POWER("block", "power", 0xFFFFD36A),
        NEOECO("block", "ae2", 0xFF87D581),
        ECO_CRAFTING("block", "ae2", 0xFF87D581),
        ECO_COMPUTATION("cpu", null, 0xFF75C8FF),
        ECO_STORAGE("disk", null, 0xFFB6A5FF),
        ECO_WORKSTATION("block", "settings", 0xFFFFD36A),
        ULK("cache", "inventory", 0xFF9EDBFF),
        FTB_QUESTS("block", "ftb_quests", 0xFF87D581),
        FTB_TEAM("player", "members", 0xFF75C8FF),
        FTB_CHUNKS("map", "map", 0xFF9EC4D2),
        FTB_CHUNK("map", "location", 0xFFFFD36A),
        DISK("disk", 0xFFDBE4EF);

        private final Identifier compact;
        private final Identifier preferred;
        private final Identifier fallback;
        private final int color;
        private final int priority;

        HudIcon(String fallback, int color) {
            this(fallback, color, 2);
        }

        HudIcon(String fallback, int color, int priority) {
            this(fallback, null, color, priority);
        }

        HudIcon(String fallback, String preferred, int color) {
            this(fallback, preferred, color, 2);
        }

        HudIcon(String fallback, String preferred, int color, int priority) {
            this.compact = HudPanel.icon("compact_" + name().toLowerCase(Locale.ROOT));
            this.preferred = preferred == null ? HudPanel.icon(fallback) : preferred.startsWith("minecraft:")
                    ? Identifier.tryParse("minecraft:textures/item/" + preferred.substring("minecraft:".length()) + ".png")
                    : HudPanel.icon(preferred);
            this.fallback = HudPanel.icon(fallback);
            this.color = color;
            this.priority = priority;
        }

        PlayerInfoHudLayout.Row row(Minecraft client, Component text) {
            Identifier icon = client.getResourceManager().getResource(compact).isPresent() ? compact
                    : client.getResourceManager().getResource(preferred).isPresent() ? preferred : fallback;
            return new PlayerInfoHudLayout.Row(new HudPanel.Row(icon, color, text), priority, group());
        }

        private HudGroups.Group group() {
            return switch (this) {
                case SIGNAL, HEALTH, FOOD, ARMOR, EXPERIENCE, AIR, EFFECTS, CLOCK -> HudGroups.Group.PLAYER;
                case MOON, CALENDAR, WEATHER, WORLD, BIOME -> HudGroups.Group.WORLD;
                case LOCATION, CHUNK, TORCH -> HudGroups.Group.LOCATION;
                case TPS, MSPT -> HudGroups.Group.SERVER;
                case MEMORY, CPU, GPU, DISK -> HudGroups.Group.COMPUTER;
                case CONNECTION, NETWORK -> HudGroups.Group.NETWORK;
                case AE2, AE2_CHANNELS, AE2_POWER -> HudGroups.Group.AE2;
                case NEOECO, ECO_CRAFTING, ECO_COMPUTATION, ECO_STORAGE, ECO_WORKSTATION -> HudGroups.Group.NEOECO;
                case ULK -> HudGroups.Group.ULK;
                case FTB_QUESTS, FTB_TEAM, FTB_CHUNKS, FTB_CHUNK -> HudGroups.Group.FTB;
                case RUNTIME, JAVA, SYSTEM, MINECRAFT, FORGE, NEOFORGE, SHADER, MODS -> HudGroups.Group.RUNTIME;
            };
        }
    }
}
