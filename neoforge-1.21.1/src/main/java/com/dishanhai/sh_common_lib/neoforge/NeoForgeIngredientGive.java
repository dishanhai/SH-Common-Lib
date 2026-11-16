// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge;

import com.dishanhai.sh_common_lib.api.client.ulk.ClientIngredientGive;
import com.dishanhai.sh_common_lib.api.client.ulk.ClientIngredientGive.Mode;
import com.dishanhai.sh_common_lib.compat.jei.JeiGiveCommands;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

/** Loader material conversion; the ULK request and JEI permission path remain shared. */
final class NeoForgeIngredientGive implements ClientIngredientGive.Backend {
    static void install() { ClientIngredientGive.install(new NeoForgeIngredientGive()); }
    public boolean enabled() { return JeiGiveCommands.enabled(); }
    public boolean infinityCellsAvailable() { return ModList.get().isLoaded("ae2"); }

    public boolean give(Object ingredient, Mode mode) {
        if (mode == Mode.INFINITY_CELL) {
            if (!infinityCellsAvailable()) return failure("give_no_cell");
            var target = InfinityCell.Target.fromIngredient(ingredient);
            if (target == null) return failure("give_unsupported");
            if (!JeiGiveCommands.give(InfinityCell.create(target), 1)) return failure("give_unavailable");
            JeiGiveCommands.feedback("cell_requested", false, target.name());
            return true;
        }
        ItemStack item;
        boolean fluid = ingredient instanceof FluidStack;
        if (ingredient instanceof ItemStack stack && !stack.isEmpty()) {
            item = stack.copy();
        } else if (ingredient instanceof FluidStack stack && !stack.isEmpty()) {
            var bucketFluid = stack.copy();
            bucketFluid.setAmount(FluidType.BUCKET_VOLUME);
            item = bucketFluid.getFluidType().getBucket(bucketFluid);
            if (item.isEmpty()) return failure("give_no_bucket");
        } else return failure("give_unsupported");
        int amount = mode.amount(fluid, item.getMaxStackSize());
        if (!JeiGiveCommands.give(item, amount)) return failure("give_unavailable");
        JeiGiveCommands.feedback("give_requested", false, amount, item.getHoverName());
        return true;
    }

    private static boolean failure(String key) {
        JeiGiveCommands.feedback(key, true);
        return true;
    }
}
