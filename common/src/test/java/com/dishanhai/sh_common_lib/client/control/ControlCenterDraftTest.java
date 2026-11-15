// Author: dishanhai
package com.dishanhai.sh_common_lib.client.control;

import com.dishanhai.sh_common_lib.client.hud.PlayerInfoHudSettings;
import com.dishanhai.sh_common_lib.client.hud.PlayerInfoHudSettingsIoTest;
import com.dishanhai.sh_common_lib.client.hud.PlayerInfoItem;
import com.dishanhai.sh_common_lib.client.hud.HudGroups;
import com.dishanhai.sh_common_lib.client.jei.JeiSearchSettings;
import com.dishanhai.sh_common_lib.client.item.ArtisanAppearancePreferences;
import com.dishanhai.sh_common_lib.internal.item.ArtisanSettings;
import com.dishanhai.sh_common_lib.internal.item.ArtisanSettings.Passives;
import com.dishanhai.sh_common_lib.internal.item.ArtisanSettings.Profiles;

import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;

/** Standalone behavioral checks; run with the JDK used by the project. */
public final class ControlCenterDraftTest {
    public static void main(String[] args) throws Exception {
        stagedEditsAndUndo();
        partiallyFailedApplyCanBeRetried();
        failedApplyKeepsAllChanges();
        layoutEditorChangesMergeWithPendingOptions();
        groupPreferencesSurviveEditorReturn();
        optionalIntegrationsFollowLoadedMods();
        jeiSettingsAreStagedAndRetryable();
        artisanSettingsRespectReadOnlyWorldsAndApply();
        artisanAppearancePersistsOnClient();
        ControlCenterLayoutTest.run();
        PlayerInfoHudSettingsIoTest.run();
        System.out.println("Control center draft checks passed");
    }

    private static void artisanSettingsRespectReadOnlyWorldsAndApply() {
        var serverSettings = new Profiles(new ArtisanSettings(true, true),
                new ArtisanSettings(false, true, 1, 2, 4, 8, 500, 12, 1.5, 3),
                new Passives(false, false, false, false));
        var remote = new ControlCenterDraft(PlayerInfoHudSettings.DEFAULT, true, false, JeiSearchSettings.DEFAULT,
                serverSettings, false, true);
        remote.setArtisan(true, ArtisanSettings.COSMIC_DEFAULT);
        remote.setArtisanPassives(Passives.DEFAULT);
        remote.restoreDefaults();
        check(remote.artisan(false).equals(serverSettings.regular()) && remote.artisan(true).equals(serverSettings.cosmic())
                        && remote.artisanPassives().equals(serverSettings.passives()),
                "reset and edits must preserve read-only profiles and passives");
        remote.setArtisanTargetColors(false);
        check(!remote.artisanTargetColors() && remote.apply(hud -> true, (a, p) -> true, jei -> true, value -> {
            throw new AssertionError("read-only world settings were written");
        }, enabled -> false) == ControlCenterDraft.ApplyResult.PARTIAL && remote.dirty(),
                "failed client preference save must remain staged while other settings save");
        check(remote.apply(hud -> true, (a, p) -> true, jei -> true, value -> {
            throw new AssertionError("read-only world settings were written");
        }, enabled -> !enabled) == ControlCenterDraft.ApplyResult.SAVED && !remote.dirty(),
                "the client preference must be editable in read-only worlds");
        remote.restoreDefaults();
        check(remote.artisanTargetColors() && remote.artisanPassives().equals(serverSettings.passives()),
                "reset must restore client colors while preserving remote passives");
        remote.restoreDefaults();
        check(!remote.artisanTargetColors() && !remote.dirty(), "undo must restore the client preference");

        var local = new ControlCenterDraft(PlayerInfoHudSettings.DEFAULT, false, false, JeiSearchSettings.DEFAULT,
                Profiles.DEFAULTS, true, true);
        var disk = new AtomicReference<>(Profiles.DEFAULTS);
        local.setArtisanPassives(local.artisanPassives().withFlight(false));
        check(local.artisanPassives().equals(new Passives(false, true, true, true)), "flight switch must be independent");
        local.setArtisanPassives(local.artisanPassives().withDamageImmunity(false));
        check(local.artisanPassives().equals(new Passives(false, false, true, true)), "damage switch must be independent");
        local.setArtisanPassives(local.artisanPassives().withFullFood(false));
        check(local.artisanPassives().equals(new Passives(false, false, false, true)), "food switch must be independent");
        local.setArtisanPassives(local.artisanPassives().withFullSaturation(false));
        local.setArtisan(false, serverSettings.regular());
        check(local.artisan(true).equals(ArtisanSettings.COSMIC_DEFAULT), "editing the regular profile must leave cosmic independent");
        local.setArtisan(true, serverSettings.cosmic());
        check(disk.get().equals(Profiles.DEFAULTS) && local.dirty(), "Artisan changes must remain staged");
        check(local.apply(hud -> true, (a, p) -> true, jei -> true, value -> false)
                == ControlCenterDraft.ApplyResult.FAILED && local.dirty(), "Artisan save can be retried");
        check(local.apply(hud -> true, (a, p) -> true, jei -> true, value -> { disk.set(value); return true; })
                == ControlCenterDraft.ApplyResult.SAVED && disk.get().equals(serverSettings) && !local.dirty(),
                "Apply must persist both independent profiles");
        local.restoreDefaults();
        check(local.artisan(false).equals(ArtisanSettings.DEFAULT) && local.artisan(true).equals(ArtisanSettings.COSMIC_DEFAULT)
                        && local.artisanPassives().equals(Passives.DEFAULT),
                "reset must restore both profiles' distinct defaults");
        local.restoreDefaults();
        check(local.artisan(false).equals(serverSettings.regular()) && local.artisan(true).equals(serverSettings.cosmic())
                && local.artisanPassives().equals(serverSettings.passives()) && !local.dirty(),
                "undo must restore the world settings");
    }

    private static void artisanAppearancePersistsOnClient() throws Exception {
        var directory = Files.createTempDirectory("shclib-artisan-test");
        var file = directory.resolve("artisan-client.properties");
        try {
            ArtisanAppearancePreferences.initialize(directory);
            check(ArtisanAppearancePreferences.targetColors(), "missing settings must enable target colors");
            check(ArtisanAppearancePreferences.save(false), "the target colors preference must save");
            ArtisanAppearancePreferences.initialize(directory);
            check(!ArtisanAppearancePreferences.targetColors(), "disabled target colors must survive reload");
            Files.writeString(file, "targetColors=invalid");
            ArtisanAppearancePreferences.initialize(directory);
            check(ArtisanAppearancePreferences.targetColors(), "invalid target colors must use the default");
        } finally {
            Files.deleteIfExists(file);
            Files.delete(directory);
        }
    }

    private static void jeiSettingsAreStagedAndRetryable() {
        var state = new ControlCenterDraft(PlayerInfoHudSettings.DEFAULT, false, false);
        var disk = new AtomicReference<>(state.jei());
        state.setJeiAe2Orderable(false);
        state.setJeiOptimizeRendering(true);
        state.setJeiEnabled(false);
        state.setJeiPrediction(false);
        state.setJeiRandom(false);
        state.setJeiChat(true);
        state.setJeiHot(true);
        state.setJeiHotShare(true);
        state.setJeiGlass(false);
        check(!state.jei().randomSource() && state.jei().questSource() && state.jei().chatSource()
                        && state.jei().hotSource() && state.jei().hotShare(),
                "other JEI edits must preserve staged source switches");
        check(disk.get().ae2Orderable() && !state.jei().ae2Orderable(), "AE2 badge edits stay staged across other changes");
        check(!disk.get().optimizeRendering() && state.jei().optimizeRendering(),
                "rendering optimization stays staged across other JEI edits");
        check(disk.get().enabled() && !state.jei().enabled() && disk.get().prediction() && state.dirty(), "JEI edits must stay staged");
        check(state.apply(value -> true, (a, p) -> true, value -> false) == ControlCenterDraft.ApplyResult.FAILED,
                "JEI save failure must be visible");
        check(state.dirty(), "failed JEI save remains staged");
        check(state.apply(value -> true, (a, p) -> true, value -> { disk.set(value); return true; })
                == ControlCenterDraft.ApplyResult.SAVED && !disk.get().enabled() && !disk.get().prediction() && !disk.get().glass()
                && !disk.get().ae2Orderable() && disk.get().optimizeRendering()
                && !disk.get().randomSource() && disk.get().questSource() && disk.get().chatSource() && !state.dirty(),
                "JEI settings can be retried");
        state.restoreDefaults();
        check(state.dirty() && !state.jei().optimizeRendering(), "restore defaults disables the rendering optimization");
        state.restoreDefaults();
        check(!state.dirty() && state.jei().optimizeRendering(), "undoing the reset restores the rendering optimization");
    }

    private static void stagedEditsAndUndo() {
        var initial = new PlayerInfoHudSettings(false, 0.4, 0.6, 125,
                PlayerInfoHudSettings.DEFAULT.visibleItems());
        var state = new ControlCenterDraft(initial, true, false);
        var disk = new AtomicReference<>(initial);
        state.setHudItem(PlayerInfoItem.TPS, true);
        check(!disk.get().shows(PlayerInfoItem.TPS), "edits must not write before Apply");
        check(state.dirty(), "changed item should enable Apply");
        state.restoreDefaults();
        check(state.hud().equals(PlayerInfoHudSettings.DEFAULT), "reset must include layout and HUD defaults");
        check(!state.animation() && !state.profiling(), "reset must include loading defaults");
        state.restoreDefaults();
        check(state.hud().shows(PlayerInfoItem.TPS) && state.hud().x() == 0.4,
                "undo reset must recover the previous draft");
        check(state.apply(value -> { disk.set(value); return true; }, (a, p) -> true)
                == ControlCenterDraft.ApplyResult.SAVED, "apply should report success");
        check(disk.get().shows(PlayerInfoItem.TPS) && !state.dirty(), "apply must save and clear pending state");
    }

    private static void partiallyFailedApplyCanBeRetried() {
        var initial = PlayerInfoHudSettings.DEFAULT;
        var state = new ControlCenterDraft(initial, false, false);
        var disk = new AtomicReference<>(initial);
        int[] hudWrites = {0};
        state.setHudItem(PlayerInfoItem.TPS, true);
        state.setAnimation(true);
        check(state.apply(value -> { hudWrites[0]++; disk.set(value); return true; }, (a, p) -> false)
                == ControlCenterDraft.ApplyResult.PARTIAL, "a failed second file must report partial save");
        check(disk.get().shows(PlayerInfoItem.TPS) && state.dirty(), "failed loading settings must remain pending");
        check(state.apply(value -> { hudWrites[0]++; return true; }, (a, p) -> a && !p)
                == ControlCenterDraft.ApplyResult.SAVED, "failed settings should be retryable");
        check(hudWrites[0] == 1 && !state.dirty(), "retry must not rewrite the already saved HUD");
    }

    private static void failedApplyKeepsAllChanges() {
        var state = new ControlCenterDraft(PlayerInfoHudSettings.DEFAULT, false, false);
        state.setHudItem(PlayerInfoItem.TPS, true);
        state.setProfiling(true);
        check(state.apply(value -> false, (a, p) -> false) == ControlCenterDraft.ApplyResult.FAILED,
                "failure in both files must be reported");
        check(state.dirty() && state.hud().shows(PlayerInfoItem.TPS) && state.profiling(),
                "a failed apply must retain both pending changes");
        check(state.apply(value -> true, (a, p) -> p) == ControlCenterDraft.ApplyResult.SAVED && !state.dirty(),
                "all failed changes must be retryable");
    }

    private static void layoutEditorChangesMergeWithPendingOptions() {
        var original = PlayerInfoHudSettings.DEFAULT;
        var state = new ControlCenterDraft(original, false, false);
        state.setHudItem(PlayerInfoItem.TPS, true);
        var fromEditor = new PlayerInfoHudSettings(false, 0.7, 0.3, 110, original.visibleItems());
        state.refreshHud(fromEditor);
        check(state.hud().shows(PlayerInfoItem.TPS), "editor return must preserve pending item changes");
        check(!state.hud().enabled() && state.hud().x() == 0.7 && state.hud().scalePercent() == 110,
                "editor changes must update untouched fields");
        state.setAnimation(true);
        state.restoreDefaults();
        var secondEdit = new PlayerInfoHudSettings(false, 0.2, 0.3, 110, original.visibleItems());
        state.refreshHud(secondEdit);
        state.restoreDefaults();
        check(state.hud().x() == 0.2 && state.hud().shows(PlayerInfoItem.TPS),
                "undo reset must preserve later editor changes as well as pending options");
    }

    private static void groupPreferencesSurviveEditorReturn() {
        var original = PlayerInfoHudSettings.DEFAULT;
        var state = new ControlCenterDraft(original, false, false);
        state.setHudGroup(HudGroups.Group.WORLD, false);
        var edited = original.withGroup(HudGroups.Group.AE2, false)
                .withDecorations(false, true).withLayout(true, 0.3, 0.6, 100);
        state.refreshHud(edited);
        check(!state.hud().showsGroup(HudGroups.Group.WORLD), "pending group visibility must survive editor return");
        check(!state.hud().showsGroup(HudGroups.Group.AE2) && !state.hud().showGroupTitles(),
                "untouched group settings must follow changes saved by the editor");
        check(state.hud().shows(PlayerInfoItem.WORLD_TIME), "hiding a group must not discard item preferences");
    }

    private static void optionalIntegrationsFollowLoadedMods() {
        java.util.Set<String> mods = new java.util.HashSet<>();
        check(!HudGroups.available(HudGroups.Group.AE2, mods::contains, true), "AE2 group must require AE2");
        check(!HudGroups.available(HudGroups.Group.FTB, mods::contains, true), "FTB group must require an FTB mod");
        mods.add("ftbquests");
        check(HudGroups.available(HudGroups.Group.FTB, mods::contains, true), "a loaded FTB mod enables its group");
        check(HudGroups.available(PlayerInfoItem.FTB_QUESTS, mods::contains, true), "loaded quests are available");
        check(!HudGroups.available(PlayerInfoItem.FTB_TEAM, mods::contains, true), "missing teams stay disabled");
        mods.add("ae2"); mods.add("neoecoae");
        check(HudGroups.available(PlayerInfoItem.NEOECO_NETWORK, mods::contains, true), "NeoEco needs both mods and adapter");
        check(!HudGroups.available(PlayerInfoItem.NEOECO_NETWORK, mods::contains, false), "unsupported loader stays disabled");
        check(HudGroups.available(PlayerInfoItem.FPS, mods::contains, false), "built-in items are always available");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
