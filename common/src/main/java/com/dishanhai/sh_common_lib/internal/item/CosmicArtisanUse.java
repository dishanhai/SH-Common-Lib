// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.item;

import com.dishanhai.sh_common_lib.internal.beacon.BeaconLanding;
import com.dishanhai.sh_common_lib.internal.beacon.CosmicBeaconBlock;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes.Mode;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.function.UnaryOperator;
import java.util.function.Consumer;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class CosmicArtisanUse {
    private static final int GROWTH_STEPS = 16;
    private static final int TELEPORT_RANGE = 24;
    private static final Map<ServerPlayer, Integer> NEXT_TELEPORT =
            Collections.synchronizedMap(new WeakHashMap<>());

    private CosmicArtisanUse() {}

    public static InteractionResult useOn(UseOnContext context, int flags, UnaryOperator<ItemStack> harvestTool,
                                          Consumer<ItemStack> routeDrop) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof CosmicBeaconBlock) return InteractionResult.PASS;
        var teleport = teleport(player, flags);
        if (teleport != InteractionResult.PASS) return teleport;
        var construction = CosmicArtisanConstruction.useOn(context, flags);
        if (construction != InteractionResult.PASS) return construction;
        boolean sneaking = player.isShiftKeyDown();
        if (sneaking && Mode.TIME_ACCELERATION.enabled(flags)) {
            BlockPos pos = context.getClickedPos();
            return CosmicArtisanAcceleration.startBlock(player, pos, context.getLevel().getBlockState(pos))
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (!sneaking) {
            BlockPos pos = context.getClickedPos();
            BlockState state = context.getLevel().getBlockState(pos);
            BlockState replanted = Mode.CROP_HARVEST.enabled(flags) ? replanted(state) : null;
            if (replanted != null) {
                if (context.getLevel() instanceof ServerLevel level && level.mayInteract(player, pos)) {
                    var lootTool = harvestTool.apply(context.getItemInHand().copy());
                    CosmicArtisanMining.takeHarvestMark(lootTool);
                    replant(level, pos, state, replanted, player, lootTool, routeDrop);
                }
                return InteractionResult.SUCCESS;
            }
            if (grow(context, state, flags)) return InteractionResult.SUCCESS;
        }
        return Artisan.useOn(context, Mode.HOE_PRIORITY.enabled(flags), !sneaking && Mode.FLINT_AND_STEEL.enabled(flags));
    }

    public static InteractionResult interact(ItemStack stack, Player player, LivingEntity target, InteractionHand hand, int flags) {
        var teleport = teleport(player, flags);
        if (teleport != InteractionResult.PASS) return teleport;
        if (player.isShiftKeyDown()) {
            if (Mode.ENTITY_ACCELERATION.enabled(flags)) {
                return CosmicArtisanAcceleration.startEntity(player, target) ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }
            if (!Mode.PROTECTION_LIST.enabled(flags) || target instanceof Player) return InteractionResult.PASS;
            if (player instanceof ServerPlayer serverPlayer) {
                String tag = protectionTag(player);
                var data = target.getPersistentData();
                boolean covered = !data.contains(tag);
                if (covered) data.putBoolean(tag, true);
                else data.remove(tag);
                serverPlayer.sendSystemMessage(Component.translatable("screen.sh_common_lib.cosmic_artisan."
                        + (covered ? "protected" : "unprotected"), target.getDisplayName()));
            }
            return InteractionResult.SUCCESS;
        }
        if (Mode.FLINT_AND_STEEL.enabled(flags) && target instanceof Creeper creeper && !creeper.isIgnited()) {
            if (player instanceof ServerPlayer) creeper.ignite();
            return InteractionResult.SUCCESS;
        }
        if (Mode.RIPEN.enabled(flags) && target instanceof AgeableMob ageable && ageable.isBaby()) {
            if (player instanceof ServerPlayer) ageable.setBaby(false);
            return InteractionResult.SUCCESS;
        }
        return Mode.SHEARS.enabled(flags) ? Items.SHEARS.interactLivingEntity(stack, player, target, hand) : InteractionResult.PASS;
    }

    public static boolean isProtected(Player owner, LivingEntity target) {
        return target.getPersistentData().contains(protectionTag(owner));
    }

    private static String protectionTag(Player owner) {
        return "sh_common_lib.cosmic_protected." + owner.getUUID();
    }

    public static InteractionResult useInAir(Player player, int flags) {
        var teleport = teleport(player, flags);
        return teleport == InteractionResult.PASS ? CosmicArtisanConstruction.useInAir(player, flags) : teleport;
    }

    public static InteractionResult teleport(Player player, int flags) {
        if (!Mode.TELEPORT.enabled(flags) || player.isShiftKeyDown() || player.isSpectator()) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        if (player.tickCount >= NEXT_TELEPORT.getOrDefault(serverPlayer, 0)) {
            var level = (ServerLevel) player.level();
            Vec3 eyes = player.getEyePosition();
            Vec3 aim = Vec3.directionFromRotation(player.getXRot(), player.getYRot());
            var hit = level.clip(new ClipContext(eyes, eyes.add(aim.scale(TELEPORT_RANGE)),
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            Vec3 end = hit.getLocation();
            boolean landed = false;
            if (hit.getType() == HitResult.Type.BLOCK) {
                var top = Vec3.atBottomCenterOf(hit.getBlockPos().above());
                if (BeaconLanding.safe(level, serverPlayer, top, true)) {
                    serverPlayer.teleportTo(top.x, top.y, top.z);
                    landed = true;
                }
            }
            Vec3 direction = end.subtract(eyes).normalize();
            double distance = eyes.distanceTo(end);
            for (double step = distance - 0.6; !landed && step >= 1; step -= 0.5) {
                Vec3 feet = eyes.add(direction.scale(step)).subtract(0, player.getEyeHeight(), 0);
                if (BeaconLanding.safe(level, serverPlayer, feet, false)) {
                    serverPlayer.teleportTo(feet.x, feet.y, feet.z);
                    landed = true;
                }
            }
            if (landed) {
                serverPlayer.resetFallDistance();
                NEXT_TELEPORT.put(serverPlayer, player.tickCount + 5);
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static BlockState replanted(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) return crop.getStateForAge(0);
        if (state.getBlock() instanceof NetherWartBlock && state.getValue(NetherWartBlock.AGE) == 3)
            return state.setValue(NetherWartBlock.AGE, 0);
        if (state.getBlock() instanceof CocoaBlock && state.getValue(CocoaBlock.AGE) == 2)
            return state.setValue(CocoaBlock.AGE, 0);
        return null;
    }

    private static void replant(ServerLevel level, BlockPos pos, BlockState ripe, BlockState young,
                                Player player, ItemStack tool, Consumer<ItemStack> routeDrop) {
        if (!level.setBlockAndUpdate(pos, young)) return;
        var drops = Block.getDrops(ripe, level, pos, level.getBlockEntity(pos), player, tool);
        boolean replantedSeed = false;
        for (ItemStack stack : drops) {
            if (!replantedSeed && stack.is(ripe.getBlock().asItem())) {
                stack.shrink(1);
                replantedSeed = true;
            }
            routeDrop.accept(stack);
            Block.popResource(level, pos, stack);
        }
    }

    private static boolean grow(UseOnContext context, BlockState state, int flags) {
        if ((!Mode.RIPEN.enabled(flags) || !(state.getBlock() instanceof BonemealableBlock))
                && (!Mode.FORCE_GROW.enabled(flags) || !state.isRandomlyTicking())) return false;
        if (context.getLevel() instanceof ServerLevel level) {
            BlockPos pos = context.getClickedPos();
            if (!level.mayInteract(context.getPlayer(), pos)) return false;
            for (int i = 0; i < GROWTH_STEPS; i++) {
                BlockState current = level.getBlockState(pos);
                if (current.getBlock() != state.getBlock() || replanted(current) != null) break;
                if (Mode.RIPEN.enabled(flags) && BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), level, pos)) continue;
                if (!Mode.FORCE_GROW.enabled(flags) || !current.isRandomlyTicking()) break;
                current.randomTick(level, pos, level.getRandom());
            }
        }
        return true;
    }
}
