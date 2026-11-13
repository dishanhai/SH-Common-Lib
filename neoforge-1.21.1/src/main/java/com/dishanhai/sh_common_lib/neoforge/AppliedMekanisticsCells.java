package com.dishanhai.sh_common_lib.neoforge;

import appeng.api.stacks.AEKey;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

final class AppliedMekanisticsCells {
    private AppliedMekanisticsCells() {}

    static InfinityCell.Target fromIngredient(Object ingredient) {
        if (ingredient instanceof ChemicalStack stack && !stack.isEmpty()) {
            return new InfinityCell.Target(InfinityCell.Kind.CHEMICAL,
                    MekanismAPI.CHEMICAL_REGISTRY.getKey(stack.getChemical()),
                    DataComponentPatch.EMPTY);
        }
        return null;
    }

    static AEKey key(ResourceLocation id) {
        return MekanismAPI.CHEMICAL_REGISTRY.getHolder(id)
                .map(holder -> MekanismKey.of(new ChemicalStack(holder, 1))).orElse(null);
    }

    static boolean hasChemical(ResourceLocation id) {
        return key(id) != null;
    }

    static Component name(ResourceLocation id) {
        var key = key(id);
        return key == null ? Component.literal(id.toString()) : key.getDisplayName();
    }
}
