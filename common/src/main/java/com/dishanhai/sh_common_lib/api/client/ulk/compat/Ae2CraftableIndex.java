// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.ulk.compat;

import appeng.api.stacks.AEKey;
import appeng.menu.me.common.MEStorageMenu;
import net.minecraft.client.Minecraft;

import java.util.Set;
import java.util.function.Supplier;

/** Client-side view of the craftable keys synchronized by AE2 or the platform. */
public interface Ae2CraftableIndex {
    Set<AEKey> shclib$craftableKeys();

    /** Call on the client thread with AE2 loaded. The returned set is immutable. */
    static Set<AEKey> activeKeys() {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof MEStorageMenu menu
                && menu.getClientRepo() instanceof Ae2CraftableIndex index) {
            return index.shclib$craftableKeys();
        }
        return Background.source.get();
    }

    /** Installs the client-thread source used outside ME storage terminals. */
    static void installBackground(Supplier<Set<AEKey>> source) {
        Background.source = java.util.Objects.requireNonNull(source);
    }

    final class Background {
        private static Supplier<Set<AEKey>> source = Set::of;

        private Background() {}
    }
}
