package com.dishanhai.sh_common_lib.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

final class ForgeArtisanCombat {
    private static final UUID DAMAGE = UUID.fromString("a932396c-015f-4b22-860c-6d226f43b84a");
    private static final UUID SPEED = UUID.fromString("b601deac-08e6-4a84-a735-c845adff11b3");

    private ForgeArtisanCombat() {}

    static void refresh(ServerPlayer player) {
        var item = player.getMainHandItem().getItem();
        if (item instanceof ForgeArtisan artisan && item != ForgeArtisan.cosmicItem()
                && artisan.settings().enabled()) {
            var settings = artisan.settings();
            update(player, Attributes.ATTACK_DAMAGE, DAMAGE, settings.attackDamage() - 9);
            update(player, Attributes.ATTACK_SPEED, SPEED, settings.attackSpeed() - 0.9);
        } else {
            update(player, Attributes.ATTACK_DAMAGE, DAMAGE, 0);
            update(player, Attributes.ATTACK_SPEED, SPEED, 0);
        }
    }

    private static void update(ServerPlayer player, Attribute attribute, UUID id, double amount) {
        var instance = player.getAttribute(attribute);
        if (instance == null) return;
        var existing = instance.getModifier(id);
        if (existing != null && existing.getAmount() == amount) return;
        if (existing != null) instance.removeModifier(id);
        if (amount != 0) instance.addTransientModifier(new AttributeModifier(id, "SHCLIB Artisan", amount, AttributeModifier.Operation.ADDITION));
    }
}
