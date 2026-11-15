// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge.mixin;

import com.dishanhai.sh_common_lib.neoforge.NeoForgeCosmicModes;
import com.dishanhai.sh_common_lib.neoforge.NeoForgeCosmicStorage;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanMining;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes.Mode;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class CosmicArtisanLootMixin {
    @Inject(method = "getDrops", at = @At("RETURN"), cancellable = true)
    private void shclib$smeltDrops(LootParams.Builder parameters, CallbackInfoReturnable<List<ItemStack>> result) {
        var tool = parameters.getOptionalParameter(LootContextParams.TOOL);
        if (tool == null || !CosmicArtisanModes.isActive(tool)) return;
        int flags = NeoForgeCosmicModes.flags(tool);
        var drops = result.getReturnValue();
        if (Mode.AUTO_SMELT.enabled(flags)) drops = shclib$smelt(parameters, drops);
        if (Mode.AE_STORAGE_PRIORITY.enabled(flags) && CosmicArtisanMining.takeHarvestMark(tool)
                && parameters.getOptionalParameter(LootContextParams.THIS_ENTITY) instanceof ServerPlayer player) {
            var remaining = new ArrayList<ItemStack>();
            for (var drop : drops) {
                var item = drop.copy();
                NeoForgeCosmicStorage.insert(player, tool, item);
                if (!item.isEmpty()) remaining.add(item);
            }
            drops = remaining;
        }
        if (drops != result.getReturnValue()) result.setReturnValue(drops);
    }

    @org.spongepowered.asm.mixin.Unique
    private static List<ItemStack> shclib$smelt(LootParams.Builder parameters, List<ItemStack> drops) {
        var level = parameters.getLevel();
        var recipes = level.getRecipeManager();
        return CosmicArtisanMining.smelt(drops, drop -> {
            var ingredient = new SingleRecipeInput(drop);
            for (var type : List.of(RecipeType.SMELTING, RecipeType.BLASTING, RecipeType.SMOKING)) {
                var recipe = recipes.getRecipeFor(type, ingredient, level);
                if (recipe.isPresent()) return recipe.get().value().assemble(ingredient, level.registryAccess());
            }
            return ItemStack.EMPTY;
        });
    }
}
