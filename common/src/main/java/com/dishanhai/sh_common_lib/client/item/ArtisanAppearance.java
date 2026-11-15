package com.dishanhai.sh_common_lib.client.item;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class ArtisanAppearance {
    public static final String PROPERTY = "artisan_mode";

    private ArtisanAppearance() {}

    public static float modelValue(ItemStack stack, ClientLevel level, LivingEntity holder, ActionProbe probe) {
        if (!ArtisanAppearancePreferences.targetColors()) return Mode.DEFAULT.value;
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || holder != player || level == null || level != client.level
                || client.getCameraEntity() != player || player.isSpectator()
                || !(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return Mode.DEFAULT.value;
        }

        InteractionHand hand;
        if (stack == player.getMainHandItem()) {
            hand = InteractionHand.MAIN_HAND;
        } else if (stack == player.getOffhandItem()) {
            hand = InteractionHand.OFF_HAND;
        } else {
            return Mode.DEFAULT.value;
        }

        BlockState block = level.getBlockState(hit.getBlockPos());
        if (block.isAir()) return Mode.DEFAULT.value;

        UseOnContext context = new UseOnContext(player, hand, hit);
        return resolve(block, context, probe).value;
    }

    private static Mode resolve(BlockState block, UseOnContext context, ActionProbe probe) {
        // Match Artisan.useOn: axe first, then prefer the hoe while sneaking.
        if (probe.canUse(Mode.AXE, block, context)) return Mode.AXE;
        boolean sneaking = context.isSecondaryUseActive();
        if (sneaking && probe.canUse(Mode.HOE, block, context)) return Mode.HOE;
        if (probe.canUse(Mode.SHOVEL, block, context)) return Mode.SHOVEL;
        if (!sneaking && probe.canUse(Mode.HOE, block, context)) return Mode.HOE;

        if (block.is(BlockTags.MINEABLE_WITH_PICKAXE)) return Mode.PICKAXE;
        if (block.is(BlockTags.MINEABLE_WITH_AXE)) return Mode.AXE;
        if (block.is(BlockTags.MINEABLE_WITH_SHOVEL)) return Mode.SHOVEL;
        if (block.is(BlockTags.MINEABLE_WITH_HOE)) return Mode.HOE;
        return Mode.DEFAULT;
    }

    @FunctionalInterface
    public interface ActionProbe {
        boolean canUse(Mode mode, BlockState block, UseOnContext context);
    }

    public enum Mode {
        DEFAULT(0.0F),
        PICKAXE(0.25F),
        AXE(0.5F),
        SHOVEL(0.75F),
        HOE(1.0F);

        private final float value;

        Mode(float value) {
            this.value = value;
        }
    }
}
