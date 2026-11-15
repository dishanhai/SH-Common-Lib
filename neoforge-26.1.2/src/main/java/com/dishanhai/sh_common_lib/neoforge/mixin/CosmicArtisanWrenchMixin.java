// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge.mixin;

import com.dishanhai.sh_common_lib.neoforge.NeoForgeCosmicModes;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes.Mode;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemStack.class)
abstract class CosmicArtisanWrenchMixin {
    @Unique
    public boolean is(TagKey<Item> tag) {
        ItemStack stack = (ItemStack) (Object) this;
        return (tag.location().toString().equals("c:tools/wrench") && CosmicArtisanModes.isCosmic(stack))
                ? CosmicArtisanModes.isActive(stack) && Mode.WRENCH_TAG.enabled(NeoForgeCosmicModes.flags(stack))
                : stack.typeHolder().is(tag);
    }
}
