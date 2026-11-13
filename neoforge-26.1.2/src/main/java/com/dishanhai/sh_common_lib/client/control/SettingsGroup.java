// Author: dishanhai
package com.dishanhai.sh_common_lib.client.control;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

record SettingsGroup(Component title, GraphicIcon icon, List<Entry> entries, boolean twoColumns) {
    SettingsGroup {
        entries = List.copyOf(entries);
    }

    SettingsGroup(Component title, GraphicIcon icon, List<Entry> entries) {
        this(title, icon, entries, false);
    }

    sealed interface Entry permits Toggle, Page, Subgroup {}

    record Toggle(Function<Boolean, Component> label, BooleanSupplier read, Consumer<Boolean> edit,
                  BooleanSupplier available) implements Entry {
        Toggle(Function<Boolean, Component> label, BooleanSupplier read, Consumer<Boolean> edit) {
            this(label, read, edit, () -> true);
        }
        Component text(boolean enabled) {
            var value = label.apply(enabled);
            return available.getAsBoolean() ? value : Component.translatable(
                    "screen.sh_common_lib.control_center.not_enabled", value);
        }
    }

    record Subgroup(Component title, Toggle toggle, List<Entry> entries) implements Entry {
        Subgroup { entries = List.copyOf(entries); }
    }

    record Page(Component label, GraphicIcon icon, Supplier<Component> unavailable,
                BiConsumer<Screen, Supplier<? extends GuiEventListener>> open) implements Entry {}
}
