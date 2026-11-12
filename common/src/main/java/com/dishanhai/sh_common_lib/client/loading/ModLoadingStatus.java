// Author: dishanhai
package com.dishanhai.sh_common_lib.client.loading;

import com.dishanhai.sh_common_lib.api.performance.PerformanceSession;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Optional mod milestones, shared by the loading screens and the short in-game summary. */
public final class ModLoadingStatus {
    private static final String PREFIX = "screen.sh_common_lib.loading.";
    private static final long SUMMARY_NANOS = 5_000_000_000L;
    private static boolean jeiInstalled;
    private static volatile boolean ae2Ready;
    private static long ae2Started;
    private static PerformanceSession ae2Profile;
    private static long jeiStarted;
    private static long jeiStageStarted;
    private static PerformanceSession jeiProfile;
    private static volatile JeiStatus jei;

    private ModLoadingStatus() {}

    static void initialize(boolean hasJei) {
        jeiInstalled = hasJei;
    }

    public static void beginAe2(Consumer<String> earlyMessage) {
        if (!ClientLoading.animation() && !ClientLoading.profiling()) return;
        ae2Started = System.nanoTime();
        ae2Profile = ClientLoading.currentProfile();
        postEarly(earlyMessage, "ae2_initializing");
    }

    public static void finishAe2(Consumer<String> earlyMessage) {
        ae2Ready = true;
        if (ae2Profile != null) {
            ae2Profile.recordElapsed("ae2 client setup", "observed", ae2Started, false);
            ae2Profile = null;
        }
        postEarly(earlyMessage, "ae2_ready");
    }

    public static synchronized void jeiStage(String key) {
        if (!ClientLoading.animation() && !ClientLoading.profiling()) return;
        long now = System.nanoTime();
        recordJeiStage();
        if (jei == null || jei.completedAt() != 0) {
            jeiStarted = now;
            jeiProfile = ClientLoading.currentProfile();
        }
        jeiStageStarted = now;
        jei = new JeiStatus(key, label(key), 0);
    }

    public static synchronized void jeiReady(long ingredients, long categories) {
        if (!ClientLoading.animation() && !ClientLoading.profiling()) return;
        long now = System.nanoTime();
        recordJeiStage();
        long elapsedMillis = jeiStarted == 0 ? 0 : (now - jeiStarted) / 1_000_000;
        jei = new JeiStatus("jei_ready", label("jei_ready", ingredients, categories, elapsedMillis), now);
        jeiStarted = 0;
        jeiProfile = null;
    }

    public static synchronized void clearJei() {
        jei = null;
        jeiStarted = 0;
        jeiProfile = null;
    }

    static List<Component> lines(boolean summaryOnly) {
        JeiStatus snapshot = jei;
        if (summaryOnly && (snapshot == null || snapshot.completedAt() == 0
                || System.nanoTime() - snapshot.completedAt() >= SUMMARY_NANOS)) return List.of();
        List<Component> lines = new ArrayList<>(2);
        if (snapshot != null) lines.add(snapshot.text());
        else if (jeiInstalled) lines.add(label("jei_waiting"));
        if (ae2Ready) lines.add(label("ae2_ready"));
        return lines;
    }

    private static void recordJeiStage() {
        if (jeiProfile != null && jei != null && jei.completedAt() == 0) {
            jeiProfile.recordElapsed(jei.key(), "JEI callback interval", jeiStageStarted, false);
        }
    }

    private static Component label(String key, Object... args) {
        return Component.translatable(PREFIX + key, args);
    }

    private static void postEarly(Consumer<String> earlyMessage, String key) {
        if (!ClientLoading.animation()) return;
        // The early window has its own ASCII font, before the selected locale and glyphs are available.
        String text = EarlyLabels.ENGLISH.get(PREFIX + key);
        if (text != null) earlyMessage.accept(text);
    }

    private static final class EarlyLabels {
        private static final Map<String, String> ENGLISH = load();

        private static Map<String, String> load() {
            Map<String, String> labels = new HashMap<>();
            try (var stream = ModLoadingStatus.class.getResourceAsStream("/assets/sh_common_lib/lang/en_us.json")) {
                if (stream != null) Language.loadFromJson(stream, labels::put);
            } catch (IOException | RuntimeException error) {
                ClientLoading.LOGGER.warn("Could not read early loading labels", error);
            }
            return labels;
        }
    }

    private record JeiStatus(String key, Component text, long completedAt) {}
}
