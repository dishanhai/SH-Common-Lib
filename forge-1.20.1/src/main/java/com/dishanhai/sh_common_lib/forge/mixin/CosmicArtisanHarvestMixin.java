// Author: dishanhai
package com.dishanhai.sh_common_lib.forge.mixin;

import com.dishanhai.sh_common_lib.forge.ForgeCosmicModes;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = ServerPlayerGameMode.class, remap = false)
abstract class CosmicArtisanHarvestMixin {
    // The second ItemStack local is the copy reserved for harvesting, after the live tool.
    @ModifyVariable(method = {"destroyBlock", "m_9280_"}, at = @At(value = "STORE", ordinal = 0), ordinal = 1)
    private ItemStack shclib$applyMiningModes(ItemStack harvestTool) {
        return ForgeCosmicModes.applyMiningModes(harvestTool);
    }
}
