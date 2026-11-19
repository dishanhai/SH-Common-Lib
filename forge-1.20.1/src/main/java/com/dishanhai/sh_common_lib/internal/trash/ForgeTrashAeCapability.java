// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.trash;

import appeng.capabilities.Capabilities;
import com.dishanhai.sh_common_lib.api.ulk.trash.TrashCan.Extension;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

final class ForgeTrashAeCapability {
    static <T> LazyOptional<T> get(PlatformTrashCanEntity entity, Capability<T> capability) {
        if (capability != Capabilities.IN_WORLD_GRID_NODE_HOST) return LazyOptional.empty();
        var runtime = entity.module(Extension.ENERGY);
        return runtime == null ? LazyOptional.empty() : entity.capability(capability, () -> runtime);
    }
}
