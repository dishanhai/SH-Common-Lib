// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A remote client's potion registry cannot know the server's reloaded brewing ingredients. */
@Mixin(targets = "net.minecraft.world.inventory.BrewingStandMenu$IngredientsSlot", remap = false)
abstract class BrewingIngredientSlotMixin {
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true, remap = false)
    private void shclib$allowServerValidation(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        Minecraft client = Minecraft.getInstance();
        if (!stack.isEmpty() && client.isSameThread() && client.screen instanceof BrewingStandScreen) {
            callback.setReturnValue(true);
        }
    }
}
