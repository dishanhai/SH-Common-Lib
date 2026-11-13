// Author: dishanhai
package com.dishanhai.sh_common_lib.client.control;

import com.dishanhai.sh_common_lib.client.hud.PlayerInfoHudSettings;
import com.dishanhai.sh_common_lib.client.hud.PlayerInfoItem;
import com.dishanhai.sh_common_lib.client.hud.HudGroups;
import com.dishanhai.sh_common_lib.client.jei.JeiSearchSettings;

import java.util.EnumSet;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/** Stages the settings owned by the control center until the player applies them. */
final class ControlCenterDraft {
    enum ApplyResult { SAVED, PARTIAL, FAILED }

    private static final Values DEFAULTS = new Values(PlayerInfoHudSettings.DEFAULT, false, false, JeiSearchSettings.DEFAULT);
    private Values saved;
    private Values draft;
    private Values beforeReset;

    ControlCenterDraft(PlayerInfoHudSettings hud, boolean animation, boolean profiling) {
        this(hud, animation, profiling, JeiSearchSettings.DEFAULT);
    }

    ControlCenterDraft(PlayerInfoHudSettings hud, boolean animation, boolean profiling, JeiSearchSettings jei) {
        saved = new Values(hud, animation, profiling, jei);
        draft = saved;
    }

    PlayerInfoHudSettings hud() { return draft.hud(); }
    boolean animation() { return draft.animation(); }
    boolean profiling() { return draft.profiling(); }
    JeiSearchSettings jei() { return draft.jei(); }
    boolean dirty() { return !draft.equals(saved); }
    boolean canReset() { return beforeReset != null || !draft.equals(DEFAULTS); }
    boolean canUndoReset() { return beforeReset != null; }

    void setHudEnabled(boolean enabled) { edit(draft.withHud(draft.hud().withEnabled(enabled))); }
    void setHudItem(PlayerInfoItem item, boolean visible) { edit(draft.withHud(draft.hud().withItem(item, visible))); }
    void setHudGroup(HudGroups.Group group, boolean visible) { edit(draft.withHud(draft.hud().withGroup(group, visible))); }
    void setHudTitles(boolean value) { edit(draft.withHud(draft.hud().withDecorations(value, draft.hud().showGroupSeparators()))); }
    void setHudSeparators(boolean value) { edit(draft.withHud(draft.hud().withDecorations(draft.hud().showGroupTitles(), value))); }
    void setAnimation(boolean value) { edit(new Values(draft.hud(), value, draft.profiling(), draft.jei())); }
    void setProfiling(boolean value) { edit(new Values(draft.hud(), draft.animation(), value, draft.jei())); }
    void setJeiPrediction(boolean value) { var s = jei(); editJei(new JeiSearchSettings(value, s.history(), s.hud(), s.preview(), s.hints(), s.glass())); }
    void setJeiHistory(boolean value) { var s = jei(); editJei(new JeiSearchSettings(s.prediction(), value, s.hud(), s.preview(), s.hints(), s.glass())); }
    void setJeiHud(boolean value) { var s = jei(); editJei(new JeiSearchSettings(s.prediction(), s.history(), value, s.preview(), s.hints(), s.glass())); }
    void setJeiPreview(boolean value) { var s = jei(); editJei(new JeiSearchSettings(s.prediction(), s.history(), s.hud(), value, s.hints(), s.glass())); }
    void setJeiHints(boolean value) { var s = jei(); editJei(new JeiSearchSettings(s.prediction(), s.history(), s.hud(), s.preview(), value, s.glass())); }
    void setJeiGlass(boolean value) { var s = jei(); editJei(new JeiSearchSettings(s.prediction(), s.history(), s.hud(), s.preview(), s.hints(), value)); }
    private void editJei(JeiSearchSettings value) {
        var old = jei();
        edit(draft.withJei(value.withSources(old.randomSource(), old.questSource(), old.chatSource())));
    }
    void setJeiRandom(boolean value) { var s = jei(); edit(draft.withJei(s.withSources(value, s.questSource(), s.chatSource()))); }
    void setJeiQuest(boolean value) { var s = jei(); edit(draft.withJei(s.withSources(s.randomSource(), value, s.chatSource()))); }
    void setJeiChat(boolean value) { var s = jei(); edit(draft.withJei(s.withSources(s.randomSource(), s.questSource(), value))); }

    void restoreDefaults() {
        if (beforeReset != null) {
            draft = beforeReset;
            beforeReset = null;
        } else if (!draft.equals(DEFAULTS)) {
            beforeReset = draft;
            draft = DEFAULTS;
        }
    }

    /** Reconcile changes saved by the separate HUD editor without discarding staged values. */
    void refreshHud(PlayerInfoHudSettings current) {
        if (current.equals(saved.hud())) return;
        PlayerInfoHudSettings previous = saved.hud();
        draft = draft.withHud(merge(previous, draft.hud(), current));
        if (beforeReset != null) beforeReset = beforeReset.withHud(merge(previous, beforeReset.hud(), current));
        saved = saved.withHud(current);
    }

    ApplyResult apply(Predicate<PlayerInfoHudSettings> saveHud, BiPredicate<Boolean, Boolean> saveLoading) {
        return apply(saveHud, saveLoading, ignored -> true);
    }

    ApplyResult apply(Predicate<PlayerInfoHudSettings> saveHud, BiPredicate<Boolean, Boolean> saveLoading,
                      Predicate<JeiSearchSettings> saveJei) {
        int successes = 0;
        int failures = 0;
        if (!draft.hud().equals(saved.hud())) {
            if (saveHud.test(draft.hud())) {
                saved = saved.withHud(draft.hud());
                successes++;
            } else failures++;
        }
        if (draft.animation() != saved.animation() || draft.profiling() != saved.profiling()) {
            if (saveLoading.test(draft.animation(), draft.profiling())) {
                saved = new Values(saved.hud(), draft.animation(), draft.profiling(), saved.jei());
                successes++;
            } else failures++;
        }
        if (!draft.jei().equals(saved.jei())) {
            if (saveJei.test(draft.jei())) {
                saved = saved.withJei(draft.jei());
                successes++;
            } else failures++;
        }
        if (successes > 0) beforeReset = null;
        if (failures == 0) return ApplyResult.SAVED;
        return successes > 0 ? ApplyResult.PARTIAL : ApplyResult.FAILED;
    }

    private void edit(Values value) {
        if (!draft.equals(value)) {
            draft = value;
            beforeReset = null;
        }
    }

    private static PlayerInfoHudSettings merge(PlayerInfoHudSettings old, PlayerInfoHudSettings staged,
                                                PlayerInfoHudSettings current) {
        var items = EnumSet.noneOf(PlayerInfoItem.class);
        for (var item : PlayerInfoItem.values()) {
            boolean value = staged.shows(item) != old.shows(item) ? staged.shows(item) : current.shows(item);
            if (value) items.add(item);
        }
        var groups = EnumSet.noneOf(HudGroups.Group.class);
        for (var group : HudGroups.Group.values()) {
            boolean value = staged.showsGroup(group) != old.showsGroup(group) ? staged.showsGroup(group) : current.showsGroup(group);
            if (value) groups.add(group);
        }
        return new PlayerInfoHudSettings(staged.enabled() != old.enabled() ? staged.enabled() : current.enabled(),
                Double.compare(staged.x(), old.x()) != 0 ? staged.x() : current.x(),
                Double.compare(staged.y(), old.y()) != 0 ? staged.y() : current.y(),
                staged.scalePercent() != old.scalePercent() ? staged.scalePercent() : current.scalePercent(), items, groups,
                staged.showGroupTitles() != old.showGroupTitles() ? staged.showGroupTitles() : current.showGroupTitles(),
                staged.showGroupSeparators() != old.showGroupSeparators() ? staged.showGroupSeparators() : current.showGroupSeparators());
    }

    private record Values(PlayerInfoHudSettings hud, boolean animation, boolean profiling, JeiSearchSettings jei) {
        private Values { Objects.requireNonNull(hud); Objects.requireNonNull(jei); }
        private Values withHud(PlayerInfoHudSettings value) { return new Values(value, animation, profiling, jei); }
        private Values withJei(JeiSearchSettings value) { return new Values(hud, animation, profiling, value); }
    }
}
