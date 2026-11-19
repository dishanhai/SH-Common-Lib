// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.trash;

import appeng.api.AECapabilities;
import com.dishanhai.sh_common_lib.api.ulk.trash.TrashCan.Extension;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

final class NeoTrashAeCapability {
    static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, TrashCanPlatform.ENTITY.get(),
                (entity, ignored) -> (TrashAeEnergy) entity.module(Extension.ENERGY));
    }
}
