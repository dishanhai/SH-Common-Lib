// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge.mixin;

import com.dishanhai.sh_common_lib.neoforge.NeoForgeCosmicModes;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes.Mode;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
abstract class CosmicArtisanWrenchMixin {
    @Inject(method = "is(Lnet/minecraft/tags/TagKey;)Z", at = @At("HEAD"), cancellable = true)
    private void shclib$wrenchTag(TagKey<Item> tag, CallbackInfoReturnable<Boolean> result) {
        ItemStack stack = (ItemStack) (Object) this;
        String id = tag.location().toString();
        if ((id.equals("c:tools/wrench") || id.equals("forge:tools/wrenches")) && CosmicArtisanModes.isCosmic(stack)) {
            result.setReturnValue(CosmicArtisanModes.isActive(stack) && Mode.WRENCH_TAG.enabled(NeoForgeCosmicModes.flags(stack)));
        }
    }
}
