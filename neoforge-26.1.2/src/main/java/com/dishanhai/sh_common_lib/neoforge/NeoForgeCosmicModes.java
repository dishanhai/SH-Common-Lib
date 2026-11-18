// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge;

import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanCombat;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanMining;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes.Mode;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes.BossPolicy;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.storage.TagValueOutput;

import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

public final class NeoForgeCosmicModes {
    private NeoForgeCosmicModes() {}

    public static int flags(ItemInstance stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return CosmicArtisanModes.readFlags(tag.getIntOr(CosmicArtisanModes.TAG, 0),
                tag.getBooleanOr(CosmicArtisanModes.BEACON_PROJECTION_TAG, false));
    }

    static boolean emptyTeleport(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.getBooleanOr(CosmicArtisanModes.EMPTY_TELEPORT_TAG, false);
    }

    static BossPolicy bossPolicy(ItemStack stack) {
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return data == null ? BossPolicy.NEUTRAL : BossPolicy.fromId(data.getIntOr(CosmicArtisanModes.BOSS_POLICY_TAG, 0));
    }

    static void toggle(ServerPlayer player, int bit) {
        if (bit == CosmicArtisanModes.EMPTY_TELEPORT_REQUEST) {
            var tool = player.getMainHandItem();
            if (player.isSpectator() || !CosmicArtisanModes.isActive(tool)) return;
            boolean updated = !emptyTeleport(tool);
            CustomData.update(DataComponents.CUSTOM_DATA, tool,
                    tag -> tag.putBoolean(CosmicArtisanModes.EMPTY_TELEPORT_TAG, updated));
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
            return;
        }
        if (bit == BossPolicy.FRIENDLY.request() || bit == BossPolicy.HOSTILE.request()) {
            var tool = player.getMainHandItem();
            if (!player.isSpectator() && CosmicArtisanModes.isActive(tool)) toggleBoss(player, tool, BossPolicy.fromId(-bit));
            return;
        }
        var mode = Mode.fromBit(bit);
        var stack = player.getMainHandItem();
        if (mode == null || mode == Mode.AUTO_CLICK || player.isSpectator() || !CosmicArtisanModes.isActive(stack)) return;
        if ((mode == Mode.AE_LINK || mode == Mode.AE_STORAGE_PRIORITY) && !NeoForgeCosmicStorage.available()) return;
        int updated = mode.toggle(flags(stack));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> CosmicArtisanModes.writeFlags(tag, updated));
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
    }

    private static void toggleBoss(ServerPlayer player, ItemStack stack, BossPolicy policy) {
        if (!Mode.HOSTILE_FRIENDLY.enabled(flags(stack))) return;
        var updated = bossPolicy(stack) == policy ? BossPolicy.NEUTRAL : policy;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(CosmicArtisanModes.BOSS_POLICY_TAG, updated.id()));
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
    }

    /** The game mode passes a copy of the harvest tool for loot and experience calculations. */
    public static ItemStack applyMiningModes(ItemStack tool, ServerLevel level) {
        if (!CosmicArtisanModes.isActive(tool)) return tool;
        int flags = flags(tool);
        if (Mode.AE_STORAGE_PRIORITY.enabled(flags) && NeoForgeCosmicStorage.available()) CosmicArtisanMining.markHarvest(tool);
        if (!Mode.SILK_TOUCH.enabled(flags) && !Mode.FORTUNE.enabled(flags)) return tool;
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        EnchantmentHelper.updateEnchantments(tool, enchantments -> {
            if (Mode.SILK_TOUCH.enabled(flags)) enchantments.upgrade(registry.getOrThrow(Enchantments.SILK_TOUCH), 1);
            if (Mode.FORTUNE.enabled(flags)) enchantments.upgrade(registry.getOrThrow(Enchantments.FORTUNE), CosmicArtisanModes.FORTUNE_LEVEL);
        });
        return tool;
    }

    static ItemStack capture(LivingEntity target) {
        if (target instanceof Player) return ItemStack.EMPTY;
        var type = target.getType();
        var egg = SpawnEggItem.byId(type).map(holder -> holder.value()).orElse(null);
        if (egg == null) return ItemStack.EMPTY;
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, target.level().registryAccess());
        target.saveWithoutId(output);
        var source = output.buildResult();
        var data = new net.minecraft.nbt.CompoundTag();
        for (String key : java.util.List.of("CustomName", "custom_name", "CustomNameVisible", "custom_name_visible",
                "Variant", "variant", "Color", "color", "Sheared", "sheared", "Age", "age", "Sitting", "sitting")) {
            var value = source.get(key);
            if (value != null) data.put(key, value.copy());
        }
        var result = egg.getDefaultInstance();
        result.set(DataComponents.ENTITY_DATA, TypedEntityData.of(type, data));
        return result;
    }

    static boolean forceKill(Player attacker, Entity target) {
        if (!(attacker instanceof ServerPlayer player) || player.isSpectator()
                || !(target instanceof LivingEntity living) || !living.isAlive() || living.isSpectator()
                || !living.isAttackable()) return false;
        if (living instanceof Player other && (!player.canHarmPlayer(other)
                || CosmicArtisanCombat.protects(other, NeoForgeCosmicModes::flags))) return false;

        var tool = player.getMainHandItem();
        if (!CosmicArtisanModes.isActive(tool) || !Mode.FORCE_KILL.enabled(flags(tool))) return false;

        living.setLastHurtByPlayer(player, 100);
        living.setHealth(0.0F);
        living.die(player.damageSources().playerAttack(player));
        return true;
    }
}
