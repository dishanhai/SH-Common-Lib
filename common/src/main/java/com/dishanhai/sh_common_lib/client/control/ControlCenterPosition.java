// Author: dishanhai
package com.dishanhai.sh_common_lib.client.control;

import java.util.List;

/** Client-session navigation, independent of settings drafts and screen widgets. */
record ControlCenterPosition(boolean overview, int group, int overviewPage, ListPosition list) {
    static final ControlCenterPosition INITIAL = new ControlCenterPosition(false, 0, 0,
            new ListPosition(-1, 0d, 0d));

    record ListPosition(int subgroup, double rootOffset, double detailOffset) {
        SettingsGroup.Subgroup resolve(List<SettingsGroup.Entry> entries) {
            if (subgroup >= 0 && subgroup < entries.size() && entries.get(subgroup) instanceof SettingsGroup.Subgroup detail) {
                return detail;
            }
            return null;
        }
    }
}
