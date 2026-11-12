// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class NeoForgeKeyMappings {
    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ShCommonLib.MOD_ID, "controls"));

    private NeoForgeKeyMappings() {}
}
