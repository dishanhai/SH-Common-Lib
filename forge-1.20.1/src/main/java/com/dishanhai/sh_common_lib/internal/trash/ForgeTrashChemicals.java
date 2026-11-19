// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.trash;

import mekanism.api.Action;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import mekanism.api.chemical.infuse.IInfusionHandler;
import mekanism.api.chemical.infuse.InfuseType;
import mekanism.api.chemical.infuse.InfusionStack;
import mekanism.api.chemical.pigment.IPigmentHandler;
import mekanism.api.chemical.pigment.Pigment;
import mekanism.api.chemical.pigment.PigmentStack;
import mekanism.api.chemical.slurry.ISlurryHandler;
import mekanism.api.chemical.slurry.Slurry;
import mekanism.api.chemical.slurry.SlurryStack;
import mekanism.common.capabilities.Capabilities;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

final class ForgeTrashChemicals {
    static <T> LazyOptional<T> get(PlatformTrashCanEntity entity, Capability<T> capability) {
        if (capability == Capabilities.GAS_HANDLER) return entity.capability(capability, GasSink::new);
        if (capability == Capabilities.INFUSION_HANDLER) return entity.capability(capability, InfusionSink::new);
        if (capability == Capabilities.PIGMENT_HANDLER) return entity.capability(capability, PigmentSink::new);
        if (capability == Capabilities.SLURRY_HANDLER) return entity.capability(capability, SlurrySink::new);
        return LazyOptional.empty();
    }

    private abstract static class Sink<C extends Chemical<C>, S extends ChemicalStack<C>> implements IChemicalHandler<C, S> {
        public int getTanks() { return 1; }
        public S getChemicalInTank(int tank) { return getEmptyStack(); }
        public void setChemicalInTank(int tank, S stack) {}
        public long getTankCapacity(int tank) { return tank == 0 ? Long.MAX_VALUE : 0; }
        public boolean isValid(int tank, S stack) { return tank == 0 && !stack.isEmpty(); }
        public S insertChemical(int tank, S stack, Action action) { return tank == 0 ? getEmptyStack() : stack; }
        public S extractChemical(int tank, long amount, Action action) { return getEmptyStack(); }
    }

    private static final class GasSink extends Sink<Gas, GasStack> implements IGasHandler {}
    private static final class InfusionSink extends Sink<InfuseType, InfusionStack> implements IInfusionHandler {}
    private static final class PigmentSink extends Sink<Pigment, PigmentStack> implements IPigmentHandler {}
    private static final class SlurrySink extends Sink<Slurry, SlurryStack> implements ISlurryHandler {}
}
