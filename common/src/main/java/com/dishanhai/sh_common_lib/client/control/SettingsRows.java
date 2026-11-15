// Author: dishanhai
package com.dishanhai.sh_common_lib.client.control;

import java.util.List;

/** Maps settings entries to grid slots, with each section title starting on its own row. */
record SettingsRows(int[] slots, int rowCount) {
    static SettingsRows of(List<SettingsGroup.Entry> entries, int columns) {
        int[] slots = new int[entries.size()];
        int next = 0;
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index) instanceof SettingsGroup.Heading) {
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
