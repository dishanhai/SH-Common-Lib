// Author: dishanhai
package com.dishanhai.sh_common_lib.mixin.trash;

import com.dishanhai.sh_common_lib.internal.trash.TrashStressNetwork;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.simibubi.create.content.kinetics.KineticNetwork", remap = false)
abstract class TrashStressNetworkMixin implements TrashStressNetwork {
    @Unique
    private Map<BlockPos, Integer> shclib$limits;

    @Override
    public void shclib$setLimit(BlockPos position, Integer limit) {
        if (limit != null) {
            if (shclib$limits == null) shclib$limits = new HashMap<>();
            shclib$limits.put(position, limit);
        } else if (shclib$limits != null) {
            shclib$limits.remove(position);
        }
    }

    @Inject(method = "calculateCapacity", at = @At("RETURN"), cancellable = true)
    private void shclib$limitCapacity(CallbackInfoReturnable<Float> result) {
        if (shclib$limits == null || shclib$limits.isEmpty()) return;
        float capacity = result.getReturnValueF();
        for (int limit : shclib$limits.values()) {
            float exactOrLower = limit;
            if ((double) exactOrLower > limit) exactOrLower = Math.nextDown(exactOrLower);
            capacity = Math.min(capacity, exactOrLower);
        }
        result.setReturnValue(capacity);
    }
}
