package com.dishanhai.sh_common_lib.internal.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;

public final class Artisan {
    public static final String ID = "artisan";
    public static final String COSMIC_ID = "cosmic_artisan";
    public static final String MINEABLE_PATH = "mineable/" + ID;
    public static final Component TOOLTIP = Component.translatable("tooltip.sh_common_lib.artisan")
            .withStyle(ChatFormatting.GRAY);
    private static final ThreadLocal<ItemStack> RIGHT_CLICK_STACK = new ThreadLocal<>();

    private Artisan() {}

    public static InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        return useOn(context, player != null && player.isSecondaryUseActive(), false);
    }

    static InteractionResult useOn(UseOnContext context, boolean hoeFirst, boolean ignite) {
        ItemStack previous = RIGHT_CLICK_STACK.get();
        RIGHT_CLICK_STACK.set(context.getItemInHand());
        try {
            if (ignite) {
                InteractionResult fire = Items.FLINT_AND_STEEL.useOn(context);
                if (fire != InteractionResult.PASS) return fire;
            }
            return useVanillaTool(context, hoeFirst);
        } finally {
            if (previous == null) RIGHT_CLICK_STACK.remove();
            else RIGHT_CLICK_STACK.set(previous);
        }
    }

    public static int durabilityCost(ItemStack stack, int amount, ArtisanSettings settings) {
        if (amount <= 0) return amount;
        if (settings.infiniteDurability()) return 0;
        return RIGHT_CLICK_STACK.get() == stack ? settings.useCost() : amount;
    }

    public static float miningSpeed(BlockState state, float original, ArtisanSettings settings) {
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) return original * (float) settings.pickaxe();
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) return original * (float) settings.axe();
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) return original * (float) settings.shovel();
        if (state.is(BlockTags.MINEABLE_WITH_HOE)) return original * (float) settings.hoe();
        return original;
    }

    private static InteractionResult useVanillaTool(UseOnContext context, boolean hoeFirst) {
        // The original context keeps the Artisan stack in the player's hand.
        Player player = context.getPlayer();
        if (player != null) {
            InteractionResult axe = Items.IRON_AXE.useOn(context);
            if (axe != InteractionResult.PASS) return axe;

            if (hoeFirst) {
                InteractionResult hoe = Items.IRON_HOE.useOn(context);
                if (hoe != InteractionResult.PASS) return hoe;
                return Items.IRON_SHOVEL.useOn(context);
            }
        }

        InteractionResult shovel = Items.IRON_SHOVEL.useOn(context);
        return shovel == InteractionResult.PASS ? Items.IRON_HOE.useOn(context) : shovel;
    }
}
