// Author: dishanhai
package com.dishanhai.sh_common_lib.client.control;

import com.dishanhai.sh_common_lib.client.hud.PlayerInfoHudSettings;
import com.dishanhai.sh_common_lib.client.hud.PlayerInfoHudSettingsIoTest;
import com.dishanhai.sh_common_lib.client.hud.PlayerInfoItem;
import com.dishanhai.sh_common_lib.client.hud.HudGroups;

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
        ControlCenterLayoutTest.run();
        PlayerInfoHudSettingsIoTest.run();
        System.out.println("Control center draft checks passed");
    }

    private static void jeiSettingsAreStagedAndRetryable() {
        var state = new ControlCenterDraft(PlayerInfoHudSettings.DEFAULT, false, false);
        var disk = new AtomicReference<>(state.jei());
        state.setJeiPrediction(false);
        state.setJeiRandom(false);
        state.setJeiChat(true);
        state.setJeiGlass(false);
        check(!state.jei().randomSource() && state.jei().questSource() && state.jei().chatSource(),
                "other JEI edits must preserve staged source switches");
        check(disk.get().prediction() && state.dirty(), "JEI edits must stay staged");
        check(state.apply(value -> true, (a, p) -> true, value -> false) == ControlCenterDraft.ApplyResult.FAILED,
                "JEI save failure must be visible");
        check(state.dirty(), "failed JEI save remains staged");
        check(state.apply(value -> true, (a, p) -> true, value -> { disk.set(value); return true; })
                == ControlCenterDraft.ApplyResult.SAVED && !disk.get().prediction() && !disk.get().glass()
                && !disk.get().randomSource() && disk.get().questSource() && disk.get().chatSource() && !state.dirty(),
                "JEI settings can be retried");
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
