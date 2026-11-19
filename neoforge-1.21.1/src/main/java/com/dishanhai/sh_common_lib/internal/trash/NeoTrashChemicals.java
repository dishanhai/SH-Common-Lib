// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.trash;

import com.dishanhai.sh_common_lib.api.ulk.trash.TrashCan.Extension;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.common.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

final class NeoTrashChemicals {
    private static final IChemicalHandler SINK = new IChemicalHandler() {
        public int getChemicalTanks() { return 1; }
        public ChemicalStack getChemicalInTank(int tank) { return ChemicalStack.EMPTY; }
        public void setChemicalInTank(int tank, ChemicalStack stack) {}
        public long getChemicalTankCapacity(int tank) { return tank == 0 ? Long.MAX_VALUE : 0; }
        public boolean isValid(int tank, ChemicalStack stack) { return tank == 0 && !stack.isEmpty(); }
        public ChemicalStack insertChemical(int tank, ChemicalStack stack, Action action) {
            return tank == 0 ? ChemicalStack.EMPTY : stack;
        }
        public ChemicalStack extractChemical(int tank, long amount, Action action) { return ChemicalStack.EMPTY; }
    };

    static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.CHEMICAL.block(), TrashCanPlatform.ENTITY.get(),
                (entity, side) -> entity.hasModule(Extension.CHEMICAL) ? SINK : null);
    }
}
