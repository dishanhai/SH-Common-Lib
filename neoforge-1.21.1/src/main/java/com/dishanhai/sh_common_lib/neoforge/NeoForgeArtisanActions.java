package com.dishanhai.sh_common_lib.neoforge;

import com.dishanhai.sh_common_lib.client.item.ArtisanAppearance;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbilities;

import java.util.function.Predicate;

final class NeoForgeArtisanActions {
    private NeoForgeArtisanActions() {}

    static boolean canUse(ArtisanAppearance.Mode mode, BlockState block, UseOnContext context,
                          Predicate<ItemStack> isBlockingItem) {
        return switch (mode) {
            case AXE -> !hasBlockingIntent(context, isBlockingItem)
                    && (block.getToolModifiedState(context, ItemAbilities.AXE_STRIP, true) != null
                        || block.getToolModifiedState(context, ItemAbilities.AXE_SCRAPE, true) != null
                        || block.getToolModifiedState(context, ItemAbilities.AXE_WAX_OFF, true) != null);
            case SHOVEL -> context.getClickedFace() != Direction.DOWN
                    && ((context.getLevel().getBlockState(context.getClickedPos().above()).isAir()
                        && block.getToolModifiedState(context, ItemAbilities.SHOVEL_FLATTEN, true) != null)
                        || block.getToolModifiedState(context, ItemAbilities.SHOVEL_DOUSE, true) != null);
            case HOE -> block.getToolModifiedState(context, ItemAbilities.HOE_TILL, true) != null;
            default -> false;
        };
    }

    private static boolean hasBlockingIntent(UseOnContext context, Predicate<ItemStack> isBlockingItem) {
        Player player = context.getPlayer();
        return player != null && context.getHand() == InteractionHand.MAIN_HAND && !context.isSecondaryUseActive()
                && isBlockingItem.test(player.getOffhandItem());
    }
}
