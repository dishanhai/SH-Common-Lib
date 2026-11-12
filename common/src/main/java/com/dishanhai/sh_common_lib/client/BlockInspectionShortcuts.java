package com.dishanhai.sh_common_lib.client;

import com.dishanhai.sh_common_lib.api.block.BlockInspection;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.slf4j.Logger;

public final class BlockInspectionShortcuts {
    public static final String COPY_DATA = "key.sh_common_lib.copy_block_data";
    public static final String COPY_STATE = "key.sh_common_lib.copy_block_state";

    private static final String MESSAGE = "message.sh_common_lib.block_inspection.";
    private static final Logger LOGGER = LogUtils.getLogger();

    private BlockInspectionShortcuts() {}

    public static void tick(KeyMapping copyData, KeyMapping copyState) {
        boolean dataRequested = consumeClicks(copyData);
        boolean stateRequested = consumeClicks(copyState);
        if (!dataRequested && !stateRequested) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.screen != null || client.getOverlay() != null) return;
        if (!(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            feedback(client, "no_target");
            return;
        }

        BlockPos pos = hit.getBlockPos();
        if (!client.level.hasChunkAt(pos)) {
            feedback(client, "no_target");
            return;
        }
        var state = client.level.getBlockState(pos);
        if (state.isAir()) {
            feedback(client, "no_target");
            return;
        }
        if (stateRequested) {
            client.keyboardHandler.setClipboard(BlockInspection.serializeState(state));
            feedback(client, "state_copied");
        }
        if (dataRequested) copyBlockEntityData(client, pos);
    }

    private static void copyBlockEntityData(Minecraft client, BlockPos pos) {
        BlockEntity entity = client.level.getBlockEntity(pos);
        CompoundTag data;
        try {
            data = entity == null ? null : BlockInspection.saveBlockEntityData(entity, client.level.registryAccess());
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to serialize block entity at {}", pos, exception);
            feedback(client, "data_failed");
            return;
        }
        if (data == null || data.isEmpty()) {
            feedback(client, "no_data");
            return;
        }
        client.keyboardHandler.setClipboard(data.toString());
        feedback(client, "data_copied");
    }

    private static boolean consumeClicks(KeyMapping mapping) {
        boolean clicked = false;
        while (mapping.consumeClick()) {
            clicked = true;
        }
        return clicked;
    }

    private static void feedback(Minecraft client, String key) {
        client.player.displayClientMessage(Component.translatable(MESSAGE + key), true);
    }
}
