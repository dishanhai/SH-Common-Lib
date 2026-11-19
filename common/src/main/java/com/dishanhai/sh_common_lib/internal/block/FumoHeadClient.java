// Author: dishanhai
package com.dishanhai.sh_common_lib.internal.block;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/** Receives server animation timing and adds audible head sources to Fumo's spatial mix. */
public final class FumoHeadClient {
    private static final Map<Integer, FumoHeadJump.State> JUMPS = new HashMap<>();
    private static ClientLevel level;

    private FumoHeadClient() {}

    public static void receive(FumoHeadJump.State state) {
        updateLevel();
        if (level == null) return;
        if (state.startTick() < 0) JUMPS.remove(state.entityId());
        else JUMPS.put(state.entityId(), state);
    }

    static void tick() {
        updateLevel();
        if (level == null || Minecraft.getInstance().isPaused()) return;
        var iterator = JUMPS.values().iterator();
        while (iterator.hasNext()) {
            var state = iterator.next();
            if (!(level.getEntity(state.entityId()) instanceof LivingEntity wearer) || !wearer.isAlive()) {
                iterator.remove();
            } else if (state.bgm()) {
                var fumo = FumoHeadJump.wornFumo(wearer);
                if (fumo != null) FumoMusic.playNearby(wearer, fumo);
            }
        }
    }

    static FumoHeadItem playingItem(LivingEntity wearer) {
        var state = JUMPS.get(wearer.getId());
        return state != null && state.bgm() && wearer.isAlive()
                && wearer.level() == level && Minecraft.getInstance().level == level
                ? FumoHeadJump.wornFumo(wearer) : null;
    }

    public static void applyVanillaPose(PoseStack pose, LivingEntity wearer, float partialTick) {
        if (wearer.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof FumoHeadItem) {
            applyPose(pose, wearer, partialTick);
        }
    }

    static void applyPose(PoseStack pose, LivingEntity wearer, float partialTick) {
        if (level == null || wearer.level() != level || !wearer.isAlive()) return;
        var state = JUMPS.get(wearer.getId());
        if (state != null) {
            float jump = FumoMotion.squash(Math.max(0, level.getGameTime() - state.startTick()), partialTick);
            FumoMotion.applyHead(pose, jump);
        }
    }

    private static void updateLevel() {
        ClientLevel current = Minecraft.getInstance().level;
        if (level != current) {
            JUMPS.clear();
            level = current;
        }
    }
}
