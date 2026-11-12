package com.dishanhai.sh_common_lib.forge;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import com.dishanhai.sh_common_lib.api.client.announcement.DefaultAnnouncement;
import com.dishanhai.sh_common_lib.api.parcel.ParcelStorage;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

@Mod(ShCommonLib.MOD_ID)
public final class ForgeShCommonLib {
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ShCommonLib.MOD_ID);

    public ForgeShCommonLib(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        BLOCKS.register(modBus);
        ParcelStorage.register(modBus);
        ForgeTickNetwork.register();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ForgeLoading.register();
            var announcement = new DefaultAnnouncement(FMLPaths.CONFIGDIR.get());
            MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Init.Post event) -> announcement.onScreenInit(event.getScreen()));
            ForgePlayerInfoHud.register(modBus);
            if (ModList.get().isLoaded("jei")) {
                ForgeJeiShortcuts.register(!ModList.get().isLoaded("gt_shanhai"));
            }
        }
    }
}
