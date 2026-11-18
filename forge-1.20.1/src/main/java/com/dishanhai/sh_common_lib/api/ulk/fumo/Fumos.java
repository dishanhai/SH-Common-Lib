// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.fumo;

import com.dishanhai.sh_common_lib.internal.block.DishanhaiFumoBlock;
import com.dishanhai.sh_common_lib.internal.block.FumoRenderer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;

/** Registers the standard Fumo block, item and client renderer for a set of seated variants. */
public final class Fumos {
    private Fumos() {}

    /**
     * Declares the variants on the calling mod’s deferred registers and adds the client renderer.
     * Use that mod’s event bus and registers in the same namespace, then attach the registers as usual.
     * Keep the entity and variant paths stable for existing worlds.
     */
    public static FumoPack register(IEventBus modBus, DeferredRegister<Block> blocks, DeferredRegister<Item> items,
                                    DeferredRegister<BlockEntityType<?>> entities, String entityPath,
                                    String... variantPaths) {
        var pack = new FumoPack(entityPath, variantPaths, (path, type) -> {
            var block = blocks.register(path, () -> new DishanhaiFumoBlock(BlockBehaviour.Properties.of(), type));
            var item = items.register(path, () -> new BlockItem(block.get(), new Item.Properties()));
            return new FumoPack.Variant(block, item);
        }, (path, validBlocks, factory) -> entities.register(path,
                () -> BlockEntityType.Builder.of(factory::apply, validBlocks.get()).build(null)));
        if (FMLEnvironment.dist == Dist.CLIENT) Client.register(modBus, pack);
        return pack;
    }

    private static final class Client {
        private static void register(IEventBus modBus, FumoPack pack) {
            modBus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
                    event.registerBlockEntityRenderer(pack.danceEntityType(), FumoRenderer::new));
        }
    }
}
