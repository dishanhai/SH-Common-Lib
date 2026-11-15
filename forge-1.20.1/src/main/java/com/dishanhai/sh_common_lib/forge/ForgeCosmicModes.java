// Author: dishanhai
package com.dishanhai.sh_common_lib.forge;

import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanCombat;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanMining;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes.Mode;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes.BossPolicy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;

import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

public final class ForgeCosmicModes {
    private ForgeCosmicModes() {}

    public static int flags(ItemStack stack) {
        var tag = stack.getTag();
        return tag == null ? 0 : CosmicArtisanModes.readFlags(tag.getInt(CosmicArtisanModes.TAG),
                tag.getBoolean(CosmicArtisanModes.BEACON_PROJECTION_TAG));
    }

    static BossPolicy bossPolicy(ItemStack stack) {
        var data = stack.getTag();
        return data == null ? BossPolicy.NEUTRAL : BossPolicy.fromId(data.getInt(CosmicArtisanModes.BOSS_POLICY_TAG));
    }

    static void toggle(ServerPlayer player, int bit) {
        if (bit == BossPolicy.FRIENDLY.request() || bit == BossPolicy.HOSTILE.request()) {
            var tool = player.getMainHandItem();
            if (!player.isSpectator() && CosmicArtisanModes.isActive(tool)) toggleBoss(player, tool, BossPolicy.fromId(-bit));
            return;
        }
        var mode = Mode.fromBit(bit);
        var stack = player.getMainHandItem();
        if (mode == null || mode == Mode.AUTO_CLICK || player.isSpectator() || !CosmicArtisanModes.isActive(stack)) return;
        if ((mode == Mode.AE_LINK || mode == Mode.AE_STORAGE_PRIORITY) && !ForgeCosmicStorage.available()) return;
        CosmicArtisanModes.writeFlags(stack.getOrCreateTag(), mode.toggle(flags(stack)));
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
    }

    private static void toggleBoss(ServerPlayer player, ItemStack stack, BossPolicy policy) {
        if (!Mode.HOSTILE_FRIENDLY.enabled(flags(stack))) return;
        var updated = bossPolicy(stack) == policy ? BossPolicy.NEUTRAL : policy;
        stack.getOrCreateTag().putInt(CosmicArtisanModes.BOSS_POLICY_TAG, updated.id());
        player.inventoryMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
    }

    /** The game mode passes a copy of the harvest tool for loot and experience calculations. */
    public static ItemStack applyMiningModes(ItemStack tool) {
        if (!CosmicArtisanModes.isActive(tool)) return tool;
        int flags = flags(tool);
        if (Mode.AE_STORAGE_PRIORITY.enabled(flags) && ForgeCosmicStorage.available()) CosmicArtisanMining.markHarvest(tool);
        if (!Mode.SILK_TOUCH.enabled(flags) && !Mode.FORTUNE.enabled(flags)) return tool;
        var enchantments = EnchantmentHelper.getEnchantments(tool);
        if (Mode.SILK_TOUCH.enabled(flags)) enchantments.merge(Enchantments.SILK_TOUCH, 1, Math::max);
        if (Mode.FORTUNE.enabled(flags)) enchantments.merge(Enchantments.BLOCK_FORTUNE, CosmicArtisanModes.FORTUNE_LEVEL, Math::max);
        EnchantmentHelper.setEnchantments(enchantments, tool);
        return tool;
    }

    static ItemStack capture(LivingEntity target) {
        if (target instanceof Player) return ItemStack.EMPTY;
        var type = target.getType();
        var egg = SpawnEggItem.byId(type);
        if (egg == null) return ItemStack.EMPTY;
        var source = target.saveWithoutId(new CompoundTag());
        var data = new net.minecraft.nbt.CompoundTag();
        for (String key : java.util.List.of("CustomName", "custom_name", "CustomNameVisible", "custom_name_visible",
                "Variant", "variant", "Color", "color", "Sheared", "sheared", "Age", "age", "Sitting", "sitting")) {
            var value = source.get(key);
            if (value != null) data.put(key, value.copy());
        }
        var result = egg.getDefaultInstance();
        data.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
        result.getOrCreateTag().put("EntityTag", data);
        return result;
    }

    static boolean forceKill(Player attacker, Entity target) {
        if (!(attacker instanceof ServerPlayer player) || player.isSpectator()
                || !(target instanceof LivingEntity living) || !living.isAlive() || living.isSpectator()
                || !living.isAttackable()) return false;
        if (living instanceof Player other && (!player.canHarmPlayer(other)
                || CosmicArtisanCombat.protects(other, ForgeCosmicModes::flags))) return false;

        var tool = player.getMainHandItem();
        if (!CosmicArtisanModes.isActive(tool) || !Mode.FORCE_KILL.enabled(flags(tool))) return false;

        living.setLastHurtByPlayer(player);
        living.setHealth(0.0F);
        living.die(player.damageSources().playerAttack(player));
        return true;
    }
}
