// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.trash;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import com.dishanhai.sh_common_lib.api.ulk.trash.TrashCan;
import com.dishanhai.sh_common_lib.api.ulk.trash.TrashCan.Extension;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.VoidingResourceHandler;
import net.neoforged.neoforge.transfer.energy.VoidingEnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

public final class TrashCanPlatform {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ShCommonLib.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ShCommonLib.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ShCommonLib.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ShCommonLib.MOD_ID);

    private static final net.neoforged.neoforge.registries.DeferredBlock<TrashCanBlock> BLOCK = BLOCKS.registerBlock(TrashCan.ID, TrashCanPlatform::newBlock);
    private static final DeferredItem<BlockItem> ITEM = ITEMS.registerSimpleBlockItem(BLOCK);
    static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PlatformTrashCanEntity>> ENTITY =
            ENTITIES.register(TrashCan.ID, () -> new BlockEntityType<>(PlatformTrashCanEntity::new, BLOCK.get()));
    static final DeferredHolder<MenuType<?>, MenuType<TrashCanMenu>> MENU = MENUS.register(TrashCan.ID,
            () -> new MenuType<>(TrashCanMenu::new, FeatureFlags.DEFAULT_FLAGS));
    private static final Map<Extension, DeferredItem<TrashCanModuleItem>> MODULE_ITEMS = new EnumMap<>(Extension.class);
    private static final Map<Extension, Function<TrashCanBlockEntity, TrashCanRuntime>> FACTORIES = new EnumMap<>(Extension.class);

    private TrashCanPlatform() {}

    static Map<Extension, Function<TrashCanBlockEntity, TrashCanRuntime>> factories() {
        return FACTORIES;
    }

    static boolean aeLoaded() {
        return ModList.get().isLoaded("ae2");
    }

    private static TrashCanBlock newBlock(BlockBehaviour.Properties properties) {
        return new TrashCanBlock(properties.strength(2.5F, 6).sound(SoundType.METAL), ENTITY);
    }

    static boolean available(Extension extension) {
        return switch (extension) {
            case CHEMICAL -> ModList.get().isLoaded("mekanism");
            case SOURCE, STRESS -> false;
            case ENERGY -> true;
        };
    }

    public static void register(IEventBus modBus) {
        for (var extension : Extension.values()) {
            MODULE_ITEMS.put(extension, ITEMS.registerItem(extension.itemId(),
                    properties -> new TrashCanModuleItem(properties, extension)));
        }
        if (aeLoaded()) FACTORIES.put(Extension.ENERGY, TrashAeEnergy::new);

        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(TrashCanPlatform::registerCapabilities);
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (!event.getTabKey().equals(CreativeModeTabs.FUNCTIONAL_BLOCKS)) return;
            event.accept(ITEM.get());
            MODULE_ITEMS.forEach((extension, item) -> {
                if (available(extension)) event.accept(item.get());
            });
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (PlayerInteractEvent.RightClickBlock event) -> {
            if (!event.getUseBlock().isFalse()
                    && TrashCanBlockEntity.use(event.getLevel(), event.getPos(), event.getEntity(), event.getHand())) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
        });
        if (FMLEnvironment.getDist().isClient()) TrashCanClient.register(modBus);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        var items = new VoidingResourceHandler<>(ItemResource.EMPTY);
        var fluids = new VoidingResourceHandler<>(FluidResource.EMPTY);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ENTITY.get(), (entity, side) -> items);
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ENTITY.get(), (entity, side) -> fluids);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ENTITY.get(),
                (entity, side) -> entity.hasModule(Extension.ENERGY) ? VoidingEnergyHandler.INSTANCE : null);
        if (aeLoaded()) NeoTrashAeCapability.register(event);
        if (available(Extension.CHEMICAL)) NeoTrashChemicals.register(event);
    }
}
