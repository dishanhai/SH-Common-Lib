// Author: dishanhai
package com.dishanhai.sh_common_lib.client.hud;

import com.dishanhai.sh_common_lib.internal.performance.EcoMachineSnapshot;
import net.minecraft.network.chat.Component;

final class EcoMachineHudText {
    private static final String KEY = "hud.sh_common_lib.player_info.";
    private EcoMachineHudText() {}
    private static Component text(String key, Object... args) { return Component.translatable(KEY + key, args); }
    static Component state(EcoMachineSnapshot snapshot) {
        if (snapshot == null) return text("eco_machine_unavailable");
        return switch (snapshot.state()) {
            case EcoMachineSnapshot.NOT_INSTALLED -> text("eco_not_installed");
            case EcoMachineSnapshot.NO_TERMINAL -> text("eco_no_terminal");
            default -> text("eco_machine_unavailable");
        };
    }
    static Component empty(int type) { return text("eco_machine_empty", name(type)); }
    static Component partial() { return text("eco_machine_partial"); }
    static Component title(EcoMachineSnapshot.Machine m) {
        return text("eco_machine_entry", name(m.type()), m.dimension().isEmpty() ? text("eco_machine_unknown") : m.dimension(),
                m.x(), m.y(), m.z(), text(m.active() ? "eco_machine_online" : "eco_machine_offline"));
    }
    static Component details(EcoMachineSnapshot.Machine m) {
        Component details = switch (m.type()) {
            case EcoMachineSnapshot.CRAFTING -> text("eco_machine_crafting_data", number(m.first()), number(m.second()), number(m.third()));
            case EcoMachineSnapshot.COMPUTATION -> text("eco_machine_computation_data", number(m.first()), number(m.second()), number(m.third()), number(m.fourth()));
            case EcoMachineSnapshot.STORAGE -> text("eco_machine_storage_data", number(m.first()), number(m.second()), bool(m.third()));
            case EcoMachineSnapshot.WORKSTATION -> text("eco_machine_workstation_data", number(m.first()), temperature(m.second()),
                    bool(m.third()), bool(m.fourth()));
            default -> text("eco_machine_unavailable");
        };
        return text("eco_machine_details", name(m.type()), m.x(), m.y(), m.z(), details);
    }
    private static Component name(int type) {
        return text(switch (type) {
            case EcoMachineSnapshot.CRAFTING -> "eco_machine_crafting";
            case EcoMachineSnapshot.COMPUTATION -> "eco_machine_computation";
            case EcoMachineSnapshot.STORAGE -> "eco_machine_storage";
            default -> "eco_machine_workstation";
        });
    }
    private static Object number(long value) { return value < 0 ? text("eco_machine_unknown") : HudNumberFormatter.format(value); }
    private static Object temperature(long value) {
        return value < 0 ? text("eco_machine_unknown") : HudNumberFormatter.format(value / 10.0, "%.1f");
    }
    private static Component bool(long value) { return text(value < 0 ? "eco_machine_unknown" : value == 0 ? "eco_machine_no" : "eco_machine_yes"); }
}
