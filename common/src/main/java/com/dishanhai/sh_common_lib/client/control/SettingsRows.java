// Author: dishanhai
package com.dishanhai.sh_common_lib.client.control;

import java.util.List;

/** Maps settings entries to grid slots, with section titles and standalone detail links spanning a row. */
record SettingsRows(int[] slots, int rowCount) {
    static SettingsRows of(List<SettingsGroup.Entry> entries, int columns) {
        int[] slots = new int[entries.size()];
        int next = 0;
        for (int index = 0; index < entries.size(); index++) {
            var entry = entries.get(index);
            if (entry instanceof SettingsGroup.Heading
                    || entry instanceof SettingsGroup.Subgroup subgroup && !subgroup.hasToggle()) {
                next = (next + columns - 1) / columns * columns;
                slots[index] = next;
                next += columns;
            } else {
                slots[index] = next++;
            }
        }
        return new SettingsRows(slots, (next + columns - 1) / columns);
    }

    int slot(int index) { return slots[index]; }
}
