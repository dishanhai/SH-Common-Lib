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
    private static final VoxelShape NORTH_SOUTH_SHAPE = Shapes.or(
            box(4.75, 0, 5, 11.25, 8, 11),
            box(4.35, 8, 4.6, 11.65, 8.7, 11.4));
    private static final VoxelShape EAST_WEST_SHAPE = Shapes.or(
            box(5, 0, 4.75, 11, 8, 11.25),
            box(4.6, 8, 4.35, 11.4, 8.7, 11.65));

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
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? EAST_WEST_SHAPE : NORTH_SOUTH_SHAPE;
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
