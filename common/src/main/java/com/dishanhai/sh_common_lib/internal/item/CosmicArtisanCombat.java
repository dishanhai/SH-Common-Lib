// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.item;

import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes.Mode;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

public final class CosmicArtisanCombat {
    private static final int MAX_TARGETS = 64;
    private static final Map<ServerPlayer, Sweep> SWEEPS = Collections.synchronizedMap(new WeakHashMap<>());

    private CosmicArtisanCombat() {}

    public static boolean permitsAttack(Player attacker, LivingEntity target) {
        return !CosmicArtisanUse.isProtected(attacker, target)
                && (!(target instanceof Player player) || attacker.canHarmPlayer(player));
    }

    public static void attackNearby(ServerPlayer player, Vec3 center, Entity excluded,
                                    int flags, Mode mode, Consumer<LivingEntity> attack) {
        if (!mode.enabled(flags) || Mode.PROTECTION_LIST.enabled(flags) || player.isSpectator()) return;
        var level = (ServerLevel) player.level();
        int hits = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, nearby(center))) {
            if (target == excluded || target instanceof Player || !target.isAlive()
                    || !target.isAttackable() || target.isAlliedTo(player)
                    || target instanceof TamableAnimal pet && pet.isTame()
                    || !permitsAttack(player, target)) continue;
            attack.accept(target);
            if (++hits == MAX_TARGETS) break;
        }
    }

    public static void modifyDrops(LivingEntity target, ServerPlayer player, int flags, Collection<ItemEntity> drops) {
        var level = (ServerLevel) player.level();
        Item head = Mode.BEHEADING.enabled(flags) ? headFor(target.getType()) : null;
        if (head != null) {
            drops.add(new ItemEntity(level, target.getX(), target.getY(), target.getZ(), new ItemStack(head)));
        }
        if (Mode.MAGNET.enabled(flags)) {
            for (ItemEntity drop : drops) drop.setPos(player.getX(), player.getY(), player.getZ());
            AABB area = nearby(target.position());
            SWEEPS.merge(player, new Sweep(level, area), (previous, next) -> previous.level() == next.level()
                    && previous.area().getCenter().distanceToSqr(next.area().getCenter()) <= 144
                    ? new Sweep(level, previous.area().minmax(area)) : next);
        }
    }

    public static void collectNearby(ServerPlayer player) {
        var sweep = SWEEPS.remove(player);
        if (sweep == null || !player.isAlive() || player.level() != sweep.level()) return;
        for (ItemEntity item : sweep.level().getEntitiesOfClass(ItemEntity.class, sweep.area())) {
            if (item.isAlive() && !item.hasPickUpDelay()) item.playerTouch(player);
        }
    }

    public static boolean protects(Player player, ToIntFunction<ItemStack> flags) {
        if (!ArtisanConfig.profiles().passives().damageImmunity()) return false;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (CosmicArtisanModes.isActive(stack) && Mode.PLAYER_PROTECTION.enabled(flags.applyAsInt(stack))) {
                return true;
            }
        }
        return false;
    }

    private static Item headFor(EntityType<?> type) {
        if (type == EntityType.SKELETON) return Items.SKELETON_SKULL;
        if (type == EntityType.WITHER_SKELETON) return Items.WITHER_SKELETON_SKULL;
        if (type == EntityType.ZOMBIE) return Items.ZOMBIE_HEAD;
        if (type == EntityType.CREEPER) return Items.CREEPER_HEAD;
        if (type == EntityType.PIGLIN) return Items.PIGLIN_HEAD;
        return null;
    }

    private static AABB nearby(Vec3 center) {
        return AABB.ofSize(center, 11, 7, 11);
    }

    private record Sweep(ServerLevel level, AABB area) {}
}
