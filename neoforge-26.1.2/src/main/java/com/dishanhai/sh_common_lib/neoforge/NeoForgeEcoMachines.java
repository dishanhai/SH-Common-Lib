// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge;

import com.dishanhai.sh_common_lib.internal.performance.EcoMachineSnapshot;
import com.dishanhai.sh_common_lib.internal.performance.EcoNetworkDiagnostics;
import net.minecraft.server.level.ServerPlayer;

final class NeoForgeEcoMachines {
    private NeoForgeEcoMachines() {}

    static EcoMachineSnapshot sample(ServerPlayer player, String dimension, int mask) {
        return EcoNetworkDiagnostics.machines(NeoForgeEcoHud.connectedGrid(player), dimension, mask,
                block -> block.getLevel().dimension().identifier().toString());
    }
}
