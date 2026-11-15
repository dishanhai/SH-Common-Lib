// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge;

import appeng.api.stacks.AEKey;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

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

    static AEKey key(Identifier id) {
        return MekanismAPI.CHEMICAL_REGISTRY.get(id)
                .map(holder -> MekanismKey.of(new ChemicalStack(holder, 1))).orElse(null);
    }

    static boolean hasChemical(Identifier id) {
        return key(id) != null;
    }

    static Component name(Identifier id) {
        var key = key(id);
        return key == null ? Component.literal(id.toString()) : key.getDisplayName();
    }
}
