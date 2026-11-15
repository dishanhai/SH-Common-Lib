// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge.mixin;

import com.dishanhai.sh_common_lib.neoforge.NeoForgeCosmicModes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerPlayerGameMode.class)
abstract class CosmicArtisanHarvestMixin {
    @Shadow protected ServerLevel level;

    // The second ItemStack local is the copy reserved for harvesting, after the live tool.
    @ModifyVariable(method = "destroyBlock", at = @At(value = "STORE", ordinal = 0), ordinal = 1)
    private ItemStack shclib$applyMiningModes(ItemStack harvestTool) {
        return NeoForgeCosmicModes.applyMiningModes(harvestTool, level);
    }
}
