// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.ulk.compat;

import appeng.api.stacks.AEKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/** Exposes the primary output of the encoded pattern currently being previewed. */
public interface Ae2PatternPreview {
    /** Returns null if the preview has no order target. */
    AEKey shclib$primaryOutput();

    /** Call on the client thread with AE2 loaded. */
    static AEKey activeOutput() {
        if (Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen
                && screen.getMenu() instanceof Ae2PatternPreview preview) {
            return preview.shclib$primaryOutput();
        }
        return null;
    }
}
