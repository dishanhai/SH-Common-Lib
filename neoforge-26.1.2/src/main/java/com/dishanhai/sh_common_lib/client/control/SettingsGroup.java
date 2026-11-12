// Author: dishanhai
package com.dishanhai.sh_common_lib.client.control;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

record SettingsGroup(Component title, List<Entry> entries, boolean twoColumns) {
    SettingsGroup {
        entries = List.copyOf(entries);
    }

    SettingsGroup(Component title, List<Entry> entries) {
        this(title, entries, false);
    }

    sealed interface Entry permits Toggle, Page {}

    record Toggle(Function<Boolean, Component> label, BooleanSupplier read, Predicate<Boolean> save)
            implements Entry {}

    record Page(Component label, Supplier<Component> unavailable,
                BiConsumer<Screen, Supplier<? extends GuiEventListener>> open) implements Entry {}
}
