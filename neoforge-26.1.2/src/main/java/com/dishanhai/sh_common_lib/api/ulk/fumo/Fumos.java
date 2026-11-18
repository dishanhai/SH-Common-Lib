// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.fumo;

import com.dishanhai.sh_common_lib.internal.block.DishanhaiFumoBlock;
import com.dishanhai.sh_common_lib.internal.block.FumoRenderer;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registers the standard Fumo block, item and client renderer for a set of seated variants. */
public final class Fumos {
    private Fumos() {}

    /**
     * Declares the variants on the calling mod’s deferred registers and adds the client renderer.
     * Use that mod’s event bus and registers in the same namespace, then attach the registers as usual.
     * Keep the entity and variant paths stable for existing worlds.
     */
    public static FumoPack register(IEventBus modBus, DeferredRegister.Blocks blocks, DeferredRegister.Items items,
                                    DeferredRegister<BlockEntityType<?>> entities, String entityPath,
                                    String... variantPaths) {
        var pack = new FumoPack(entityPath, variantPaths, (path, type) -> {
            var block = blocks.registerBlock(path, properties -> new DishanhaiFumoBlock(properties, type));
            var item = items.registerSimpleBlockItem(block);
            return new FumoPack.Variant(block, item);
        }, (path, validBlocks, factory) -> entities.register(path,
                () -> new BlockEntityType<>(factory::apply, validBlocks.get())));
        if (FMLEnvironment.getDist().isClient()) Client.register(modBus, pack);
        return pack;
    }

    private static final class Client {
        private static void register(IEventBus modBus, FumoPack pack) {
            modBus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
                    event.registerBlockEntityRenderer(pack.danceEntityType(), FumoRenderer::new));
        }
    }
}
