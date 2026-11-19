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
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class TrashCanPlatform {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ShCommonLib.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ShCommonLib.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ShCommonLib.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, ShCommonLib.MOD_ID);

    private static final RegistryObject<TrashCanBlock> BLOCK = BLOCKS.register(TrashCan.ID, TrashCanPlatform::newBlock);
    private static final RegistryObject<Item> ITEM = ITEMS.register(TrashCan.ID,
            () -> new BlockItem(BLOCK.get(), new Item.Properties()));
    static final RegistryObject<BlockEntityType<PlatformTrashCanEntity>> ENTITY = ENTITIES.register(TrashCan.ID,
            () -> BlockEntityType.Builder.of(PlatformTrashCanEntity::new, BLOCK.get()).build(null));
    static final RegistryObject<MenuType<TrashCanMenu>> MENU = MENUS.register(TrashCan.ID,
            () -> new MenuType<>(TrashCanMenu::new, FeatureFlags.DEFAULT_FLAGS));
    private static final Map<Extension, RegistryObject<TrashCanModuleItem>> MODULE_ITEMS = new EnumMap<>(Extension.class);
    private static final Map<Extension, Function<TrashCanBlockEntity, TrashCanRuntime>> FACTORIES = new EnumMap<>(Extension.class);

    private TrashCanPlatform() {}

    static Map<Extension, Function<TrashCanBlockEntity, TrashCanRuntime>> factories() {
        return FACTORIES;
    }

    static boolean aeLoaded() {
        return ModList.get().isLoaded("ae2");
    }

    private static TrashCanBlock newBlock() {
        return new TrashCanBlock(BlockBehaviour.Properties.of().strength(2.5F, 6).sound(SoundType.METAL), ENTITY);
    }

    static boolean available(Extension extension) {
        return switch (extension) {
            case CHEMICAL -> ModList.get().isLoaded("mekanism");
            case SOURCE -> ModList.get().isLoaded("ars_nouveau");
            case STRESS -> ModList.get().isLoaded("create");
            case ENERGY -> true;
        };
    }

    public static void register(IEventBus modBus) {
        for (var extension : Extension.values()) {
            MODULE_ITEMS.put(extension, ITEMS.register(extension.itemId(),
                    () -> new TrashCanModuleItem(new Item.Properties(), extension)));
        }
        if (aeLoaded()) FACTORIES.put(Extension.ENERGY, TrashAeEnergy::new);
        if (available(Extension.STRESS)) FACTORIES.put(Extension.STRESS, TrashStress::new);
        if (available(Extension.SOURCE)) FACTORIES.put(Extension.SOURCE, ForgeTrashSource::new);

        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (!event.getTabKey().equals(CreativeModeTabs.FUNCTIONAL_BLOCKS)) return;
            event.accept(ITEM.get());
            MODULE_ITEMS.forEach((extension, item) -> {
                if (available(extension)) event.accept(item.get());
            });
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (PlayerInteractEvent.RightClickBlock event) -> {
            if (event.getUseBlock() != Event.Result.DENY
                    && TrashCanBlockEntity.use(event.getLevel(), event.getPos(), event.getEntity(), event.getHand())) {
                event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
                event.setCanceled(true);
            }
        });
        if (FMLEnvironment.dist.isClient()) TrashCanClient.register(modBus);
    }
}
