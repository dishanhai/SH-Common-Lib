// Author: dishanhai
package com.dishanhai.sh_common_lib.api.ulk.fumo;

import com.dishanhai.sh_common_lib.internal.block.DishanhaiFumoBlock.DanceEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** The deferred block and item handles for Fumo variants sharing a saved block entity type. */
public final class FumoPack {
    private static final Pattern PATH = Pattern.compile("[a-z0-9_./-]+");

    private final Map<String, Variant> variants;
    private final Supplier<BlockEntityType<DanceEntity>> entityType;

    FumoPack(String entityPath, String[] variantPaths, VariantRegistrar registerVariant, EntityRegistrar registerEntity) {
        checkPath(entityPath);
        if (variantPaths.length == 0) throw new IllegalArgumentException("At least one Fumo variant is required");
        Map<String, Variant> declared = new LinkedHashMap<>();
        for (String variantPath : variantPaths) {
            checkPath(variantPath);
            if (declared.containsKey(variantPath)) throw new IllegalArgumentException("Duplicate Fumo path: " + variantPath);
            declared.put(variantPath, null);
        }
        declared.replaceAll((path, ignored) -> registerVariant.register(path, this::danceEntityType));
        variants = Map.copyOf(declared);
        entityType = registerEntity.register(entityPath,
                () -> declared.values().stream().map(variant -> variant.block().get()).toArray(Block[]::new),
                this::newDanceEntity);
    }

    /** Gets handles by the path supplied during registration. Resolve the suppliers after registry initialization. */
    public Variant variant(String path) {
        Variant variant = variants.get(path);
        if (variant == null) throw new IllegalArgumentException("Unknown Fumo path: " + path);
        return variant;
    }

    /** Resolves the block entity type after registry initialization, for example for a legacy saved ID mapping. */
    public BlockEntityType<?> blockEntityType() {
        return danceEntityType();
    }

    BlockEntityType<DanceEntity> danceEntityType() {
        return entityType.get();
    }

    Collection<Variant> variants() {
        return variants.values();
    }

    private DanceEntity newDanceEntity(BlockPos pos, BlockState state) {
        return new DanceEntity(danceEntityType(), pos, state);
    }

    private static void checkPath(String path) {
        if (path == null || !PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("Invalid Fumo path: " + path);
        }
    }

    public record Variant(Supplier<? extends Block> block, Supplier<? extends BlockItem> item) {}

    @FunctionalInterface
    interface VariantRegistrar {
        Variant register(String path, Supplier<BlockEntityType<DanceEntity>> entityType);
    }

    @FunctionalInterface
    interface EntityRegistrar {
        Supplier<BlockEntityType<DanceEntity>> register(String path, Supplier<Block[]> blocks,
                                                        BiFunction<BlockPos, BlockState, DanceEntity> factory);
    }
}
