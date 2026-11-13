// Author: dishanhai
package com.dishanhai.sh_common_lib.api.client.ulk;

import com.dishanhai.sh_better_ui.kernel.api.ServiceKey;
import com.dishanhai.sh_better_ui.kernel.api.UIService;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_common_lib.api.ulk.InventoryProvider;
import com.dishanhai.sh_common_lib.api.ulk.InventorySnapshot;
import net.minecraft.server.level.ServerPlayer;

/** UUK discovery bridge. The supplied player must be inspected on its server thread. */
@FunctionalInterface
public interface UnifiedLibUiService extends UIService {
    ServiceKey<UnifiedLibUiService> KEY = new ServiceKey<>(UiId.parse("sh_common_lib:unified_lib"), UnifiedLibUiService.class, 1);
    InventorySnapshot inspect(ServerPlayer player, InventoryProvider... providers);
}
