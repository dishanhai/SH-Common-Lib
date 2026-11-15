package com.dishanhai.sh_common_lib.forge;

import com.dishanhai.sh_common_lib.api.ShCommonLib;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

final class ForgeCosmicHorizon extends MobEffect {
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, ShCommonLib.MOD_ID);
    private static final RegistryObject<MobEffect> HORIZON = EFFECTS.register("cosmic_horizon", ForgeCosmicHorizon::new);

    private ForgeCosmicHorizon() { super(MobEffectCategory.BENEFICIAL, 0xA77DFF); }
    static void register(IEventBus bus) {
        EFFECTS.register(bus);
        MinecraftForge.EVENT_BUS.addListener(ForgeCosmicHorizon::tick);
    }
    static void grant(LivingEntity player) {
        if (player.tickCount % 20 == 0) player.addEffect(new MobEffectInstance(HORIZON.get(), 60, 0, false, false, true));
    }
    private static void tick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || entity.tickCount % 20 != 0) return;
        var horizon = entity.getEffect(HORIZON.get());
        if (horizon == null) return;
        int amplifier = horizon.getAmplifier();
        for (MobEffect effect : new MobEffect[]{MobEffects.NIGHT_VISION, MobEffects.REGENERATION, MobEffects.ABSORPTION,
                MobEffects.DAMAGE_RESISTANCE, MobEffects.SATURATION, MobEffects.FIRE_RESISTANCE,
                MobEffects.DAMAGE_BOOST, MobEffects.LUCK}) {
            entity.addEffect(new MobEffectInstance(effect, effect == MobEffects.SATURATION ? 1 : 240, amplifier, false, false, false));
        }
    }
}
