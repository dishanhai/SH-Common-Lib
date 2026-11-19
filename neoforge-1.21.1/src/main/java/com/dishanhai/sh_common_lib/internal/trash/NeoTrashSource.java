// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.trash;

import com.dishanhai.sh_common_lib.api.ulk.trash.TrashCan.Extension;
import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

final class NeoTrashSource {
    private static final ISourceCap SINK = new ISourceCap() {
        public boolean canAcceptSource(int amount) { return amount >= 0; }
        public boolean canProvideSource(int amount) { return false; }
        public int getMaxExtract() { return 0; }
        public int getMaxReceive() { return Integer.MAX_VALUE; }
        public int getSource() { return 0; }
        public int getSourceCapacity() { return Integer.MAX_VALUE; }
        public void setSource(int amount) {}
        public void setMaxSource(int amount) {}
        public int receiveSource(int amount, boolean simulate) { return Math.max(0, amount); }
        public int extractSource(int amount, boolean simulate) { return 0; }
    };

    static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(CapabilityRegistry.SOURCE_CAPABILITY, TrashCanPlatform.ENTITY.get(),
                (entity, side) -> entity.hasModule(Extension.SOURCE) ? SINK : null);
    }
}
