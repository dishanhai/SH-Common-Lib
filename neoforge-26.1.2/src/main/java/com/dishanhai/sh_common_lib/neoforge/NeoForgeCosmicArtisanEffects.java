package com.dishanhai.sh_common_lib.neoforge;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanEffects;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanCombat;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanMining;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanAcceleration;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanRelations;
import com.dishanhai.sh_common_lib.internal.beacon.CosmicBeaconBlock;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanUse;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes.Mode;
import com.dishanhai.sh_common_lib.internal.item.ArtisanConfig;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

final class NeoForgeCosmicArtisanEffects {
    private static final AttributeModifier FLIGHT = new AttributeModifier(
            Identifier.fromNamespaceAndPath(ShCommonLib.MOD_ID, "cosmic_artisan_flight"),
            1.0, AttributeModifier.Operation.ADD_VALUE);

    private NeoForgeCosmicArtisanEffects() {}

    static void register() {
        CosmicArtisanModes.installEmptyTeleportReader(NeoForgeCosmicModes::emptyTeleport);
        var acceleration = new NeoForgeAccelerationPlatform();
        CosmicArtisanAcceleration.install(acceleration);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) -> {
            CosmicArtisanAcceleration.tickServer(event.getServer());
            acceleration.flushClocks();
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent event) ->
                CosmicArtisanAcceleration.shutdown(event.getServer()));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) -> {
            if (event.getLevel() instanceof ServerLevel level)
                CosmicArtisanAcceleration.entityJoined(level, event.getEntity());
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (PlayerInteractEvent.EntityInteract event) -> {
            if (accelerateNonLiving(event, event.getTarget())) {
                event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (PlayerInteractEvent.EntityInteractSpecific event) -> {
            if (accelerateNonLiving(event, event.getTarget())) {
                event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener(NeoForgeCosmicArtisanEffects::tick);
        NeoForge.EVENT_BUS.addListener(NeoForgeCosmicArtisanEffects::protect);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, NeoForgeCosmicArtisanEffects::attack);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (BreakBlockEvent event) -> {
            if (event.getPlayer() instanceof ServerPlayer player) {
                CosmicArtisanMining.afterBreak(player, event.getPos(), event.getState(), NeoForgeCosmicModes::flags);
            }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, NeoForgeCosmicArtisanEffects::forceMine);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (PlayerInteractEvent.RightClickBlock event) -> {
            if (event.getEntity() instanceof ServerPlayer player && event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                    && CosmicArtisanModes.isActive(event.getItemStack()) && CosmicArtisanAcceleration.handleBlock(player, event.getPos())) {
                event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                event.setCanceled(true);
                return;
            }
            if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level
                    && player.isShiftKeyDown() && NeoForgeCosmicStorage.bind(player, level, event.getPos(), event.getItemStack())) {
                event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                event.setCanceled(true);
            } else if (event.getEntity() instanceof ServerPlayer player && CosmicArtisanModes.isActive(event.getItemStack())
                    && !(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof CosmicBeaconBlock)
                    && CosmicArtisanUse.teleport(player,
                            NeoForgeCosmicModes.flags(event.getItemStack())) != net.minecraft.world.InteractionResult.PASS) {
                event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, NeoForgeCosmicArtisanEffects::drops);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, NeoForgeCosmicArtisanEffects::experience);
        NeoForge.EVENT_BUS.addListener((LivingChangeTargetEvent event) -> {
            if (event.getNewAboutToBeSetTarget() instanceof ServerPlayer player && (isStealthed(player)
                    || CosmicArtisanRelations.pacifies(player, event.getEntity(), NeoForgeCosmicModes::flags, NeoForgeCosmicModes::bossPolicy))) {
                event.setNewAboutToBeSetTarget(null);
            }
        });
    }

    private static boolean accelerateNonLiving(PlayerInteractEvent event, net.minecraft.world.entity.Entity target) {
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player)) return false;
        var tool = player.getMainHandItem();
        if (CosmicArtisanModes.isActive(tool) && CosmicArtisanAcceleration.isEditing(player)) return true;
        if (target instanceof LivingEntity || !player.isShiftKeyDown()) return false;
        return CosmicArtisanModes.isActive(tool) && Mode.TIME_ACCELERATION.enabled(NeoForgeCosmicModes.flags(tool))
                && CosmicArtisanAcceleration.startEntity(player, target);
    }

    private static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        NeoForgeArtisanCombat.refresh(player);
        CosmicArtisanAcceleration.tick(player, NeoForgeCosmicArtisanEffects::accelerateTimers);
        CosmicArtisanMining.tick(player, NeoForgeCosmicModes::flags);
        CosmicArtisanCombat.collectNearby(player);
        CosmicArtisanRelations.releaseTargets(player, NeoForgeCosmicModes::flags, NeoForgeCosmicModes::bossPolicy);
        if (player.tickCount % 20 == 0 && isStealthed(player)) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 35, 0, true, false, false));
        }
        var tool = player.getMainHandItem();
        if (player.tickCount % 20 == 0 && CosmicArtisanModes.isActive(tool)) {
            CosmicArtisanCombat.attackNearby(player, player.position(), player, NeoForgeCosmicModes.flags(tool),
                    Mode.KILL_AURA, target -> hit(player, target));
        }
        boolean carried = CosmicArtisanEffects.carriedBy(player, NeoForgeArtisan.cosmicItem());
        var settings = carried ? ArtisanConfig.profiles().passives() : null;
        if (carried) NeoForgeCosmicHorizon.grant(player);
        if (settings != null && CosmicArtisanEffects.heldBy(player, NeoForgeArtisan.cosmicItem())) CosmicArtisanEffects.replenishFood(player, settings);

        var flight = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (flight == null) return;
        if (settings != null && settings.flight()) {
            if (!flight.hasModifier(FLIGHT.id())) flight.addTransientModifier(FLIGHT);
        } else if (flight.removeModifier(FLIGHT.id()) && !player.mayFly() && player.getAbilities().flying) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
    }

    private static void forceMine(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START
                || !(event.getEntity() instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()) return;
        var stack = player.getMainHandItem();
        var pos = event.getPos();
        var level = (ServerLevel) player.level();
        if (!CosmicArtisanModes.isActive(stack) || !Mode.FORCE_MINING.enabled(NeoForgeCosmicModes.flags(stack))
                || player.distanceToSqr(Vec3.atCenterOf(pos)) > 36 || !level.hasChunkAt(pos)
                || !level.getWorldBorder().isWithinBounds(pos) || !level.mayInteract(player, pos)) return;
        if (player.gameMode.destroyBlock(pos)) event.setCanceled(true);
    }

    private static void attack(AttackEntityEvent event) {
        var attacker = event.getEntity();
        var stack = attacker.getMainHandItem();
        if (!(stack.getItem() instanceof NeoForgeArtisan item)) return;
        if (!item.settings().enabled()) {
            event.setCanceled(true);
            return;
        }
        if (!(attacker instanceof ServerPlayer player) || !CosmicArtisanModes.isCosmic(stack)) return;
        if (event.getTarget() instanceof LivingEntity living && !CosmicArtisanCombat.permitsAttack(player, living)) {
            event.setCanceled(true);
            return;
        }
        if (NeoForgeCosmicModes.forceKill(player, event.getTarget())) event.setCanceled(true);
        CosmicArtisanCombat.attackNearby(player, event.getTarget().position(), event.getTarget(), NeoForgeCosmicModes.flags(stack),
                Mode.AREA_DAMAGE, target -> hit(player, target));
    }

    private static void hit(ServerPlayer player, LivingEntity target) {
        if (!NeoForgeCosmicModes.forceKill(player, target)) {
            var source = player.damageSources().playerAttack(player);
            target.hurtServer((ServerLevel) player.level(), source, CosmicArtisanModes.ATTACK_DAMAGE);
        }
    }

    private static void drops(LivingDropsEvent event) {
        if (event.getSource().getDirectEntity() instanceof ServerPlayer player
                && CosmicArtisanModes.isActive(player.getMainHandItem())) {
            int flags = NeoForgeCosmicModes.flags(player.getMainHandItem());
            var target = event.getEntity();
            if (Mode.CAPTURE.enabled(flags)) {
                var egg = NeoForgeCosmicModes.capture(target);
                if (!egg.isEmpty()) event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                        target.level(), target.getX(), target.getY(), target.getZ(), egg));
            }
            CosmicArtisanCombat.modifyDrops(target, player, flags, event.getDrops());
            if (Mode.AE_STORAGE_PRIORITY.enabled(flags) && NeoForgeCosmicStorage.available()) {
                var drops = event.getDrops().iterator();
                while (drops.hasNext()) {
                    var item = drops.next().getItem();
                    NeoForgeCosmicStorage.insert(player, player.getMainHandItem(), item);
                    if (item.isEmpty()) drops.remove();
                }
            }
        }
    }

    private static boolean isStealthed(ServerPlayer player) {
        var stack = player.getMainHandItem();
        return CosmicArtisanModes.isActive(stack) && Mode.ADVANCED_STEALTH.enabled(NeoForgeCosmicModes.flags(stack));
    }

    private static void accelerateTimers(AgeableMob target, int ticks) {
        if (target instanceof Sheep sheep && sheep.isSheared()) sheep.setSheared(false);
        if (target instanceof Chicken chicken && chicken.eggTime > 0) chicken.eggTime = Math.max(1, chicken.eggTime - ticks);
        if (target instanceof Bee bee && bee.hasNectar()) bee.resetTicksWithoutNectarSinceExitingHive();
    }

    private static void experience(LivingExperienceDropEvent event) {
        if (event.getAttackingPlayer() instanceof ServerPlayer player
                && CosmicArtisanModes.isActive(player.getMainHandItem())
                && Mode.MAGNET.enabled(NeoForgeCosmicModes.flags(player.getMainHandItem()))) {
            player.giveExperiencePoints(event.getDroppedExperience());
            event.setDroppedExperience(0);
        }
    }

    private static void protect(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer attacker)
            CosmicArtisanRelations.provoke(event.getEntity(), attacker);
        if (event.getEntity() instanceof ServerPlayer player
                && (CosmicArtisanCombat.protects(player, NeoForgeCosmicModes::flags)
                    || event.getSource().getEntity() instanceof LivingEntity mob
                        && CosmicArtisanRelations.pacifies(player, mob, NeoForgeCosmicModes::flags, NeoForgeCosmicModes::bossPolicy))) {
            event.setCanceled(true);
        }
    }
}
