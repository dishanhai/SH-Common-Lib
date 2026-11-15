package com.dishanhai.sh_common_lib.neoforge;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

final class NeoForgeCosmicHorizon extends MobEffect {
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, ShCommonLib.MOD_ID);
    private static final net.minecraft.core.Holder<MobEffect> HORIZON = EFFECTS.register("cosmic_horizon", NeoForgeCosmicHorizon::new);

    private NeoForgeCosmicHorizon() { super(MobEffectCategory.BENEFICIAL, 0xA77DFF); }
    static void register(IEventBus bus) {
        EFFECTS.register(bus);
        NeoForge.EVENT_BUS.addListener(NeoForgeCosmicHorizon::tick);
    }
    static void grant(LivingEntity player) {
        if (player.tickCount % 20 == 0) player.addEffect(new MobEffectInstance(HORIZON, 60, 0, false, false, true));
    }
    private static void tick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity.level().isClientSide() || entity.tickCount % 20 != 0) return;
        var horizon = entity.getEffect(HORIZON);
        if (horizon == null) return;
        int amplifier = horizon.getAmplifier();
        for (var effect : java.util.List.of(MobEffects.NIGHT_VISION, MobEffects.REGENERATION, MobEffects.ABSORPTION,
                MobEffects.RESISTANCE, MobEffects.SATURATION, MobEffects.FIRE_RESISTANCE,
                MobEffects.STRENGTH, MobEffects.LUCK)) {
            entity.addEffect(new MobEffectInstance(effect, effect == MobEffects.SATURATION ? 1 : 240, amplifier, false, false, false));
        }
    }
}
