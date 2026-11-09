package com.dishanhai.sh_common_lib.neoforge;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(ShCommonLib.MOD_ID)
public final class NeoForgeShCommonLib {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ShCommonLib.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ShCommonLib.MOD_ID);

    public NeoForgeShCommonLib(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }
}
