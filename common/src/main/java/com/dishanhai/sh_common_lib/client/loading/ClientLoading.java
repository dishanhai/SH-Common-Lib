// Author: dishanhai
package com.dishanhai.sh_common_lib.client.loading;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_common_lib.api.client.LoadingTemplate;
import com.dishanhai.sh_common_lib.api.performance.PerformanceSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.packs.resources.ReloadInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/** Client-thread lifecycle for the opt-in built-in presentation and reports. */
public final class ClientLoading {
    static final Logger LOGGER = LoggerFactory.getLogger("sh_common_lib:loading");
    private static LoadingSettings settings;
    private static LoadingReports reports;
    private static LoadingReports.Capture startup;
    private static LoadingReports.Capture world;
    private static volatile PerformanceSession currentProfile;
    private static boolean startupPending;
    private static boolean sawStartupOverlay;
    private static boolean checkedStartupReload;
    private static long startupTime;
    private static long stageTime;
    private static Stage stage;
    private static boolean renderFailed;

    private ClientLoading() {}

    public static void initialize(Path configDirectory, boolean hasJei) {
        var directory = configDirectory.resolve("sh_common_lib");
        com.dishanhai.sh_common_lib.client.jei.JeiSearchPreferences.initialize(directory);
        settings = new LoadingSettings(directory.resolve("loading.properties"));
        ModLoadingStatus.initialize(hasJei);
        reports = new LoadingReports(directory.resolve("loading_reports"));
        startupPending = animation() || profiling();
        if (startupPending) startupTime = System.nanoTime();
        if (profiling()) {
            startup = reports.begin("startup");
            currentProfile = startup.session();
        }
    }

    public static boolean animation() { return settings != null && settings.animation(); }
    public static boolean profiling() { return settings != null && settings.profiling(); }
    public static boolean setAnimation(boolean enabled) { return settings != null && settings.save(enabled, profiling()); }
    public static boolean setProfiling(boolean enabled) { return settings != null && settings.save(animation(), enabled); }
    public static boolean applySettings(boolean animation, boolean profiling) {
        return settings != null && settings.save(animation, profiling);
    }
    public static Path reportDirectory() { return reports.directory(); }

    /** Loader hooks may capture this reference before dispatching work to background workers. */
    public static PerformanceSession currentProfile() { return currentProfile; }

    public static void renderStartup(GuiGraphics graphics, ReloadInstance reload, boolean fading,
                                     float displayedProgress, boolean showIndicator) {
        if (!startupPending) return;
        if (!sawStartupOverlay) {
            sawStartupOverlay = true;
            if (startup != null) startup.session().recordElapsed("client initialization", "observed", startupTime, false);
            startupTime = System.nanoTime();
        }
        if (fading && !checkedStartupReload) {
            checkedStartupReload = true;
            try {
                reload.checkExceptions();
            } catch (RuntimeException failure) {
                finishStartup("failed");
            }
        } else if (!fading && showIndicator && !renderFailed) {
            try {
                LoadingTemplate.renderIndicator(graphics, graphics.guiHeight() * 2 / 3, displayedProgress,
                        (System.nanoTime() - startupTime) / 1_000_000, loadingPreferences());
            } catch (RuntimeException error) {
                onRenderFailure(error);
            }
        }
    }

    public static void beginLocalWorld() {
        if (stage == null && (animation() || profiling())) transition(Stage.LOCAL_DATA);
    }

    public static void screenInitialized(Screen screen) {
        if (!animation() && !profiling()) return;
        var client = Minecraft.getInstance();
        Stage next = stageOf(screen);
        if (next != null && (stage != null || client.level == null)) transition(next);
    }

    public static void tick() {
        if (!startupPending && stage == null) return;
        var client = Minecraft.getInstance();
        if (startupPending && client.getOverlay() == null && (client.screen instanceof TitleScreen
                || sawStartupOverlay && (client.screen != null || client.player != null))) {
            finishStartup("ready");
        }
        if (stage == null) return;
        if (!animation() && !profiling()) {
            finishWorld("disabled");
        } else if (client.player != null && client.level != null && client.screen == null && client.getOverlay() == null) {
            finishWorld("ready");
        } else if (client.screen instanceof DisconnectedScreen) {
            finishWorld("failed");
        } else if (client.screen != null && stageOf(client.screen) == null) {
            finishWorld("interrupted");
        }
    }

    public static void renderWorld(Screen screen, GuiGraphics graphics) {
        if (stage == null || !animation() || screen instanceof LevelLoadingScreen || stageOf(screen) == null) return;
        // Keep the native status, narration, and cancel button available.
        if (graphics.guiHeight() >= 224) {
            if (render(graphics, 8, label("world"), label(stage.key), Float.NaN, stageTime)) {
                renderModStatus(graphics, 60, false);
            }
        }
    }

    /** Draws the visual replacement after vanilla has drawn its background and updated narration. */
    public static boolean renderWorldChunks(GuiGraphics graphics, int percent) {
        if (stage != Stage.CHUNKS || !animation() || graphics.guiWidth() < 56 || graphics.guiHeight() < 48) return false;
        int progress = Math.clamp(percent, 0, 100);
        int top = (graphics.guiHeight() - 48) / 2;
        boolean rendered = render(graphics, top, label("world"),
                Component.translatable("screen.sh_common_lib.loading.chunk_progress", label("chunks"), progress),
                progress == 0 ? Float.NaN : progress / 100f, stageTime);
        if (rendered) renderModStatus(graphics, top + 52, false);
        return rendered;
    }

    public static void renderHud(GuiGraphics graphics) {
        if (!animation() || renderFailed) return;
        var client = Minecraft.getInstance();
        if (client.screen == null && client.getOverlay() == null && !client.options.hideGui) {
            renderModStatus(graphics, graphics.guiHeight() - 84, true);
        }
    }

    private static void renderModStatus(GuiGraphics graphics, int top, boolean summaryOnly) {
        var lines = ModLoadingStatus.lines(summaryOnly);
        if (lines.isEmpty()) return;
        try {
            LoadingTemplate.renderStatus(graphics, Minecraft.getInstance().font, top, lines, loadingPreferences());
        } catch (RuntimeException error) {
            onRenderFailure(error);
        }
    }

    private static Stage stageOf(Screen screen) {
        if (screen instanceof ConnectScreen) return Stage.CONNECT;
        if (screen instanceof LevelLoadingScreen) return Stage.CHUNKS;
        if (screen instanceof ReceivingLevelScreen) return Stage.TERRAIN;
        if (screen != null && screen.getTitle().getContents() instanceof TranslatableContents title) {
            return switch (title.getKey()) {
                case "selectWorld.data_read" -> Stage.LOCAL_DATA;
                case "selectWorld.resource_load" -> Stage.LOCAL_RESOURCES;
                default -> null;
            };
        }
        return null;
    }

    private static void transition(Stage next) {
        if (stage == next) return;
        if (stage == null) {
            // Quick Play may start loading the world inside the startup overlay callback.
            if (startupPending) finishStartup("ready");
            if (profiling()) {
                world = reports.begin("world");
                currentProfile = world.session();
            }
        }
        recordStage(false);
        stage = next;
        stageTime = System.nanoTime();
    }

    private static void finishStartup(String outcome) {
        startupPending = false;
        if (startup == null) return;
        if (sawStartupOverlay) {
            startup.session().recordElapsed("startup overlay", "observed", startupTime, outcome.equals("failed"));
        }
        if (world == null) currentProfile = null;
        reports.finish(startup, outcome);
        startup = null;
    }

    private static void finishWorld(String outcome) {
        if (world != null) {
            recordStage(outcome.equals("failed"));
            currentProfile = startup == null ? null : startup.session();
            reports.finish(world, outcome);
            world = null;
        }
        stage = null;
    }

    private static void recordStage(boolean failed) {
        if (world != null && stage != null) world.session().recordElapsed(stage.key, "observed", stageTime, failed);
    }

    private static boolean render(GuiGraphics graphics, int top, Component title, Component detail, float progress, long started) {
        if (renderFailed) return false;
        var font = Minecraft.getInstance().font;
        if (font == null) return false;
        try {
            LoadingTemplate.render(graphics, font, top, title, detail, progress,
                    (System.nanoTime() - started) / 1_000_000, loadingPreferences());
            return true;
        } catch (RuntimeException error) {
            onRenderFailure(error);
            return false;
        }
    }

    private static UiPreferences loadingPreferences() {
        var configured = UiPreferences.configured();
        return new UiPreferences(configured.highContrast(), configured.reducedMotion(), false, configured.feedback());
    }

    private static void onRenderFailure(RuntimeException error) {
        renderFailed = true;
        LOGGER.warn("SHCLIB loading card failed to render", error);
    }

    private static Component label(String key) {
        return Component.translatable("screen.sh_common_lib.loading." + key);
    }

    private enum Stage {
        LOCAL_DATA("local_data"), LOCAL_RESOURCES("resources"), CONNECT("connect"), CHUNKS("chunks"), TERRAIN("terrain");

        private final String key;

        Stage(String key) { this.key = key; }
    }
}
