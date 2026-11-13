// Author: dishanhai
package com.dishanhai.sh_common_lib.client.hud;

import com.dishanhai.sh_common_lib.api.ulk.InventorySource;
import com.dishanhai.sh_common_lib.api.ulk.InventorySnapshot;
import com.dishanhai.sh_common_lib.internal.performance.InventoryHudSnapshot;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/** Localized presentation for the bounded, server-side HUD snapshot. */
final class InventoryHudText {
    private static final String KEY = "hud.sh_common_lib.player_info.";
    private InventoryHudText() {}
    private static Component text(String key, Object... args) { return Component.translatable(KEY + key, args); }

    static Component summary(InventoryHudSnapshot snapshot) {
        if (snapshot == null || !snapshot.scanned()) return text("ulk_unavailable");
        int entries = 0;
        long amount = 0;
        boolean partial = false;
        for (var source : snapshot.sources().values()) {
            entries += source.entries();
            amount = Long.MAX_VALUE - amount < source.amount() ? Long.MAX_VALUE : amount + source.amount();
            partial |= source.status() == InventorySnapshot.Status.INCOMPLETE;
        }
        return text("ulk_summary", HudNumberFormatter.format(entries), HudNumberFormatter.format(amount),
                text(partial ? "ulk_partial" : "ulk_complete"));
    }

    static Component source(InventoryHudSnapshot snapshot, InventorySource source) {
        Component name = text("ulk_source." + source.name().toLowerCase(Locale.ROOT));
        if (snapshot == null || !snapshot.scanned()) return text("ulk_source_unavailable", name);
        var data = snapshot.sources().get(source);
        if (data == null) return text("ulk_source_unavailable", name);
        Component status = text("ulk_status." + data.status().name().toLowerCase(Locale.ROOT));
        return text("ulk_source_data", name, status, HudNumberFormatter.format(data.entries()), HudNumberFormatter.format(data.amount()));
    }

    static Component eco(InventoryHudSnapshot snapshot) {
        if (snapshot == null) return text("eco_unavailable");
        return switch (snapshot.ecoState()) {
            case InventoryHudSnapshot.ECO_NOT_INSTALLED -> text("eco_not_installed");
            case InventoryHudSnapshot.ECO_NO_TERMINAL -> text("eco_no_terminal");
            case InventoryHudSnapshot.ECO_CONNECTED -> text("eco_nodes", HudNumberFormatter.format(snapshot.ecoActive()),
                    HudNumberFormatter.format(snapshot.ecoNodes()), text(snapshot.ecoTruncated() ? "eco_partial" : "eco_complete"));
            default -> text("eco_unavailable");
        };
    }
}
