// Author: dishanhai
package com.dishanhai.sh_common_lib.mixin.jei_ae2;

import appeng.api.stacks.AEKey;
import appeng.menu.me.common.GridInventoryEntry;
import com.dishanhai.sh_common_lib.api.client.ulk.compat.Ae2CraftableIndex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

@Pseudo
@Mixin(targets = "appeng.client.gui.me.common.Repo", remap = false)
abstract class Ae2CraftableIndexMixin implements Ae2CraftableIndex {
    @Shadow public abstract Set<GridInventoryEntry> getAllEntries();

    @Unique private Set<AEKey> shclib$craftableKeys;

    @Inject(method = "handleUpdate(ZLjava/util/List;)V", at = @At("HEAD"), require = 1)
    private void shclib$invalidateCraftableKeys(CallbackInfo ci) {
        shclib$craftableKeys = null;
    }

    @Override
    public Set<AEKey> shclib$craftableKeys() {
        if (shclib$craftableKeys == null) {
            var keys = new HashSet<AEKey>();
            for (var entry : getAllEntries()) {
                if (entry.isCraftable()) keys.add(entry.getWhat());
            }
            shclib$craftableKeys = Set.copyOf(keys);
        }
        return shclib$craftableKeys;
    }
}
