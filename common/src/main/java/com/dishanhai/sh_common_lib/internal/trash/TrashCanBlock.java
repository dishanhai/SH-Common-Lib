// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.trash;

import com.dishanhai.sh_common_lib.api.ulk.trash.TrashCan.Extension;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class TrashCanBlock extends Block implements EntityBlock {
    private static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SOUTH_SHAPE = Shapes.or(
            box(2, 0, 5.75, 14, 12.25, 10.25),
            box(2.75, 0, 4, 13.25, 12.25, 12),
            box(4, 0, 2.75, 12, 12.25, 13.25),
            box(5.75, 0, 2, 10.25, 12.25, 14),
            box(3.4, 12.25, 4.5, 12.6, 15.9, 12.5),
            box(5.68, 0, 12.5, 10.32, 2.4, 15.15));
    private static final VoxelShape WEST_SHAPE = rotateShapeClockwise(SOUTH_SHAPE);
    private static final VoxelShape NORTH_SHAPE = rotateShapeClockwise(WEST_SHAPE);
    private static final VoxelShape EAST_SHAPE = rotateShapeClockwise(NORTH_SHAPE);

    private final Supplier<? extends BlockEntityType<?>> entityType;

    public TrashCanBlock(Properties properties, Supplier<? extends BlockEntityType<?>> entityType) {
        super(properties.noOcclusion().requiresCorrectToolForDrops());
        this.entityType = entityType;
        BlockState state = defaultBlockState().setValue(FACING, Direction.SOUTH);
        for (var extension : Extension.values()) {
            state = state.setValue(extension.property(), false);
        }
        registerDefaultState(state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case WEST -> WEST_SHAPE;
            case NORTH -> NORTH_SHAPE;
            case EAST -> EAST_SHAPE;
            default -> SOUTH_SHAPE;
        };
    }

    private static VoxelShape rotateShapeClockwise(VoxelShape shape) {
        VoxelShape rotated = Shapes.empty();
        for (var bounds : shape.toAabbs()) {
            rotated = Shapes.or(rotated, Shapes.box(1 - bounds.maxZ, bounds.minY, bounds.minX,
                    1 - bounds.minZ, bounds.maxY, bounds.maxX));
        }
        return rotated;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        for (var extension : Extension.values()) {
            builder.add(extension.property());
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return entityType.get().create(pos, state);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        var drops = super.getDrops(state, params);
        if (drops.isEmpty() || !(params.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                instanceof TrashCanBlockEntity entity)) return drops;
        var all = new ArrayList<>(drops);
        for (int index = 0; index < entity.storage.getContainerSize(); index++) {
            var stack = entity.storage.getItem(index);
            if (!stack.isEmpty()) all.add(stack.copy());
        }
        return all;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide() || type != entityType.get()
                || !(Extension.ENERGY.isInstalled(state) || Extension.STRESS.isInstalled(state)
                || Extension.SOURCE.isInstalled(state))) {
            return null;
        }
        return (world, pos, current, entity) -> ((TrashCanBlockEntity) entity).tick();
    }
}
