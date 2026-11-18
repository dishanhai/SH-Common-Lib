package com.dishanhai.sh_common_lib.neoforge;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import com.dishanhai.sh_common_lib.api.ulk.fumo.Fumos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import com.dishanhai.sh_common_lib.internal.beacon.CosmicBeacon;
import com.dishanhai.sh_common_lib.internal.beacon.CosmicBeaconBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import com.dishanhai.sh_common_lib.api.ulk.UnifiedLibKernel;
import com.dishanhai.sh_common_lib.neoforge.ulk.NeoForgeUlkPlatform;
import com.dishanhai.sh_common_lib.api.client.announcement.DefaultAnnouncement;
import com.dishanhai.sh_common_lib.api.parcel.ParcelStorage;
import net.neoforged.fml.ModContainer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(ShCommonLib.MOD_ID)
public final class NeoForgeShCommonLib {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ShCommonLib.MOD_ID);

    private static final DeferredRegister.Items BLOCK_ITEMS = DeferredRegister.createItems(ShCommonLib.MOD_ID);
    private static final net.neoforged.neoforge.registries.DeferredBlock<CosmicBeaconBlock> BEACON = BLOCKS.registerBlock(
            CosmicBeacon.BLOCK_ID, properties -> new CosmicBeaconBlock(properties, NeoForgeBeaconNetwork::reply),
            BlockBehaviour.Properties::of);
    private static final net.neoforged.neoforge.registries.DeferredItem<BlockItem> BEACON_ITEM =
            BLOCK_ITEMS.registerSimpleBlockItem(BEACON);
    private static final String DISHANHAI_FUMO = "dishanhai_fumo";
    private static final String BEIBEIPUO_FUMO = "beibeipuo_fumo";
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ShCommonLib.MOD_ID);

    public NeoForgeShCommonLib(IEventBus modBus, ModContainer container) {
        NeoForgeArtisanConfig.register(container);
        NeoForgeRecipeBrewing.register();
        UnifiedLibKernel.install(new NeoForgeUlkPlatform());
        var fumos = Fumos.register(modBus, BLOCKS, BLOCK_ITEMS, BLOCK_ENTITIES,
                DISHANHAI_FUMO, DISHANHAI_FUMO, BEIBEIPUO_FUMO);
        var fallbackFumo = Identifier.fromNamespaceAndPath("sh_better_ui", DISHANHAI_FUMO);
        var currentFumo = Identifier.fromNamespaceAndPath(ShCommonLib.MOD_ID, DISHANHAI_FUMO);
        BLOCKS.addAlias(fallbackFumo, currentFumo);
        BLOCK_ITEMS.addAlias(fallbackFumo, currentFumo);
        BLOCK_ENTITIES.addAlias(fallbackFumo, currentFumo);
        var fallbackBeiBeiPuo = Identifier.fromNamespaceAndPath("sh_better_ui", BEIBEIPUO_FUMO);
        var currentBeiBeiPuo = Identifier.fromNamespaceAndPath(ShCommonLib.MOD_ID, BEIBEIPUO_FUMO);
        BLOCKS.addAlias(fallbackBeiBeiPuo, currentBeiBeiPuo);
        BLOCK_ITEMS.addAlias(fallbackBeiBeiPuo, currentBeiBeiPuo);
        BLOCKS.register(modBus);
        BLOCK_ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey().equals(CreativeModeTabs.FUNCTIONAL_BLOCKS)) event.accept(BEACON_ITEM.get());
            if (event.getTabKey().equals(CreativeModeTabs.COLORED_BLOCKS)) {
                event.accept(fumos.variant(DISHANHAI_FUMO).item().get());
                event.accept(fumos.variant(BEIBEIPUO_FUMO).item().get());
            }
        });
        modBus.addListener(NeoForgeBeaconNetwork::register);
        ParcelStorage.register(modBus);
        NeoForgeArtisan.register(modBus);
        NeoForgeCosmicHorizon.register(modBus);
        NeoForgeCosmicArtisanEffects.register();
        InfinityCell.register(modBus);
        modBus.addListener(NeoForgeTickNetwork::register);
        com.dishanhai.sh_common_lib.internal.ulk.ServerHotWords.initialize(FMLPaths.CONFIGDIR.get());
        modBus.addListener(NeoForgeHotWordsNetwork::register);
        modBus.addListener(NeoForgeCosmicModesNetwork::register);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent event) ->
                com.dishanhai.sh_common_lib.internal.ulk.ServerHotWords.clear(event.getServer()));
        if (ModList.get().isLoaded("ae2")) {
            Ae2InfinityCellStorage.register(modBus);
            modBus.addListener(NeoForgeJeiOrderableNetwork::register);
        }
        if (FMLEnvironment.getDist().isClient()) {
            modBus.addListener(NeoForgeArtisanClient::registerModel);
            NeoForgeArtisanClient.registerQuickConfig(modBus);
            NeoForgeArtisanClient.registerBeaconMarkers(modBus);
            NeoForgeLoading.register();
            var announcement = new DefaultAnnouncement(FMLPaths.CONFIGDIR.get());
            NeoForge.EVENT_BUS.addListener((ScreenEvent.Init.Post event) -> announcement.onScreenInit(event.getScreen()));
            modBus.addListener(InfinityCellDecoration::register);
            modBus.addListener(InfinityCellDecoration::registerTooltip);
            NeoForgePlayerInfoHud.register(modBus);
            if (ModList.get().isLoaded("jei")) {
                if (ModList.get().isLoaded("ae2")) NeoForgeJeiOrderableNetwork.bindClient();
                NeoForgeJeiShortcuts.register();
            }
        }
    }

    static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> orderablePayloadType(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ShCommonLib.MOD_ID, path));
    }

    static boolean canReachTerminal(ServerPlayer player, BlockPos pos) {
        return player.isWithinBlockInteractionRange(pos, 0);
    }
}
