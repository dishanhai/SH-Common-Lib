// Author: dishanhai
package com.dishanhai.sh_common_lib.client.hud;

import com.dishanhai.sh_better_ui.client.gui.text.UiShortcutKeycaps;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

final class HudShortcutHints {
    private static final String SHBUI_PANEL_KEY = "key.sh_better_ui.control_panel";

    private HudShortcutHints() {}

    static Component shbuiPanel(Minecraft client) {
        for (var mapping : client.options.keyMappings) {
            if (SHBUI_PANEL_KEY.equals(mapping.getName())) return UiShortcutKeycaps.binding(mapping);
        }
        return UiShortcutKeycaps.unbound();
    }

    static Component pageKey(KeyMapping mapping, int defaultKey) {
        return mapping == null ? UiShortcutKeycaps.chord(UiShortcutKeycaps.Modifier.ALT, UiShortcutKeycaps.key(defaultKey))
                : UiShortcutKeycaps.binding(mapping);
    }
}
