// Author: dishanhai
package com.dishanhai.sh_common_lib.neoforge.mixin;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerLevel.class)
public interface ServerLevelWeatherInvoker {
    @Invoker("advanceWeatherCycle")
    void shCommonLib$advanceWeatherCycle();
}
