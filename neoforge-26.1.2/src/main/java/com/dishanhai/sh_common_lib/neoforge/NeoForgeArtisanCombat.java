package com.dishanhai.sh_common_lib.neoforge;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

final class NeoForgeArtisanCombat {
    private static final Identifier DAMAGE = Identifier.fromNamespaceAndPath(ShCommonLib.MOD_ID, "artisan_attack_damage");
    private static final Identifier SPEED = Identifier.fromNamespaceAndPath(ShCommonLib.MOD_ID, "artisan_attack_speed");

    private NeoForgeArtisanCombat() {}

    static void refresh(ServerPlayer player) {
        var item = player.getMainHandItem().getItem();
        if (item instanceof NeoForgeArtisan artisan && item != NeoForgeArtisan.cosmicItem()
                && artisan.settings().enabled()) {
            var settings = artisan.settings();
            update(player, Attributes.ATTACK_DAMAGE, DAMAGE, settings.attackDamage() - 9);
            update(player, Attributes.ATTACK_SPEED, SPEED, settings.attackSpeed() - 0.9);
        } else {
            update(player, Attributes.ATTACK_DAMAGE, DAMAGE, 0);
            update(player, Attributes.ATTACK_SPEED, SPEED, 0);
        }
    }

    private static void update(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount) {
        var instance = player.getAttribute(attribute);
        if (instance == null) return;
        var existing = instance.getModifier(id);
        if (existing != null && existing.amount() == amount) return;
        if (existing != null) instance.removeModifier(id);
        if (amount != 0) instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
    }
}
