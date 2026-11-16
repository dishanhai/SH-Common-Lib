package com.dishanhai.sh_common_lib.forge;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import com.dishanhai.sh_common_lib.internal.beacon.CosmicBeacon;
import com.dishanhai.sh_common_lib.internal.beacon.CosmicBeaconBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import com.dishanhai.sh_common_lib.api.ulk.UnifiedLibKernel;
import com.dishanhai.sh_common_lib.forge.ulk.ForgeUlkPlatform;
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

    private static final DeferredRegister<Item> BEACON_ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ShCommonLib.MOD_ID);
    private static final net.minecraftforge.registries.RegistryObject<CosmicBeaconBlock> BEACON = BLOCKS.register(CosmicBeacon.BLOCK_ID,
            () -> new CosmicBeaconBlock(BlockBehaviour.Properties.of(), ForgeBeaconNetwork::reply));
    private static final net.minecraftforge.registries.RegistryObject<Item> BEACON_ITEM = BEACON_ITEMS.register(
            CosmicBeacon.BLOCK_ID, () -> new BlockItem(BEACON.get(), new Item.Properties()));

    public ForgeShCommonLib(FMLJavaModLoadingContext context) {
        ForgeArtisanConfig.register(context);
        IEventBus modBus = context.getModEventBus();
        ForgeRecipeBrewing.register(modBus);
        UnifiedLibKernel.install(new ForgeUlkPlatform());
        BLOCKS.register(modBus);
        BEACON_ITEMS.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey().equals(CreativeModeTabs.FUNCTIONAL_BLOCKS)) event.accept(BEACON_ITEM.get());
        });
        ForgeBeaconNetwork.register();
        ParcelStorage.register(modBus);
        ForgeArtisan.register(modBus);
        ForgeCosmicHorizon.register(modBus);
        ForgeCosmicArtisanEffects.register();
        InfinityCell.register(modBus);
        ForgeTickNetwork.register();
        com.dishanhai.sh_common_lib.internal.ulk.ServerHotWords.initialize(FMLPaths.CONFIGDIR.get());
        ForgeHotWordsNetwork.register();
        if (ModList.get().isLoaded("ae2")) {
            Ae2InfinityCellStorage.register(modBus);
            ForgeJeiOrderableNetwork.register();
        }
        ForgeCosmicModesNetwork.register();
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStoppedEvent event) ->
                com.dishanhai.sh_common_lib.internal.ulk.ServerHotWords.clear(event.getServer()));
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modBus.addListener(InfinityCellDecoration::register);
            modBus.addListener(InfinityCellDecoration::registerTooltip);
            modBus.addListener(ForgeArtisanClient::registerModel);
            ForgeArtisanClient.registerQuickConfig(modBus);
            ForgeArtisanClient.registerBeaconMarkers();
            ForgeLoading.register();
            var announcement = new DefaultAnnouncement(FMLPaths.CONFIGDIR.get());
            MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Init.Post event) -> announcement.onScreenInit(event.getScreen()));
            MinecraftForge.EVENT_BUS.addListener((ScreenEvent.KeyPressed.Pre event) -> {
                if (com.dishanhai.sh_common_lib.client.control.ControlCenterModule.handleScreenKey(
                        event.getScreen(), event.getKeyCode(), event.getScanCode())) event.setCanceled(true);
            });
            ForgePlayerInfoHud.register(modBus);
            if (ModList.get().isLoaded("jei")) {
                if (ModList.get().isLoaded("ae2")) ForgeJeiOrderableNetwork.bindClient();
                ForgeJeiShortcuts.register(!ModList.get().isLoaded("gt_shanhai"));
            }
        }
    }
}
