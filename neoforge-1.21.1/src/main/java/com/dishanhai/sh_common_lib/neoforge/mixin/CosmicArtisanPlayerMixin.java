// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge.mixin;

import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
abstract class CosmicArtisanPlayerMixin {
    @Inject(method = "getAttackStrengthScale", at = @At("HEAD"), cancellable = true)
    private void shclib$fullAttackStrength(float partialTick, CallbackInfoReturnable<Float> result) {
        if (CosmicArtisanModes.isActive(((Player) (Object) this).getMainHandItem())) {
            result.setReturnValue(1.0F);
        }
    }

    // Replace the base value before vanilla applies enchantments and critical hits.
    @ModifyVariable(method = "attack", at = @At(value = "STORE", ordinal = 0), ordinal = 0)
    private float shclib$baseAttackDamage(float damage) {
        return CosmicArtisanModes.isActive(((Player) (Object) this).getMainHandItem())
                ? CosmicArtisanModes.ATTACK_DAMAGE : damage;
    }
}
