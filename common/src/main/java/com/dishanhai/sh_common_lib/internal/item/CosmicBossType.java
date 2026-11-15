// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

final class CosmicBossType {
    private static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("sh_common_lib", "cosmic_bosses"));

    private CosmicBossType() {}

    static boolean matches(EntityType<?> type) {
        return type == EntityType.WITHER || type == EntityType.ENDER_DRAGON || type.is(BOSSES);
    }
}
