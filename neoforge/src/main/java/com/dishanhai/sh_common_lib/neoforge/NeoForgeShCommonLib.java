package com.dishanhai.sh_common_lib.neoforge;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import com.dishanhai.sh_common_lib.api.parcel.ParcelStorage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(ShCommonLib.MOD_ID)
public final class NeoForgeShCommonLib {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ShCommonLib.MOD_ID);

    public NeoForgeShCommonLib(IEventBus modBus) {
        BLOCKS.register(modBus);
        ParcelStorage.register(modBus);
        InfinityCell.register(modBus);
        if (ModList.get().isLoaded("ae2")) Ae2InfinityCellStorage.register(modBus);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForgePlayerInfoHud.register(modBus);
            if (ModList.get().isLoaded("jei")) NeoForgeJeiShortcuts.register();
        }
    }
}
