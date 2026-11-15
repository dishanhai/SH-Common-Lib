// Author: dishanhai
package com.dishanhai.sh_common_lib.forge;

import appeng.api.stacks.AEKey;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import me.ramidzkh.mekae2.ae2.MekanismKeyType;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

final class AppliedMekanisticsCells {
    private AppliedMekanisticsCells() {}

    static InfinityCell.Target fromIngredient(Object ingredient) {
        if (!(ingredient instanceof ChemicalStack<?> stack) || stack.isEmpty()) return null;
        var unit = stack.copy();
        unit.setAmount(1);
        var key = MekanismKey.of(unit);
        return key == null ? null : new InfinityCell.Target(InfinityCell.Kind.CHEMICAL, key.getId(), key.toTag());
    }

    static AEKey key(InfinityCell.Target target) {
        var tag = target.tag();
        if (tag == null || !tag.contains("t", Tag.TAG_BYTE)) return null;
        var key = MekanismKeyType.TYPE.loadKeyFromTag(tag);
        return key != null && target.id().equals(key.getId()) ? key : null;
    }

    static boolean hasChemical(InfinityCell.Target target) {
        return key(target) != null;
    }

    static Component name(InfinityCell.Target target) {
        var key = key(target);
        return key == null ? Component.literal(target.id().toString()) : key.getDisplayName();
    }
}
