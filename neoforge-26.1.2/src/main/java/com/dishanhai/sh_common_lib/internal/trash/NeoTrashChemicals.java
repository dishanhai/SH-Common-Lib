// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.trash;

import com.dishanhai.sh_common_lib.api.ulk.trash.TrashCan.Extension;
import mekanism.api.chemical.ChemicalResource;
import mekanism.common.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.VoidingResourceHandler;

final class NeoTrashChemicals {
    static void register(RegisterCapabilitiesEvent event) {
        var sink = new VoidingResourceHandler<>(ChemicalResource.EMPTY);
        event.registerBlockEntity(Capabilities.CHEMICAL.block(), TrashCanPlatform.ENTITY.get(),
                (entity, side) -> entity.hasModule(Extension.CHEMICAL) ? sink : null);
    }
}
