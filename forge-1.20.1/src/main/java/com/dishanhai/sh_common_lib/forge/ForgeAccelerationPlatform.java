// Author: dishanhai
package com.dishanhai.sh_common_lib.forge;

import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanAcceleration;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes;
import com.dishanhai.sh_common_lib.internal.item.CosmicArtisanModes.Mode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;

final class ForgeAccelerationPlatform implements CosmicArtisanAcceleration.Platform {
    @Override public boolean masterEnabled(ServerPlayer player) {
        var stack = player.getMainHandItem();
        return !player.isSpectator() && CosmicArtisanModes.isActive(stack) && Mode.TIME_ACCELERATION.enabled(ForgeCosmicModes.flags(stack));
    }

    @Override public void setMaster(ServerPlayer player, boolean enabled) {
        var stack = player.getMainHandItem();
        if (!player.isSpectator() && CosmicArtisanModes.isActive(stack)
                && Mode.TIME_ACCELERATION.enabled(ForgeCosmicModes.flags(stack)) != enabled)
            ForgeCosmicModes.toggle(player, Mode.TIME_ACCELERATION.bit());
    }

    @Override public boolean tickAe(ServerLevel level, BlockPos pos, BlockEntity entity) {
        return !entity.isRemoved() && ModList.get().isLoaded("ae2") && ForgeAccelerationAe.tick(entity);
    }

    @Override public void tickEntity(ServerLevel level, Entity entity) {
        if (entity instanceof Player || entity.isRemoved()) return;
        if (entity.isPassenger() || entity.getSelfAndPassengers().anyMatch(e -> e instanceof Player)) {
            entity.setOldPosAndRot();
            entity.tickCount++;
            if (entity.isPassenger()) entity.rideTick(); else entity.tick();
        } else level.tickNonPassenger(entity);
    }

    @Override public void tickWorld(ServerLevel level) {
        if (level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) level.setDayTime(level.getDayTime() + 1);
        ForgeWeatherAccess.advance(level);
    }

    @Override public void strikeLightning(ServerLevel level, BlockPos pos) {
        var lightning = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
        if (lightning != null) {
            lightning.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(pos.above()));
            level.addFreshEntity(lightning);
        }
    }

    @Override public void syncWorld(ServerLevel level, long worldExtraTicks) {
        String dimension = level.dimension().location().toString();
        for (var player : level.players()) ForgeAccelerationNetwork.syncClock(player, dimension, worldExtraTicks);
    }

    @Override public void showHint(ServerPlayer player) {
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(
                "screen.sh_common_lib.acceleration.hint", Component.keybind("key.sh_common_lib.acceleration"))));
    }
}
