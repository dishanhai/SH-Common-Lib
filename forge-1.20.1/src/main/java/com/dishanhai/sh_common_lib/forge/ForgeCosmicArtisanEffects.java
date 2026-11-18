package com.dishanhai.sh_common_lib.forge;

import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanEffects;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanCombat;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanMining;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanAcceleration;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import com.dishanhai.sh_common_lib.internal.item.ArtisanConfig;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanRelations;
import com.dishanhai.sh_common_lib.internal.beacon.CosmicBeaconBlock;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanUse;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes.Mode;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;

final class ForgeCosmicArtisanEffects {
    private static final String FLIGHT_GRANT = "sh_common_lib.cosmic_artisan_flight";

    private ForgeCosmicArtisanEffects() {}

    static void register() {
        CosmicArtisanAcceleration.install(new ForgeAccelerationPlatform());
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) CosmicArtisanAcceleration.tickServer(event.getServer());
        });
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStoppingEvent event) ->
                CosmicArtisanAcceleration.shutdown(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.EntityJoinLevelEvent event) -> {
            if (event.getLevel() instanceof ServerLevel level)
                CosmicArtisanAcceleration.entityJoined(level, event.getEntity());
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (PlayerInteractEvent.EntityInteract event) -> {
            if (accelerateNonLiving(event, event.getTarget())) {
                event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (PlayerInteractEvent.EntityInteractSpecific event) -> {
            if (accelerateNonLiving(event, event.getTarget())) {
                event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
        });
        MinecraftForge.EVENT_BUS.addListener(ForgeCosmicArtisanEffects::tick);
        MinecraftForge.EVENT_BUS.addListener(ForgeCosmicArtisanEffects::protect);
        MinecraftForge.EVENT_BUS.addListener((BlockEvent.BreakEvent event) -> {
            var tool = event.getPlayer().getMainHandItem();
            if (CosmicArtisanModes.isActive(tool) && Mode.SILK_TOUCH.enabled(ForgeCosmicModes.flags(tool))) {
                event.setExpToDrop(0);
            }
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, ForgeCosmicArtisanEffects::attack);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (BlockEvent.BreakEvent event) -> {
            if (event.getPlayer() instanceof ServerPlayer player) {
                CosmicArtisanMining.afterBreak(player, event.getPos(), event.getState(), ForgeCosmicModes::flags);
            }
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, ForgeCosmicArtisanEffects::forceMine);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (PlayerInteractEvent.RightClickBlock event) -> {
            if (event.getEntity() instanceof ServerPlayer player && event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                    && CosmicArtisanModes.isActive(event.getItemStack()) && CosmicArtisanAcceleration.handleBlock(player, event.getPos())) {
                event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                event.setCanceled(true);
                return;
            }
            if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level
                    && player.isShiftKeyDown() && ForgeCosmicStorage.bind(player, level, event.getPos(), event.getItemStack())) {
                event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                event.setCanceled(true);
            } else if (event.getEntity() instanceof ServerPlayer player && CosmicArtisanModes.isActive(event.getItemStack())
                    && !(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof CosmicBeaconBlock)
                    && CosmicArtisanUse.teleport(player,
                            ForgeCosmicModes.flags(event.getItemStack())) != net.minecraft.world.InteractionResult.PASS) {
                event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, ForgeCosmicArtisanEffects::drops);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, ForgeCosmicArtisanEffects::experience);
        MinecraftForge.EVENT_BUS.addListener((LivingChangeTargetEvent event) -> {
            if (event.getNewTarget() instanceof ServerPlayer player && (isStealthed(player)
                    || CosmicArtisanRelations.pacifies(player, event.getEntity(), ForgeCosmicModes::flags, ForgeCosmicModes::bossPolicy))) {
                event.setNewTarget(null);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) revokeFlight(player);
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.Clone event) -> {
            if (event.getOriginal() instanceof ServerPlayer player) revokeFlight(player);
            if (event.getEntity() instanceof ServerPlayer player) revokeFlight(player);
        });
    }

    private static boolean accelerateNonLiving(PlayerInteractEvent event, net.minecraft.world.entity.Entity target) {
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player)) return false;
        var tool = player.getMainHandItem();
        if (CosmicArtisanModes.isActive(tool) && CosmicArtisanAcceleration.isEditing(player)) return true;
        if (target instanceof LivingEntity || !player.isShiftKeyDown()) return false;
        return CosmicArtisanModes.isActive(tool) && Mode.TIME_ACCELERATION.enabled(ForgeCosmicModes.flags(tool))
                && CosmicArtisanAcceleration.startEntity(player, target);
    }

    private static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        ForgeArtisanCombat.refresh(player);
        CosmicArtisanAcceleration.tick(player, ForgeCosmicArtisanEffects::accelerateTimers);
        CosmicArtisanMining.tick(player, ForgeCosmicModes::flags);
        CosmicArtisanCombat.collectNearby(player);
        CosmicArtisanRelations.releaseTargets(player, ForgeCosmicModes::flags, ForgeCosmicModes::bossPolicy);
        if (player.tickCount % 20 == 0 && isStealthed(player)) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 35, 0, true, false, false));
        }
        var tool = player.getMainHandItem();
        if (player.tickCount % 20 == 0 && CosmicArtisanModes.isActive(tool)) {
            CosmicArtisanCombat.attackNearby(player, player.position(), player, ForgeCosmicModes.flags(tool),
                    Mode.KILL_AURA, target -> hit(player, target));
        }
        if (!CosmicArtisanEffects.carriedBy(player, ForgeArtisan.cosmicItem())) {
            revokeFlight(player);
            return;
        }
        var settings = ArtisanConfig.profiles().passives();
        ForgeCosmicHorizon.grant(player);
        if (CosmicArtisanEffects.heldBy(player, ForgeArtisan.cosmicItem())) CosmicArtisanEffects.replenishFood(player, settings);
        if (!settings.flight()) {
            revokeFlight(player);
            return;
        }
        if (!player.getAbilities().mayfly) {
            // Persist ownership in case the player data is saved before normal logout cleanup.
            player.getPersistentData().putBoolean(FLIGHT_GRANT, true);
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
        }
    }

    private static void revokeFlight(ServerPlayer player) {
        var data = player.getPersistentData();
        if (!data.getBoolean(FLIGHT_GRANT)) return;
        data.remove(FLIGHT_GRANT);
        if (player.isCreative() || player.isSpectator()) return;
        var abilities = player.getAbilities();
        if (abilities.mayfly || abilities.flying) {
            abilities.mayfly = false;
            abilities.flying = false;
            player.onUpdateAbilities();
        }
    }

    private static void forceMine(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START
                || !(event.getEntity() instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()) return;
        var stack = player.getMainHandItem();
        var pos = event.getPos();
        var level = (ServerLevel) player.level();
        if (!CosmicArtisanModes.isActive(stack) || !Mode.FORCE_MINING.enabled(ForgeCosmicModes.flags(stack))
                || player.distanceToSqr(Vec3.atCenterOf(pos)) > 36 || !level.hasChunkAt(pos)
                || !level.getWorldBorder().isWithinBounds(pos) || !level.mayInteract(player, pos)) return;
        if (player.gameMode.destroyBlock(pos)) event.setCanceled(true);
    }

    private static void attack(AttackEntityEvent event) {
        var attacker = event.getEntity();
        var stack = attacker.getMainHandItem();
        if (!(stack.getItem() instanceof ForgeArtisan item)) return;
        if (!item.settings().enabled()) {
            event.setCanceled(true);
            return;
        }
        if (!(attacker instanceof ServerPlayer player) || !CosmicArtisanModes.isCosmic(stack)) return;
        if (event.getTarget() instanceof LivingEntity living && !CosmicArtisanCombat.permitsAttack(player, living)) {
            event.setCanceled(true);
            return;
        }
        if (ForgeCosmicModes.forceKill(player, event.getTarget())) event.setCanceled(true);
        CosmicArtisanCombat.attackNearby(player, event.getTarget().position(), event.getTarget(), ForgeCosmicModes.flags(stack),
                Mode.AREA_DAMAGE, target -> hit(player, target));
    }

    private static void hit(ServerPlayer player, LivingEntity target) {
        if (!ForgeCosmicModes.forceKill(player, target)) {
            var source = player.damageSources().playerAttack(player);
            target.hurt(source, CosmicArtisanModes.ATTACK_DAMAGE);
        }
    }

    private static void drops(LivingDropsEvent event) {
        if (event.getSource().getDirectEntity() instanceof ServerPlayer player
                && CosmicArtisanModes.isActive(player.getMainHandItem())) {
            int flags = ForgeCosmicModes.flags(player.getMainHandItem());
            var target = event.getEntity();
            if (Mode.CAPTURE.enabled(flags)) {
                var egg = ForgeCosmicModes.capture(target);
                if (!egg.isEmpty()) event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                        target.level(), target.getX(), target.getY(), target.getZ(), egg));
            }
            CosmicArtisanCombat.modifyDrops(target, player, flags, event.getDrops());
            if (Mode.AE_STORAGE_PRIORITY.enabled(flags) && ForgeCosmicStorage.available()) {
                var drops = event.getDrops().iterator();
                while (drops.hasNext()) {
                    var item = drops.next().getItem();
                    ForgeCosmicStorage.insert(player, player.getMainHandItem(), item);
                    if (item.isEmpty()) drops.remove();
                }
            }
        }
    }

    private static boolean isStealthed(ServerPlayer player) {
        var stack = player.getMainHandItem();
        return CosmicArtisanModes.isActive(stack) && Mode.ADVANCED_STEALTH.enabled(ForgeCosmicModes.flags(stack));
    }

    private static void accelerateTimers(AgeableMob target, int ticks) {
        if (target instanceof Sheep sheep && sheep.isSheared()) sheep.setSheared(false);
        if (target instanceof Chicken chicken && chicken.eggTime > 0) chicken.eggTime = Math.max(1, chicken.eggTime - ticks);
        if (target instanceof Bee bee && bee.hasNectar()) bee.resetTicksWithoutNectarSinceExitingHive();
    }

    private static void experience(LivingExperienceDropEvent event) {
        if (event.getAttackingPlayer() instanceof ServerPlayer player
                && CosmicArtisanModes.isActive(player.getMainHandItem())
                && Mode.MAGNET.enabled(ForgeCosmicModes.flags(player.getMainHandItem()))) {
            player.giveExperiencePoints(event.getDroppedExperience());
            event.setDroppedExperience(0);
        }
    }

    private static void protect(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer attacker)
            CosmicArtisanRelations.provoke(event.getEntity(), attacker);
        if (event.getEntity() instanceof ServerPlayer player
                && (CosmicArtisanCombat.protects(player, ForgeCosmicModes::flags)
                    || event.getSource().getEntity() instanceof LivingEntity mob
                        && CosmicArtisanRelations.pacifies(player, mob, ForgeCosmicModes::flags, ForgeCosmicModes::bossPolicy))) {
            event.setCanceled(true);
        }
    }
}
